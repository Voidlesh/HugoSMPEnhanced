package com.hugosmpenhanced.client.feature.inventorybuttons;

/** One configurable button next to the player inventory. */
public class InventoryButton {
	public String name = "";
	public String action = "";
	public String icon = "minecraft:compass";
	public InventoryButtonPosition position = InventoryButtonPosition.RIGHT;

	public InventoryButton() {
	}

	public InventoryButton copy() {
		InventoryButton copy = new InventoryButton();
		copy.name = this.name;
		copy.action = this.action;
		copy.icon = this.icon;
		copy.position = this.position;
		return copy;
	}
}
