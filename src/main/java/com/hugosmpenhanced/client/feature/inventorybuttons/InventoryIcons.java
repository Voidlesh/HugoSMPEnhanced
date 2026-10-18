package com.hugosmpenhanced.client.feature.inventorybuttons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryIcons {
	private InventoryIcons() {
	}

	/** The item for an id like "minecraft:diamond" or "diamond", or a barrier if it does not exist. */
	public static ItemStack stack(String id) {
		Item item = item(id);
		return new ItemStack(item == null ? Items.BARRIER : item);
	}

	public static Item item(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		Identifier key = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
		if (key == null) {
			return null;
		}
		return BuiltInRegistries.ITEM.getOptional(key).filter(item -> item != Items.AIR).orElse(null);
	}

	public static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Items whose id or translated name contains the query, in registry order. */
	public static List<Item> search(String query, int limit) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Item> result = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			if (q.isEmpty() || id(item).contains(q)
				|| item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT).contains(q)) {
				result.add(item);
				if (result.size() >= limit) {
					break;
				}
			}
		}
		return result;
	}
}
