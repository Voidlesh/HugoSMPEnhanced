package com.hugosmpenhanced.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * The mod's widget look: a translucent dark field with a thin grey border that lightens on hover,
 * like a dark search bar. Vanilla widget sprites are swapped for it on the mod's own screens and
 * on YACL screens (see {@code GuiGraphicsStyleMixin}).
 */
public final class HugoStyle {
	private static final int FILL = 0xB0181818;
	private static final int FILL_HOVER = 0xC0303030;
	private static final int FILL_DISABLED = 0x90101010;
	private static final int BORDER = 0xFF555555;
	private static final int BORDER_HOVER = 0xFFB0B0B0;
	private static final int BORDER_DISABLED = 0xFF333333;
	private static final int HANDLE = 0xFF8A8A8A;
	private static final int HANDLE_HOVER = 0xFFD8D8D8;

	public enum State {
		NORMAL, HOVER, DISABLED
	}

	private HugoStyle() {
	}

	public static void drawBox(GuiGraphics graphics, int x, int y, int width, int height, State state) {
		int right = x + width;
		int bottom = y + height;
		int fill = switch (state) {
			case HOVER -> FILL_HOVER;
			case DISABLED -> FILL_DISABLED;
			default -> FILL;
		};
		int border = switch (state) {
			case HOVER -> BORDER_HOVER;
			case DISABLED -> BORDER_DISABLED;
			default -> BORDER;
		};
		graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
		drawOutline(graphics, x, y, width, height, border);
	}

	public static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		int right = x + width;
		int bottom = y + height;
		graphics.fill(x, y, right, y + 1, color);
		graphics.fill(x, bottom - 1, right, bottom, color);
		graphics.fill(x, y + 1, x + 1, bottom - 1, color);
		graphics.fill(right - 1, y + 1, right, bottom - 1, color);
	}

	/** Whether the screen being shown should use this style instead of the vanilla sprites. */
	public static boolean activeFor(Screen screen) {
		return screen instanceof StyledScreen || (screen != null && screen.getClass().getName().startsWith("dev.isxander.yacl3."));
	}

	/**
	 * Draws the replacement for a vanilla widget sprite and returns true, or returns false for
	 * sprites this style does not cover.
	 */
	public static boolean replaceSprite(GuiGraphics graphics, Identifier sprite, int x, int y, int width, int height) {
		if (!"minecraft".equals(sprite.getNamespace()) || !activeFor(Minecraft.getInstance().screen)) {
			return false;
		}
		switch (sprite.getPath()) {
			case "widget/button", "widget/text_field", "widget/slider" -> drawBox(graphics, x, y, width, height, State.NORMAL);
			case "widget/button_highlighted", "widget/text_field_highlighted", "widget/slider_highlighted" ->
				drawBox(graphics, x, y, width, height, State.HOVER);
			case "widget/button_disabled" -> drawBox(graphics, x, y, width, height, State.DISABLED);
			case "widget/slider_handle" -> graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, HANDLE);
			case "widget/slider_handle_highlighted" -> graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, HANDLE_HOVER);
			default -> {
				return false;
			}
		}
		return true;
	}
}
