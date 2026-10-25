package com.hugosmpenhanced.client.feature.auction;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Remembers the auction and order prices seen in item tooltips (per single item), so the search can
 * show what an item went for recently. Kept in its own file next to the config.
 */
public final class AuctionPriceMemory {
	/** A lowest or highest price older than this is replaced by the next price seen. */
	private static final long MIN_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000;
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced-prices.json");
	private static final Gson GSON = new Gson();
	private static final Type TYPE = new TypeToken<HashMap<String, Seen>>() { }.getType();

	private static Map<String, Seen> prices;
	private static boolean dirty;

	public static final class Seen {
		public double min;
		public long minAt;
		public double max;
		public long maxAt;
		public double last;
		public long lastAt;
	}

	private AuctionPriceMemory() {
	}

	/**
	 * Key for a stack: "custom:" and the id for a HugoSMP item, the item id, or the enchantment id for
	 * a book with exactly one enchantment. Null for renamed items.
	 */
	public static String keyOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String custom = CustomItems.idOf(stack, true);
		if (custom != null) {
			return "custom:" + custom;
		}
		if (stack.has(DataComponents.CUSTOM_NAME)) {
			return null;
		}
		if (stack.is(Items.ENCHANTED_BOOK)) {
			ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
			if (stored == null || stored.size() != 1) {
				return null;
			}
			Holder<Enchantment> enchantment = stored.keySet().iterator().next();
			return enchantment.unwrapKey().map(key -> "enchantment:" + key.identifier()).orElse(null);
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static void record(ItemStack stack, double perItem, AuctionMarket market) {
		String key = keyOf(stack);
		if (key != null) {
			recordKey(market.keyPrefix + key, perItem);
		}
	}

	/** The best price per hour of time left seen for a limited item. */
	public static void recordPerHour(ItemStack stack, double perHour, AuctionMarket market) {
		String key = keyOf(stack);
		if (key != null && ModConfigManager.CONFIG.auctionPriceMemoryEnabled) {
			recordKey("perhour:" + market.keyPrefix + key, perHour);
		}
	}

	public static Seen getPerHour(String key, AuctionMarket market) {
		return key == null ? null : all().get("perhour:" + market.keyPrefix + key);
	}

	private static void recordKey(String key, double perItem) {
		if (perItem <= 0 || !Double.isFinite(perItem)) {
			return;
		}
		long now = System.currentTimeMillis();
		Seen seen = all().computeIfAbsent(key, k -> new Seen());
		if (seen.last == perItem && now - seen.lastAt < 60_000) {
			return;
		}
		seen.last = perItem;
		seen.lastAt = now;
		if (seen.minAt == 0 || perItem < seen.min || now - seen.minAt > MIN_MAX_AGE_MS) {
			seen.min = perItem;
			seen.minAt = now;
		}
		if (seen.maxAt == 0 || perItem > seen.max || now - seen.maxAt > MIN_MAX_AGE_MS) {
			seen.max = perItem;
			seen.maxAt = now;
		}
		dirty = true;
	}

	public static Seen get(String key, AuctionMarket market) {
		return key == null ? null : all().get(market.keyPrefix + key);
	}

	private static Map<String, Seen> all() {
		if (prices == null) {
			prices = new HashMap<>();
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					Map<String, Seen> loaded = GSON.fromJson(reader, TYPE);
					if (loaded != null) {
						prices.putAll(loaded);
					}
				} catch (IOException | RuntimeException ignored) {
					// start empty
				}
			}
		}
		return prices;
	}

	public static void saveIfDirty() {
		if (!dirty || prices == null) {
			return;
		}
		dirty = false;
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(prices, TYPE, writer);
			}
		} catch (IOException ignored) {
			// keep the prices in memory
		}
	}

	public static void clear() {
		all().clear();
		dirty = true;
		saveIfDirty();
	}
}
