package com.hugosmpenhanced.client.feature.inventorybuttons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A square vanilla button showing an item instead of a label. */
public class IconButton extends Button {
	private ItemStack icon;

	public IconButton(ItemStack icon, Component narration, OnPress onPress) {
		super(0, 0, InventoryButtonsFeature.SIZE, InventoryButtonsFeature.SIZE, narration, onPress, DEFAULT_NARRATION);
		this.icon = icon;
	}

	public void setIcon(ItemStack icon) {
		this.icon = icon;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderDefaultSprite(graphics);
		graphics.renderItem(this.icon, this.getX() + (this.width - 16) / 2, this.getY() + (this.height - 16) / 2);
	}
}
