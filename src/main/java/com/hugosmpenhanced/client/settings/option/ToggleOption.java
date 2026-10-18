package com.hugosmpenhanced.client.settings.option;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** On/off switch; a click anywhere on the row flips it. */
public class ToggleOption extends ValueOption<Boolean> {
	public ToggleOption(String label, String description, Function<ModConfig, Boolean> getter, BiConsumer<ModConfig, Boolean> setter) {
		super(label, description, getter, setter);
	}

	public ToggleOption(String label, String description, Binding<Boolean> binding) {
		super(label, description, binding);
	}

	@Override
	protected int controlWidth(Font font) {
		return 22;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		Theme.toggle(graphics, right - 22, centerY - 5, this.get(), this.hovered);
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		this.set(!this.get());
		this.save();
		return true;
	}
}
