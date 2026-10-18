package com.hugosmpenhanced.client.feature.inventorylayout;

import java.util.ArrayList;
import java.util.List;

/** One saved inventory layout: a name and an item id per player inventory slot ("" = anything). */
public class SavedInventoryLayout {
	public String name = "";
	public List<String> slots = new ArrayList<>();

	public SavedInventoryLayout() {
	}

	public SavedInventoryLayout(String name, List<String> slots) {
		this.name = name;
		this.slots = slots;
	}
}
