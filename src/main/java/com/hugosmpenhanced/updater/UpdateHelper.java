package com.hugosmpenhanced.updater;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Swaps the mod jar after the game has closed. Started by the mod as its own small Java process
 * (from a copy of the jar, so nothing it needs is locked), because a running game keeps its mod
 * jars open and on Windows they can be neither deleted nor overwritten.
 *
 * <p>Plain Java on purpose: no Minecraft or Fabric classes exist in this process.
 *
 * <p>Arguments: game pid, old jar, staged new jar, target jar, result file. The new jar is first
 * copied next to the old one under a name Fabric ignores ({@code .jar.tmp}), then the old jar is
 * moved aside ({@code .jar.old}) and the copy renamed into place. If anything fails, the old jar is
 * put back, so the mods folder always holds exactly one version of the mod. The result file tells
 * the next game start what happened.
 */
public final class UpdateHelper {
	private static final long WAIT_FOR_GAME_MS = TimeUnit.MINUTES.toMillis(10);
	private static final long UNLOCK_TIMEOUT_MS = 30_000;

	private UpdateHelper() {
	}

	public static void main(String[] args) {
		if (args.length != 5) {
			System.err.println("usage: UpdateHelper <pid> <oldJar> <newJar> <targetJar> <resultFile>");
			System.exit(2);
		}
		Path result = Path.of(args[4]);
		try {
			waitForExit(Long.parseLong(args[0]));
			install(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
			writeResult(result, true, "");
		} catch (Throwable t) {
			StringWriter trace = new StringWriter();
			t.printStackTrace(new PrintWriter(trace));
			writeResult(result, false, String.valueOf(t.getMessage()) + "\n" + trace);
			System.exit(1);
		}
	}

	private static void waitForExit(long pid) throws Exception {
		Optional<ProcessHandle> game = ProcessHandle.of(pid);
		if (game.isPresent()) {
			game.get().onExit().get(WAIT_FOR_GAME_MS, TimeUnit.MILLISECONDS);
		}
		// file handles of a just-ended process can take a moment to be released
		Thread.sleep(500);
	}

	/** Replaces {@code oldJar} by {@code newJar} under the name {@code target}. */
	public static void install(Path oldJar, Path newJar, Path target) throws IOException, InterruptedException {
		if (!Files.isRegularFile(newJar)) {
			throw new IOException("Neue Version fehlt: " + newJar);
		}
		// Fabric only loads *.jar, so neither of these names is ever picked up as a second copy of the mod
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		Path backup = oldJar.resolveSibling(oldJar.getFileName() + ".old");
		Files.copy(newJar, temp, StandardCopyOption.REPLACE_EXISTING);
		try {
			moveWithRetry(oldJar, backup);
		} catch (IOException | InterruptedException e) {
			Files.deleteIfExists(temp);
			throw new IOException("Alte Version ist noch gesperrt: " + oldJar, e);
		}
		try {
			if (!target.equals(oldJar)) {
				// an older copy under the new file name would otherwise make Fabric load the mod twice
				deleteWithRetry(target);
			}
			moveWithRetry(temp, target);
		} catch (IOException | InterruptedException e) {
			// put the old version back, a game without the mod is worse than no update
			Files.move(backup, oldJar, StandardCopyOption.REPLACE_EXISTING);
			Files.deleteIfExists(temp);
			throw e;
		}
		Files.deleteIfExists(backup);
		Files.deleteIfExists(newJar);
	}

	private static void deleteWithRetry(Path path) throws IOException, InterruptedException {
		long until = System.currentTimeMillis() + UNLOCK_TIMEOUT_MS;
		while (true) {
			try {
				Files.deleteIfExists(path);
				return;
			} catch (IOException e) {
				if (System.currentTimeMillis() > until) {
					throw e;
				}
				Thread.sleep(250);
			}
		}
	}

	private static void moveWithRetry(Path from, Path to) throws IOException, InterruptedException {
		long until = System.currentTimeMillis() + UNLOCK_TIMEOUT_MS;
		while (true) {
			try {
				Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				if (System.currentTimeMillis() > until) {
					throw e;
				}
				Thread.sleep(250);
			}
		}
	}

	private static void writeResult(Path result, boolean ok, String message) {
		String json = "{\"ok\":" + ok + ",\"time\":\"" + Instant.now() + "\",\"message\":\"" + escape(message) + "\"}";
		try {
			Files.createDirectories(result.getParent());
			Files.writeString(result, json, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// nothing else to report to
		}
	}

	private static String escape(String text) {
		StringBuilder out = new StringBuilder();
		for (char c : text.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}
}
