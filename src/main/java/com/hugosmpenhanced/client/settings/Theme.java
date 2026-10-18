package com.hugosmpenhanced.client.settings;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Colours and small drawing helpers of the settings menu. Neutral dark greys everywhere; colour
 * only where you click: green for "on", gold for buttons and the selection, red for deleting.
 */
public final class Theme {
	public static final int FRAME = 0xEE141414;
	public static final int FRAME_BORDER = 0xFF3C3C3C;
	public static final int SIDEBAR = 0xC80D0D0D;
	public static final int SECTION = 0xDC1E1E1E;
	public static final int SECTION_BORDER = 0xFF393939;
	public static final int DIVIDER = 0x0EFFFFFF;
	public static final int ROW_HOVER = 0x12FFFFFF;
	public static final int SELECTED = 0x16FFFFFF;

	public static final int TEXT = 0xFFE8E8E8;
	public static final int TEXT_DIM = 0xFFC3C3C3;
	public static final int MUTED = 0xFF969696;
	public static final int FAINT = 0xFF606060;
	public static final int WHITE = 0xFFFFFFFF;

	public static final int GOLD = 0xFFFFC83D;
	public static final int GOLD_FILL = 0x46FFC83D;
	public static final int GREEN = 0xFF3CBE5A;
	public static final int GREEN_EDGE = 0xFF14461F;
	public static final int RED = 0xFFDC3C3C;
	public static final int RED_FILL = 0x5A781919;
	public static final int RED_TEXT = 0xFFFF7878;

	public static final int FIELD = 0xFF0B0B0B;
	public static final int FIELD_BORDER = 0xFF4A4A4A;
	public static final int CONTROL = 0xFF1F1F1F;
	public static final int CONTROL_BORDER = 0xFF505050;
	public static final int CHIP = 0xFF2C2C2C;
	public static final int CHIP_BORDER = 0xFF5A5A5A;

	public static final int ICON_ON = 0xFFFFFFFF;
	public static final int ICON_OFF = 0xFF9A9A9A;

	private Theme() {
	}

	public static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "settings/" + name);
	}

	public static void icon(GuiGraphics graphics, String name, int x, int y, int size, int color) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, size, size, color);
	}

	/** A box with cut corners and a one pixel border. */
	public static void panel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
		int right = x + width;
		int bottom = y + height;
		graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
		if (border != 0) {
			graphics.fill(x + 1, y, right - 1, y + 1, border);
			graphics.fill(x + 1, bottom - 1, right - 1, bottom, border);
			graphics.fill(x, y + 1, x + 1, bottom - 1, border);
			graphics.fill(right - 1, y + 1, right, bottom - 1, border);
		}
	}

	public static void toggle(GuiGraphics graphics, int x, int y, boolean on, boolean hovered) {
		if (on) {
			panel(graphics, x, y, 22, 11, GREEN, GREEN_EDGE);
			graphics.fill(x + 13, y + 2, x + 20, y + 9, WHITE);
		} else {
			panel(graphics, x, y, 22, 11, hovered ? 0xFF2A2A2A : CONTROL, hovered ? 0xFF7A7A7A : CONTROL_BORDER);
			graphics.fill(x + 2, y + 2, x + 9, y + 9, 0xFF828282);
		}
	}

	/** Small pixel glyphs drawn from patterns ('#' = pixel). */
	public static void glyph(GuiGraphics graphics, String[] pattern, int x, int y, int color) {
		for (int row = 0; row < pattern.length; row++) {
			String line = pattern[row];
			for (int col = 0; col < line.length(); col++) {
				if (line.charAt(col) == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
				}
			}
		}
	}

	public static final String[] CROSS = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
	public static final String[] PLUS = {"..#..", "..#..", "#####", "..#..", "..#.."};
	public static final String[] CHEVRON = {"#...", ".#..", "..#.", ".#..", "#..."};
	public static final String[] DOWN = {"#####", ".###.", "..#.."};
	public static final String[] RESET = {"..#..", ".####", "#.#.#", "#...#", ".###."};
}
