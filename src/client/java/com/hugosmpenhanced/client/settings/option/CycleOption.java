package com.hugosmpenhanced.client.settings.option;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.settings.Labeled;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Picks one enum value: left click goes to the next, right click to the previous. */
public class CycleOption<E extends Enum<E> & Labeled> extends ValueOption<E> {
	private final E[] values;

	public CycleOption(String label, String description, Class<E> type, Function<ModConfig, E> getter, BiConsumer<ModConfig, E> setter) {
		super(label, description, getter, setter);
		this.values = type.getEnumConstants();
	}

	@Override
	protected int controlWidth(Font font) {
		int widest = 0;
		for (E value : this.values) {
			widest = Math.max(widest, font.width(value.getDisplayName()));
		}
		return widest + 22;
	}

	@Override
	protected void renderControl(GuiGraphics graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		int w = this.controlWidth(font);
		int left = right - w;
		boolean over = this.inside(mouseX, mouseY, left, centerY - 7, w, 15);
		Theme.panel(graphics, left, centerY - 7, w, 15, Theme.FIELD, over ? Theme.GOLD : Theme.FIELD_BORDER);
		graphics.drawString(font, this.get().getDisplayName(), left + 6, centerY - 3, Theme.WHITE, false);
		Theme.glyph(graphics, Theme.DOWN, right - 11, centerY - 1, Theme.MUTED);
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		int index = this.get().ordinal() + (button == 1 ? -1 : 1);
		this.set(this.values[Math.floorMod(index, this.values.length)]);
		changed();
		return true;
	}
}
