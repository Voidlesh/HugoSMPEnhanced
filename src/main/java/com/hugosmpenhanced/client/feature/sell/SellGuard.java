package com.hugosmpenhanced.client.feature.sell;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Decides which stacks are never sold, whatever the rules say. Only plain items as they drop from a
 * farm pass: anything renamed, enchanted, filled, damaged or otherwise changed by the server stays.
 * This compares the whole stack, not just the item id, so a rule for shulker boxes never sells one
 * with items inside and a rule for books never sells a Mending book.
 */
public final class SellGuard {
	private SellGuard() {
	}

	/** Why this stack must not be sold, or null when it is a plain item. */
	public static String protectedReason(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		DataComponentPatch patch = stack.getComponentsPatch();
		if (patch.isEmpty()) {
			return null;
		}
		if (stack.has(DataComponents.CUSTOM_NAME)) {
			return "umbenannt";
		}
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if ((enchantments != null && !enchantments.isEmpty()) || (stored != null && !stored.isEmpty())) {
			return "verzaubert";
		}
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if ((container != null && container.nonEmptyItems().iterator().hasNext()) || (bundle != null && !bundle.isEmpty())) {
			return "hat Inhalt";
		}
		if (stack.isDamaged()) {
			return "beschädigt";
		}
		return "verändert";
	}
}
