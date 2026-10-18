package com.hugosmpenhanced.client.feature.inventorybuttons;

/** A button on one of the inventory's spots: its widget, its saved spot and what a click does. */
public record PlacedButton(IconButton button, InventoryButton entry, Runnable onClick) {
}
