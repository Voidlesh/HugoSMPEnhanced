package com.hugosmpenhanced.client.settings.option;

import java.util.function.Supplier;

import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Read-only text on the right, e.g. the version. */
public class InfoOption extends SettingsOption {
	private final Supplier<String> value;

	public InfoOption(String label, String description, Supplier<String> value) {
		super(label, description);
		this.value = value;
	}

	@Override
	protected int controlWidth(Font font) {
		return font.width(this.value.get());
	}

	@Override
	protected void renderControl(GuiGraphics graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		String text = this.value.get();
		graphics.drawString(font, text, right - font.width(text), centerY - 4, Theme.MUTED, false);
	}
}
