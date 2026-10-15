package com.hugosmpenhanced.client.hud;

/**
 * Where a HUD element sits: {@code x} and {@code y} are fractions of the free room (0 = left/top
 * edge, 1 = right/bottom edge, 0.5 = centred), so an element keeps its corner or centre on every
 * window size and GUI scale. {@code scale} enlarges or shrinks it.
 */
public class HudPos {
	public double x;
	public double y;
	public float scale = 1.0f;

	public HudPos() {
	}

	public HudPos(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public HudPos(double x, double y, float scale) {
		this(x, y);
		this.scale = scale;
	}

	public HudPos copy() {
		return new HudPos(this.x, this.y, this.scale);
	}

	/** Gap kept to the screen edges. */
	public static final int MARGIN = 3;

	/** Left edge in GUI pixels for an element of this (scaled) width. */
	public int left(int screenWidth, int width) {
		return MARGIN + (int) Math.round(clamp(this.x) * Math.max(0, screenWidth - 2 * MARGIN - width));
	}

	public int top(int screenHeight, int height) {
		return MARGIN + (int) Math.round(clamp(this.y) * Math.max(0, screenHeight - 2 * MARGIN - height));
	}

	/** Sets the fractions from a left/top edge in GUI pixels. */
	public void place(int left, int top, int screenWidth, int screenHeight, int width, int height) {
		int roomX = screenWidth - 2 * MARGIN - width;
		int roomY = screenHeight - 2 * MARGIN - height;
		this.x = roomX <= 0 ? 0 : clamp((left - MARGIN) / (double) roomX);
		this.y = roomY <= 0 ? 0 : clamp((top - MARGIN) / (double) roomY);
	}

	private static double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}
}
