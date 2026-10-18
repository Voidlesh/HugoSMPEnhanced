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
 * The saved inventory layouts: each holds one item id per player inventory slot (hotbar, main
 * inventory, armour, offhand), "" where the slot was empty and anything may go. While one is shown
 * (or previewed from the layout menu), empty slots get a faded ghost of the item that belongs there
 * and slots holding a different item are tinted red, in every container screen that shows the
 * player inventory.
 */
public final class InventoryLayout {
	public static final int MAX_LAYOUTS = 9;
	/** Lays the slot background colour over the ghost item so it reads as transparent. */
	private static final int GHOST_FADE = 0x608B8B8B;
	private static final int WRONG_TINT = 0x60FF2020;

	/** Layout hovered in the open layout menu, shown instead of the selected one; -1 for none. */
	static int previewIndex = -1;

	private static List<String> cachedSource;
	private static Item[] expected = new Item[0];

	private InventoryLayout() {
	}

	/** The saved layouts, after moving a layout from the first version of the feature over. */
	public static List<SavedInventoryLayout> layouts() {
		ModConfig config = ModConfigManager.CONFIG;
		if (config.inventoryLayouts == null) {
			config.inventoryLayouts = new ArrayList<>();
		}
		if (config.inventoryLayout != null) {
			if (config.inventoryLayout.stream().anyMatch(id -> !id.isEmpty())) {
				config.inventoryLayouts.add(new SavedInventoryLayout("Layout 1", config.inventoryLayout));
				config.inventoryLayoutShownIndex = config.inventoryLayoutShown ? config.inventoryLayouts.size() - 1 : -1;
			}
			config.inventoryLayout = null;
			config.inventoryLayoutShown = false;
			ModConfigManager.save();
		}
		return config.inventoryLayouts;
	}

	public static int shownIndex() {
		int index = ModConfigManager.CONFIG.inventoryLayoutShownIndex;
		return index >= 0 && index < layouts().size() ? index : -1;
	}

	public static void show(int index) {
		ModConfigManager.CONFIG.inventoryLayoutShownIndex = index;
		ModConfigManager.save();
	}

	/** Saves the inventory as a new layout and shows it; returns its index. */
	public static int saveNew(Inventory inventory) {
		List<SavedInventoryLayout> layouts = layouts();
		layouts.add(new SavedInventoryLayout(freeName(), current(inventory)));
		ModConfigManager.save();
		return layouts.size() - 1;
	}

	public static void overwrite(int index, Inventory inventory) {
		layouts().get(index).slots = current(inventory);
		ModConfigManager.save();
	}

	public static void delete(int index) {
		layouts().remove(index);
		int shown = ModConfigManager.CONFIG.inventoryLayoutShownIndex;
		if (shown == index) {
			ModConfigManager.CONFIG.inventoryLayoutShownIndex = -1;
		} else if (shown > index) {
			ModConfigManager.CONFIG.inventoryLayoutShownIndex = shown - 1;
		}
		ModConfigManager.save();
	}

	public static void clear() {
		layouts().clear();
		ModConfigManager.CONFIG.inventoryLayoutShownIndex = -1;
		ModConfigManager.save();
	}

	/** Whether a layout already matches what the inventory holds now. */
	public static boolean matches(int index, Inventory inventory) {
		return current(inventory).equals(layouts().get(index).slots);
	}

	public static boolean isEmpty(Inventory inventory) {
		return current(inventory).stream().allMatch(String::isEmpty);
	}

	/** "Layout N" with the lowest N no saved layout is called. */
	private static String freeName() {
		for (int n = 1; ; n++) {
			String name = "Layout " + n;
			if (layouts().stream().noneMatch(layout -> name.equals(layout.name))) {
				return name;
			}
		}
	}

	private static List<String> current(Inventory inventory) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			ids.add(stack.isEmpty() ? "" : InventoryIcons.id(stack.getItem()));
		}
		return ids;
	}

	/** The item the drawn layout wants in a player inventory slot, or null when any item may go there. */
	private static Item expectedAt(List<String> source, int index) {
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
		if (!ModConfigManager.CONFIG.inventoryLayoutEnabled || !(slot.container instanceof Inventory)) {
			return;
		}
		List<SavedInventoryLayout> layouts = layouts();
		int index = previewIndex >= 0 && previewIndex < layouts.size() ? previewIndex : shownIndex();
		if (index < 0) {
			return;
		}
		Item want = expectedAt(layouts.get(index).slots, slot.getContainerSlot());
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
