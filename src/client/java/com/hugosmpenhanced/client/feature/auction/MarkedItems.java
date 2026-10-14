package com.hugosmpenhanced.client.feature.auction;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.world.item.ItemStack;

/**
 * Items marked in the auction search: in the auction house and in orders they get a coloured
 * frame, so e.g. sand stands out from suspicious sand. Matched by the same key as the remembered
 * prices: item id, enchantment of a single-enchantment book, or the HugoSMP item id.
 */
public final class MarkedItems {
	public static final int COLOR = 0xFF3FD8FF;
	public static final int FILL = 0x403FD8FF;

	private MarkedItems() {
	}

	public static boolean isMarked(String key) {
		return key != null && ModConfigManager.CONFIG.auctionMarkedItems.contains(key);
	}

	public static boolean isMarked(ItemStack stack) {
		List<String> marked = ModConfigManager.CONFIG.auctionMarkedItems;
		return !marked.isEmpty() && !stack.isEmpty() && marked.contains(AuctionPriceMemory.keyOf(stack));
	}

	public static void toggle(String key) {
		if (key == null) {
			return;
		}
		List<String> marked = ModConfigManager.CONFIG.auctionMarkedItems;
		if (!marked.remove(key)) {
			marked.add(key);
		}
		ModConfigManager.save();
	}

	public static void clear() {
		ModConfigManager.CONFIG.auctionMarkedItems.clear();
		ModConfigManager.save();
	}
}
