package com.hugosmpenhanced.client.feature.inventorybuttons;

public enum InventoryButtonPosition {
	RIGHT("Rechts"),
	TOP("Oben");

	private final String displayName;

	InventoryButtonPosition(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}
}
