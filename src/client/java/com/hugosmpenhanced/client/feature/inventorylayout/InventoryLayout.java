package com.hugosmpenhanced.client.feature.inventorylayout;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryIcons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The saved inventory layout: one item id per player inventory slot (hotbar, main inventory,
 * armour, offhand), "" where the slot was empty and anything may go. While shown, empty slots get
 * a faded ghost of the item that belongs there and slots holding a different item are tinted red,
 * in every container screen that shows the player inventory.
 */
public final class InventoryLayout {
	/** Lays the slot background colour over the ghost item so it reads as transparent. */
	private static final int GHOST_FADE = 0x998B8B8B;
	private static final int WRONG_TINT = 0x60FF2020;

	private static List<String> cachedSource;
	private static Item[] expected = new Item[0];

	private InventoryLayout() {
	}

	public static boolean hasLayout() {
		for (String id : ModConfigManager.CONFIG.inventoryLayout) {
			if (id != null && !id.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Whether the saved layout already matches what the inventory holds now. */
	public static boolean matches(Inventory inventory) {
		return current(inventory).equals(ModConfigManager.CONFIG.inventoryLayout);
	}

	public static void save(Inventory inventory) {
		ModConfigManager.CONFIG.inventoryLayout = current(inventory);
		ModConfigManager.save();
	}

	public static void clear() {
		ModConfigManager.CONFIG.inventoryLayout = new ArrayList<>();
		ModConfigManager.CONFIG.inventoryLayoutShown = false;
		ModConfigManager.save();
	}

	private static List<String> current(Inventory inventory) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			ids.add(stack.isEmpty() ? "" : InventoryIcons.id(stack.getItem()));
		}
		return ids;
	}

	/** The item the layout wants in a player inventory slot, or null when any item may go there. */
	private static Item expectedAt(int index) {
		List<String> source = ModConfigManager.CONFIG.inventoryLayout;
		if (source != cachedSource) {
			cachedSource = source;
			expected = new Item[source.size()];
			for (int i = 0; i < source.size(); i++) {
				expected[i] = InventoryIcons.item(source.get(i));
			}
		}
		return index >= 0 && index < expected.length ? expected[index] : null;
	}

	/** Called after vanilla drew a slot, in the screen's slot coordinates. */
	public static void renderSlot(GuiGraphics graphics, Slot slot) {
		ModConfig config = ModConfigManager.CONFIG;
		if (!config.inventoryLayoutEnabled || !config.inventoryLayoutShown || !(slot.container instanceof Inventory)) {
			return;
		}
		Item want = expectedAt(slot.getContainerSlot());
		if (want == null) {
			return;
		}
		ItemStack actual = slot.getItem();
		int x = slot.x;
		int y = slot.y;
		if (actual.isEmpty()) {
			graphics.renderFakeItem(new ItemStack(want), x, y);
			graphics.fill(x, y, x + 16, y + 16, GHOST_FADE);
		} else if (actual.getItem() != want) {
			graphics.fill(x, y, x + 16, y + 16, WRONG_TINT);
		}
	}
}
