package com.hugosmpenhanced.client.feature.inventorybuttons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A square button in the style of a dark search field: a translucent dark fill with a thin grey
 * border that lightens on hover. Shows an item, or a short text symbol when no item is set.
 */
public class IconButton extends Button {
	private static final int FILL = 0xB0181818;
	private static final int FILL_HOVER = 0xC0303030;
	private static final int BORDER = 0xFF555555;
	private static final int BORDER_HOVER = 0xFFB0B0B0;
	private static final int TEXT = 0xFFE0E0E0;

	private ItemStack icon;
	private final String symbol;

	public IconButton(ItemStack icon, Component narration, OnPress onPress) {
		this(icon, "", narration, onPress);
	}

	public IconButton(String symbol, Component narration, OnPress onPress) {
		this(ItemStack.EMPTY, symbol, narration, onPress);
	}

	private IconButton(ItemStack icon, String symbol, Component narration, OnPress onPress) {
		super(0, 0, InventoryButtonsFeature.SIZE, InventoryButtonsFeature.SIZE, narration, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		this.symbol = symbol;
	}

	public void setIcon(ItemStack icon) {
		this.icon = icon;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.getX();
		int y = this.getY();
		int right = x + this.width;
		int bottom = y + this.height;
		boolean highlighted = this.active && this.isHoveredOrFocused();

		graphics.fill(x + 1, y + 1, right - 1, bottom - 1, highlighted ? FILL_HOVER : FILL);
		int border = highlighted ? BORDER_HOVER : BORDER;
		graphics.fill(x, y, right, y + 1, border);
		graphics.fill(x, bottom - 1, right, bottom, border);
		graphics.fill(x, y + 1, x + 1, bottom - 1, border);
		graphics.fill(right - 1, y + 1, right, bottom - 1, border);

		if (!this.icon.isEmpty()) {
			graphics.renderItem(this.icon, x + (this.width - 16) / 2, y + (this.height - 16) / 2);
		} else if (!this.symbol.isEmpty()) {
			var font = Minecraft.getInstance().font;
			graphics.drawCenteredString(font, this.symbol, x + this.width / 2, y + (this.height - 8) / 2, TEXT);
		}
	}
}
