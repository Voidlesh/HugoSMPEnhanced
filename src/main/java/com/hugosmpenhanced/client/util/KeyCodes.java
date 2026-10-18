package com.hugosmpenhanced.client.util;

/**
 * Key codes that Minecraft 26.3 sends but does not name in {@code InputConstants}. The values are the SDL key codes,
 * the same numbers the game uses, so the keys still match the events the game sends.
 */
public final class KeyCodes {
	public static final int UNKNOWN = 0;
	public static final int KP_PERIOD = 99;
	public static final int KP_DIVIDE = 84;
	public static final int KP_SUBTRACT = 86;
	public static final int MENU = 101;
	public static final int WORLD_2 = 100;

	private KeyCodes() {
	}
}
