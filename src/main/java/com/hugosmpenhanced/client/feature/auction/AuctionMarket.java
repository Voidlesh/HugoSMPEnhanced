package com.hugosmpenhanced.client.feature.auction;

import java.util.Locale;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * HugoSMP opens the same search sign for the auction house and for orders. Which one it is comes
 * from the menu that was open just before, recognised by its title.
 */
public enum AuctionMarket {
	AUCTION("AUKTIONSHAUS-SUCHE", "Auktionshaus-Suche", ""),
	ORDERS("ORDER-SUCHE", "Order-Suche", "order:");

	/** How long after closing a menu its title still decides the market of a sign. */
	private static final long REMEMBER_MS = 60_000;

	private static String lastTitle = "";
	private static long lastSeenAt;

	public final String heading;
	public final String title;
	/** Prefix for this market's keys in the price memory. */
	public final String keyPrefix;

	AuctionMarket(String heading, String title, String keyPrefix) {
		this.heading = heading;
		this.title = title;
		this.keyPrefix = keyPrefix;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> remember(screen));
	}

	private static void remember(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> container) {
			lastTitle = container.getTitle().getString();
			lastSeenAt = System.currentTimeMillis();
		}
	}

	/** The market of the menu open now, for prices in tooltips. */
	public static AuctionMarket current() {
		Screen screen = Minecraft.getInstance().gui.screen();
		return screen instanceof AbstractContainerScreen<?> container ? of(container.getTitle().getString()) : AUCTION;
	}

	/** The market of the menu that was open last, for the search sign. */
	public static AuctionMarket recent() {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen instanceof AbstractContainerScreen<?>) {
			return current();
		}
		return System.currentTimeMillis() - lastSeenAt < REMEMBER_MS ? of(lastTitle) : AUCTION;
	}

	static AuctionMarket of(String title) {
		return title.toLowerCase(Locale.ROOT).contains("order") ? ORDERS : AUCTION;
	}
}
