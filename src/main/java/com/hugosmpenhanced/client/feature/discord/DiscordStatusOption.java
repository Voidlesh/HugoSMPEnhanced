package com.hugosmpenhanced.client.feature.discord;

import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Whether the Discord app was found, with a green dot once connected. */
public class DiscordStatusOption extends SettingsOption {
	public DiscordStatusOption() {
		super("Verbindung", null);
	}

	@Override
	protected int controlWidth(Font font) {
		return font.width(DiscordPresence.statusText()) + 8;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		Component status = DiscordPresence.statusText();
		int w = font.width(status);
		int dot = DiscordPresence.connected() ? Theme.GREEN : Theme.FAINT;
		graphics.fill(right - w - 8, centerY - 2, right - w - 4, centerY + 2, dot);
		graphics.text(font, status, right - w, centerY - 4, Theme.WHITE, false);
	}
}
