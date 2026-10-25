package com.hugosmpenhanced.loader;

import net.fabricmc.loader.api.FabricLoader;

/** Which Minecraft line is running: 1.21.x (remapped, Java 21 code) or 26.x and later (unobfuscated, Java 25 code). */
public final class Line {
	public static final String NEW_PACKAGE = "com.hugosmpenhanced.mc26";

	private static Boolean modern;

	private Line() {
	}

	/** True for Minecraft 26.x and later, whose version numbers no longer start with 1. */
	public static boolean modern() {
		if (modern == null) {
			String version = FabricLoader.getInstance().getModContainer("minecraft")
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.21.11");
			int end = version.indexOf('.');
			int major;
			try {
				major = Integer.parseInt(end < 0 ? version : version.substring(0, end));
			} catch (NumberFormatException e) {
				major = 1;
			}
			modern = major >= 26;
		}
		return modern;
	}

	/** Prefix a class name of the 1.21.11 code with the package of this line's copy. */
	public static String className(String legacyName) {
		return modern() ? NEW_PACKAGE + legacyName.substring("com.hugosmpenhanced".length()) : legacyName;
	}
}
