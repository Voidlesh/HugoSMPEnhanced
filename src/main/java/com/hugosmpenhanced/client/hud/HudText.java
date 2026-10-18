package com.hugosmpenhanced.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A small HUD box of text lines, each made of coloured parts, in the same look as the AFK farm
 * pill. Optional bars under a line show progress (a spawner's storage, durability).
 */
public final class HudText {
	public static final int TEXT = 0xFFE8E8E8;
	public static final int MUTED = 0xFF9A9A9A;
	public static final int GOLD = 0xFFFFC83D;
	public static final int GREEN = 0xFF5ADC78;
	public static final int RED = 0xFFFF6464;
	public static final int ORANGE = 0xFFF0BE3C;
	private static final int BG = 0xD80E0E0E;
	private static final int BORDER = 0xFF464646;
	private static final int LINE = 11;
	private static final int BAR = 4;

	private record Part(String text, int color, int minWidth) {
	}

	private static final class Line {
		final List<Part> parts = new ArrayList<>();
		int barPercent = -1;
		int barColor;
	}

	private final List<Line> lines = new ArrayList<>();
	private Line current;

	public HudText line() {
		this.current = new Line();
		this.lines.add(this.current);
		return this;
	}

	public HudText add(String text, int color) {
		if (this.current == null) {
			this.line();
		}
		this.current.parts.add(new Part(text, color, 0));
		return this;
	}

	/** A part at least {@code minWidth} wide, so columns line up between lines. */
	public HudText column(String text, int color, int minWidth) {
		if (this.current == null) {
			this.line();
		}
		this.current.parts.add(new Part(text, color, minWidth));
		return this;
	}

	/** A thin bar under the current line, 0–100. */
	public HudText bar(int percent, int color) {
		if (this.current != null) {
			this.current.barPercent = Math.max(0, Math.min(100, percent));
			this.current.barColor = color;
		}
		return this;
	}

	public boolean isEmpty() {
		return this.lines.isEmpty();
	}

	private static int lineWidth(Font font, Line line) {
		int width = 0;
		for (Part part : line.parts) {
			width += Math.max(part.minWidth(), font.width(part.text()));
		}
		return width;
	}

	public int width(Font font) {
		int width = 0;
		for (Line line : this.lines) {
			width = Math.max(width, lineWidth(font, line));
		}
		return this.lines.isEmpty() ? 0 : width + 10;
	}

	public int height() {
		int height = 0;
		for (Line line : this.lines) {
			height += LINE + (line.barPercent >= 0 ? BAR : 0);
		}
		return this.lines.isEmpty() ? 0 : height + 5;
	}

	public void render(GuiGraphicsExtractor graphics, Font font) {
		if (this.lines.isEmpty()) {
			return;
		}
		int width = this.width(font);
		int height = this.height();
		graphics.fill(1, 0, width - 1, height, BG);
		graphics.fill(0, 1, width, height - 1, BG);
		graphics.fill(1, 0, width - 1, 1, BORDER);
		graphics.fill(1, height - 1, width - 1, height, BORDER);
		graphics.fill(0, 1, 1, height - 1, BORDER);
		graphics.fill(width - 1, 1, width, height - 1, BORDER);
		int y = 4;
		for (Line line : this.lines) {
			int x = 5;
			for (Part part : line.parts) {
				graphics.text(font, part.text(), x, y, part.color(), true);
				x += Math.max(part.minWidth(), font.width(part.text()));
			}
			y += LINE;
			if (line.barPercent >= 0) {
				graphics.fill(5, y - 2, width - 5, y, 0xFF2D2D2D);
				graphics.fill(5, y - 2, 5 + (width - 10) * line.barPercent / 100, y, line.barColor);
				y += BAR;
			}
		}
	}
}
