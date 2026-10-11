package com.hugosmpenhanced.client.feature.inventorybuttons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The spots buttons snap to, relative to the inventory's top-left corner. Right of the inventory
 * there are two columns of seven spots (numbered down the first column, then the second); above it
 * two rows of eight (numbered along the lower row, then the upper one). The corner above the
 * right column holds the edit button.
 */
public final class InventoryButtonSpots {
	public static final int STEP = InventoryButtonsFeature.SIZE + 2;
	private static final int RIGHT_ROWS = 7;
	private static final int RIGHT_COLUMNS = 2;
	private static final int TOP_COLUMNS = 8;
	private static final int TOP_ROWS = 2;

	private InventoryButtonSpots() {
	}

	public static int count(InventoryButtonPosition side) {
		return side == InventoryButtonPosition.TOP ? TOP_COLUMNS * TOP_ROWS : RIGHT_COLUMNS * RIGHT_ROWS;
	}

	/** Offset of a spot from the inventory's top-left corner. */
	public static int x(InventoryButtonPosition side, int slot, int imageWidth) {
		if (side == InventoryButtonPosition.TOP) {
			return (slot % TOP_COLUMNS) * STEP;
		}
		return imageWidth + 2 + (slot / RIGHT_ROWS) * STEP;
	}

	public static int y(InventoryButtonPosition side, int slot) {
		if (side == InventoryButtonPosition.TOP) {
			return -STEP - (slot / TOP_COLUMNS) * STEP;
		}
		return (slot % RIGHT_ROWS) * STEP;
	}

	public static int firstFree(List<InventoryButton> buttons, InventoryButtonPosition side) {
		Set<Integer> used = new HashSet<>();
		for (InventoryButton button : buttons) {
			if (button.position == side) {
				used.add(button.slot);
			}
		}
		for (int slot = 0; slot < count(side); slot++) {
			if (!used.contains(slot)) {
				return slot;
			}
		}
		return -1;
	}

	/** Buttons of one side, ordered by spot. */
	public static List<InventoryButton> onSide(List<InventoryButton> buttons, InventoryButtonPosition side) {
		List<InventoryButton> result = new ArrayList<>();
		for (InventoryButton button : buttons) {
			if (button.position == side) {
				result.add(button);
			}
		}
		result.sort(Comparator.comparingInt(button -> button.slot));
		return result;
	}

	public static InventoryButton at(List<InventoryButton> buttons, InventoryButtonPosition side, int slot) {
		for (InventoryButton button : buttons) {
			if (button.position == side && button.slot == slot) {
				return button;
			}
		}
		return null;
	}

	/**
	 * Gives every button a valid spot of its own: buttons from older configs, out-of-range spots and
	 * duplicates get the first free spot on their side, or on the other side when theirs is full.
	 */
	public static void normalize(List<InventoryButton> buttons) {
		Set<String> taken = new HashSet<>();
		List<InventoryButton> unplaced = new ArrayList<>();
		for (InventoryButton button : buttons) {
			if (button.position == null) {
				button.position = InventoryButtonPosition.RIGHT;
			}
			boolean valid = button.slot >= 0 && button.slot < count(button.position);
			if (valid && taken.add(button.position + ":" + button.slot)) {
				continue;
			}
			button.slot = -1;
			unplaced.add(button);
		}
		for (InventoryButton button : unplaced) {
			int slot = firstFree(buttons, button.position);
			if (slot < 0) {
				button.position = button.position == InventoryButtonPosition.TOP
					? InventoryButtonPosition.RIGHT : InventoryButtonPosition.TOP;
				slot = firstFree(buttons, button.position);
			}
			button.slot = slot;
		}
	}
}
