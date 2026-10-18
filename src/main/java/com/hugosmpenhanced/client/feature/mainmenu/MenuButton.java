package com.hugosmpenhanced.client.feature.mainmenu;

import com.hugosmpenhanced.client.gui.HugoStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A main menu button in {@link HugoStyle}, optionally left-aligned and with a gold accent bar. */
public class MenuButton extends Button {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_HOVER = 0xFFFFFFFF;
	private static final int ACCENT = 0xFFF0AF28;
	private static final int ACCENT_TEXT = 0xFFFFD75A;

	private final boolean leftAligned;
	private final boolean accent;

	public MenuButton(int x, int y, int width, int height, Component message, boolean leftAligned, boolean accent,
			OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.leftAligned = leftAligned;
		this.accent = accent;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.getX();
		int y = this.getY();
		HugoStyle.State state = !this.active ? HugoStyle.State.DISABLED
			: this.isHoveredOrFocused() ? HugoStyle.State.HOVER : HugoStyle.State.NORMAL;
		HugoStyle.drawBox(graphics, x, y, this.width, this.height, state);
		if (this.accent) {
			graphics.fill(x, y, x + 2, y + this.height, ACCENT);
		}

		var font = Minecraft.getInstance().font;
		int color = this.accent ? ACCENT_TEXT : state == HugoStyle.State.HOVER ? TEXT_HOVER : TEXT;
		if (!this.active) {
			color = 0xFF808080;
		}
		int textY = y + (this.height - 8) / 2;
		if (this.leftAligned) {
			graphics.drawString(font, this.getMessage(), x + (this.accent ? 10 : 8), textY, color);
		} else {
			graphics.drawCenteredString(font, this.getMessage(), x + this.width / 2, textY, color);
		}
	}
}
