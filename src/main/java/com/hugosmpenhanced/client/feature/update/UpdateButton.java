package com.hugosmpenhanced.client.feature.update;

import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A button in the settings menu's look: gold for the main action, grey for the rest. */
class UpdateButton extends Button {
	private final boolean primary;

	UpdateButton(int x, int y, int width, int height, String message, boolean primary, OnPress onPress) {
		super(x, y, width, height, Component.literal(message), onPress, DEFAULT_NARRATION);
		this.primary = primary;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.getX();
		int y = this.getY();
		boolean over = this.active && this.isHoveredOrFocused();
		if (!this.active) {
			Theme.panel(graphics, x, y, this.width, this.height, 0xFF161616, 0xFF2E2E2E);
		} else if (this.primary) {
			Theme.panel(graphics, x, y, this.width, this.height, over ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
		} else {
			Theme.panel(graphics, x, y, this.width, this.height, over ? 0xFF2A2A2A : Theme.CONTROL, over ? 0xFF7A7A7A : Theme.CONTROL_BORDER);
		}
		var font = Minecraft.getInstance().font;
		int color = !this.active ? Theme.FAINT : this.primary || over ? Theme.WHITE : Theme.TEXT_DIM;
		graphics.centeredText(font, this.getMessage(), x + this.width / 2, y + (this.height - 8) / 2, color);
	}
}
