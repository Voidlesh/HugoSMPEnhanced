package com.hugosmpenhanced.client.settings.option;

import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** A number between min and max, snapped to step; saved when the mouse is let go. */
public class SliderOption extends ValueOption<Double> {
	private static final int TRACK = 90;
	private static final int VALUE = 46;

	private final double min;
	private DoubleSupplier max;
	private final double step;
	private final DoubleFunction<String> format;
	private boolean dragging;
	private boolean applyOnRelease;
	/** While dragging a slider that applies on release: the value shown but not yet set. */
	private Double pending;

	public SliderOption(String label, String description, double min, double max, double step, DoubleFunction<String> format,
		Function<ModConfig, Double> getter, BiConsumer<ModConfig, Double> setter) {
		super(label, description, getter, setter);
		this.min = min;
		this.max = () -> max;
		this.step = step;
		this.format = format;
	}

	public SliderOption(String label, String description, double min, double max, double step, DoubleFunction<String> format,
		Binding<Double> binding) {
		super(label, description, binding);
		this.min = min;
		this.max = () -> max;
		this.step = step;
		this.format = format;
	}

	public static SliderOption ofInt(String label, String description, int min, int max, DoubleFunction<String> format,
		Function<ModConfig, Integer> getter, BiConsumer<ModConfig, Integer> setter) {
		return new SliderOption(label, description, min, max, 1, format,
			config -> getter.apply(config).doubleValue(), (config, value) -> setter.accept(config, (int) Math.round(value)));
	}

	/** Sets the value only when the mouse is let go, for options that are expensive to change (view distance). */
	public SliderOption applyOnRelease() {
		this.applyOnRelease = true;
		return this;
	}

	/** For ranges that depend on the window, like the GUI scale. */
	public SliderOption max(DoubleSupplier max) {
		this.max = max;
		return this;
	}

	private double shown() {
		return this.pending != null ? this.pending : this.get();
	}

	private int trackLeft() {
		return this.x + this.width - 8 - VALUE - TRACK;
	}

	@Override
	protected int controlWidth(Font font) {
		return TRACK + VALUE;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		int left = right - VALUE - TRACK;
		double max = this.max.getAsDouble();
		double fraction = max <= this.min ? 0 : (this.shown() - this.min) / (max - this.min);
		int knob = left + (int) Math.round(Mth.clamp(fraction, 0, 1) * (TRACK - 6));
		graphics.fill(left, centerY, left + TRACK, centerY + 1, 0xFF464646);
		graphics.fill(left, centerY, knob + 3, centerY + 1, 0xFFDCDCDC);
		boolean active = this.dragging || this.inside(mouseX, mouseY, left - 2, centerY - 6, TRACK + 4, 13);
		Theme.panel(graphics, knob, centerY - 4, 6, 9, Theme.WHITE, active ? Theme.GOLD : 0xFF282828);
		String text = this.format.apply(this.shown());
		graphics.text(font, text, right - font.width(text), centerY - 4, Theme.TEXT);
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
			if (this.pending != null) {
				this.set(this.pending);
				this.pending = null;
			}
			this.save();
		}
	}

	private void apply(double mouseX) {
		double fraction = Mth.clamp((mouseX - this.trackLeft() - 3) / (TRACK - 6), 0, 1);
		double max = this.max.getAsDouble();
		double raw = this.min + fraction * (max - this.min);
		double snapped = Math.round(raw / this.step) * this.step;
		// Avoid 0.7000000001 style values in the saved config.
		snapped = Math.round(snapped * 1000.0) / 1000.0;
		double value = Mth.clamp(snapped, this.min, max);
		if (this.applyOnRelease) {
			this.pending = value;
		} else {
			this.set(value);
		}
	}
}
