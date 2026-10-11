package com.hugosmpenhanced.client.settings.option;

import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** A number between min and max, snapped to step; saved when the mouse is let go. */
public class SliderOption extends ValueOption<Double> {
	private static final int TRACK = 90;
	private static final int VALUE = 46;

	private final double min;
	private final double max;
	private final double step;
	private final DoubleFunction<String> format;
	private boolean dragging;

	public SliderOption(String label, String description, double min, double max, double step, DoubleFunction<String> format,
		Function<ModConfig, Double> getter, BiConsumer<ModConfig, Double> setter) {
		super(label, description, getter, setter);
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
	}

	public static SliderOption ofInt(String label, String description, int min, int max, DoubleFunction<String> format,
		Function<ModConfig, Integer> getter, BiConsumer<ModConfig, Integer> setter) {
		return new SliderOption(label, description, min, max, 1, format,
			config -> getter.apply(config).doubleValue(), (config, value) -> setter.accept(config, (int) Math.round(value)));
	}

	private int trackLeft() {
		return this.x + this.width - 8 - VALUE - TRACK;
	}

	@Override
	protected int controlWidth(Font font) {
		return TRACK + VALUE;
	}

	@Override
	protected void renderControl(GuiGraphics graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		int left = right - VALUE - TRACK;
		double fraction = (this.get() - this.min) / (this.max - this.min);
		int knob = left + (int) Math.round(Mth.clamp(fraction, 0, 1) * (TRACK - 6));
		graphics.fill(left, centerY, left + TRACK, centerY + 1, 0xFF464646);
		graphics.fill(left, centerY, knob + 3, centerY + 1, 0xFFDCDCDC);
		boolean active = this.dragging || this.inside(mouseX, mouseY, left - 2, centerY - 6, TRACK + 4, 13);
		Theme.panel(graphics, knob, centerY - 4, 6, 9, Theme.WHITE, active ? Theme.GOLD : 0xFF282828);
		String text = this.format.apply(this.get());
		graphics.drawString(font, text, right - font.width(text), centerY - 4, Theme.TEXT);
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		int left = this.trackLeft();
		if (button != 0 || mouseX < left - 4 || mouseX > left + TRACK + 4) {
			return false;
		}
		this.dragging = true;
		this.apply(mouseX);
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		if (!this.dragging) {
			return false;
		}
		this.apply(mouseX);
		return true;
	}

	@Override
	public void mouseReleased() {
		if (this.dragging) {
			this.dragging = false;
			changed();
		}
	}

	private void apply(double mouseX) {
		double fraction = Mth.clamp((mouseX - this.trackLeft() - 3) / (TRACK - 6), 0, 1);
		double raw = this.min + fraction * (this.max - this.min);
		double snapped = Math.round(raw / this.step) * this.step;
		// Avoid 0.7000000001 style values in the saved config.
		snapped = Math.round(snapped * 1000.0) / 1000.0;
		this.set(Mth.clamp(snapped, this.min, this.max));
	}
}
