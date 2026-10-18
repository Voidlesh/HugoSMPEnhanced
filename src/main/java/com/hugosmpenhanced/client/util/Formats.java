package com.hugosmpenhanced.client.util;

import net.minecraft.ChatFormatting;

/**
 * Colour and code of a chat formatting. Minecraft 26.3 no longer exposes these on {@link ChatFormatting}, so the
 * vanilla values are kept here.
 */
public final class Formats {
	private static final int[] COLORS = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
	};

	private Formats() {
	}

	/** Whether this formatting is one of the 16 colours (not bold, italic and so on). */
	public static boolean isColor(ChatFormatting formatting) {
		return formatting.ordinal() < COLORS.length;
	}

	/** The RGB value of a colour formatting, 0 for the style codes. */
	public static int color(ChatFormatting formatting) {
		return isColor(formatting) ? COLORS[formatting.ordinal()] : 0;
	}

	/** The character after the section sign, like {@code 'a'} for green. */
	public static char code(ChatFormatting formatting) {
		return formatting.toString().charAt(1);
	}
}
