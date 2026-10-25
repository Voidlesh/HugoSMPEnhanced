package hse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Turns the shared source tree into the sources of one Minecraft line.
 *
 * <p>Lines the two lines differ in are written once per line between directives (each on its own line):
 * <pre>
 * //? if modern      26.x: unobfuscated, Java 25
 * ...
 * //? else           1.21.11
 * ...
 * //? end
 * </pre>
 * {@code //? if legacy} works the same with the lines swapped. A file whose first line is {@code //? only modern}
 * (or {@code legacy}) exists in that line only. The shared lines are written for 26.x; for 1.21.11 the swaps of
 * {@code versions/swaps.txt} (plain renames such as GuiGraphicsExtractor -> GuiGraphics) are applied to them, in
 * {@code .java} files only.
 */
public final class Preprocessor {
	private static final String MARK = "//?";
	private static final List<String> TEXT = List.of(".java", ".json");

	public record Swap(Pattern pattern, String replacement) {
	}

	private final boolean modern;
	private final List<Swap> swaps;

	public Preprocessor(boolean modern, List<Swap> swaps) {
		this.modern = modern;
		this.swaps = swaps;
	}

	public static List<Swap> readSwaps(Path file) throws IOException {
		List<Swap> swaps = new ArrayList<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			int tab = line.indexOf('\t');
			if (tab < 0) {
				throw new IllegalStateException("swaps.txt line without a tab: " + line);
			}
			swaps.add(new Swap(Pattern.compile(line.substring(0, tab)), line.substring(tab + 1)));
		}
		return swaps;
	}

	public void run(Path source, Path target) throws IOException {
		deleteTree(target);
		if (!Files.isDirectory(source)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(source)) {
			for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				Path out = target.resolve(source.relativize(file).toString());
				String name = file.getFileName().toString();
				if (TEXT.stream().noneMatch(name::endsWith)) {
					Files.createDirectories(out.getParent());
					Files.copy(file, out);
					continue;
				}
				String text = process(Files.readString(file, StandardCharsets.UTF_8), file.toString(), name.endsWith(".java"));
				if (text != null) {
					Files.createDirectories(out.getParent());
					Files.writeString(out, text, StandardCharsets.UTF_8);
				}
			}
		}
	}

	/** Returns the text for this line, or null when the file does not exist in this line. */
	String process(String text, String where, boolean swap) {
		String[] lines = text.split("\n", -1);
		StringBuilder out = new StringBuilder(text.length());
		boolean first = true;
		int state = 0; // 0 outside, 1 in the first branch, 2 in else
		boolean firstActive = false;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String trimmed = line.strip();
			if (trimmed.startsWith(MARK)) {
				String[] words = trimmed.substring(MARK.length()).strip().split("\\s+");
				if (words.length < 1 || words[0].isEmpty() || (words[0].equals("if") || words[0].equals("only")) && words.length < 2) {
					throw new IllegalStateException(where + ":" + (i + 1) + ": directive without argument: " + trimmed);
				}
				switch (words[0]) {
					case "only" -> {
						if (i != 0) {
							throw new IllegalStateException(where + ":" + (i + 1) + ": '//? only' must be the first line");
						}
						if (!words[1].equals(modern ? "modern" : "legacy")) {
							return null;
						}
						continue;
					}
					case "if" -> {
						if (state != 0) {
							throw new IllegalStateException(where + ":" + (i + 1) + ": nested '//? if'");
						}
						state = 1;
						firstActive = words[1].equals(modern ? "modern" : "legacy");
						continue;
					}
					case "else" -> {
						if (state != 1) {
							throw new IllegalStateException(where + ":" + (i + 1) + ": '//? else' without 'if'");
						}
						state = 2;
						continue;
					}
					case "end" -> {
						if (state == 0) {
							throw new IllegalStateException(where + ":" + (i + 1) + ": '//? end' without 'if'");
						}
						state = 0;
						continue;
					}
					default -> throw new IllegalStateException(where + ":" + (i + 1) + ": unknown directive " + trimmed);
				}
			}
			if (state == 1 && !firstActive || state == 2 && firstActive) {
				continue;
			}
			if (state == 0 && !modern && swap) {
				for (Swap rename : swaps) {
					line = rename.pattern().matcher(line).replaceAll(rename.replacement());
				}
			}
			if (!first) {
				out.append('\n');
			}
			out.append(line);
			first = false;
		}
		if (state != 0) {
			throw new IllegalStateException(where + ":" + lines.length + ": unterminated '//? if' ('//? end' missing)");
		}
		return out.toString();
	}

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}
}
