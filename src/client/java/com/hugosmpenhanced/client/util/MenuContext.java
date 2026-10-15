package com.hugosmpenhanced.client.util;

import java.util.Locale;

import com.hugosmpenhanced.client.formats.HugoFormats;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Which server menu is open (or was open a moment ago) and what kind it is, from its title and
 * the menu words in the formats file. Lets the money tracker say where money came from.
 */
public final class MenuContext {
	/** What a menu is, by its title. */
	public enum Kind {
		SPAWNER("Spawner"), ORDERS("Aufträge"), AUCTION("Auktionshaus"), SELL("Verkaufen"), OTHER(null);

		public final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	private static String title;
	private static Kind kind;
	private static long closedAt;

	private MenuContext() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?>) || screen instanceof InventoryScreen) {
				return;
			}
			title = HugoFormats.clean(screen.getTitle().getString());
			kind = kindOf(title);
			closedAt = 0;
			ScreenEvents.remove(screen).register(s -> closedAt = System.currentTimeMillis());
		});
	}

	public static Kind kindOf(String menuTitle) {
		if (menuTitle == null) {
			return Kind.OTHER;
		}
		String lower = menuTitle.toLowerCase(Locale.ROOT);
		if (HugoFormats.containsWord("menu.spawner", lower)) {
			return Kind.SPAWNER;
		}
		if (HugoFormats.containsWord("menu.orders", lower)) {
			return Kind.ORDERS;
		}
		if (HugoFormats.containsWord("menu.auction", lower)) {
			return Kind.AUCTION;
		}
		if (HugoFormats.containsWord("menu.sell", lower)) {
			return Kind.SELL;
		}
		return Kind.OTHER;
	}

	/** The title of the open menu, or of one closed less than {@code graceMs} ago; null otherwise. */
	public static String title(long graceMs) {
		if (title == null || closedAt != 0 && System.currentTimeMillis() - closedAt > graceMs) {
			return null;
		}
		return title;
	}

	public static Kind kind(long graceMs) {
		return title(graceMs) == null ? null : kind;
	}

	/** For game tests. */
	public static void set(String menuTitle) {
		title = menuTitle;
		kind = kindOf(menuTitle);
		closedAt = menuTitle == null ? 0 : System.currentTimeMillis();
	}
}
