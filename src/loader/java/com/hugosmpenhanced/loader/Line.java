package com.hugosmpenhanced.loader;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Which copy of the mod belongs to the running Minecraft: the 1.21.11 one (remapped, Java 21 code, the plain
 * {@code com.hugosmpenhanced} package) or one of the 26.x ones (unobfuscated, Java 25 code, one package per line:
 * {@code mc26_1}, {@code mc26_2}, {@code mc26_3}). 26.3 also serves everything newer.
 */
public final class Line {
	public static final String ROOT = "com.hugosmpenhanced";

	private static String current;

	private Line() {
	}

	/** Package segment of the copy to use: empty for 1.21.x, otherwise {@code mc26_1}, {@code mc26_2} or {@code mc26_3}. */
	public static String current() {
		if (current == null) {
			String version = FabricLoader.getInstance().getModContainer("minecraft")
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.21.11");
			current = of(version);
		}
		return current;
	}

	static String of(String version) {
		String[] parts = version.split("[^0-9]+");
		int major = number(parts, 0, 1);
		if (major < 26) {
			return "";
		}
		int minor = major == 26 ? number(parts, 1, 3) : 3;
		return "mc26_" + Math.max(1, Math.min(minor, 3));
	}

	private static int number(String[] parts, int index, int fallback) {
		try {
			return index < parts.length && !parts[index].isEmpty() ? Integer.parseInt(parts[index]) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** The mixin or class package of a copy: its segment right after {@code com.hugosmpenhanced}, or empty. */
	public static String lineOf(String packageName) {
		String rest = packageName.startsWith(ROOT + ".") ? packageName.substring(ROOT.length() + 1) : "";
		return rest.startsWith("mc26_") ? rest.substring(0, rest.indexOf('.') < 0 ? rest.length() : rest.indexOf('.')) : "";
	}

	/** Prefix a class name of the 1.21.11 code with the package of the running copy. */
	public static String className(String legacyName) {
		String line = current();
		return line.isEmpty() ? legacyName : ROOT + "." + line + legacyName.substring(ROOT.length());
	}
}
