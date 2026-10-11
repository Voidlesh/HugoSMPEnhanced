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

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Remembers the auction prices seen in item tooltips (per single item), so the auction search can
 * show what an item went for recently. Kept in its own file next to the config.
 */
public final class AuctionPriceMemory {
	/** A lowest price older than this is replaced by the next price seen. */
	private static final long MIN_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000;
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced-prices.json");
	private static final Gson GSON = new Gson();
	private static final Type TYPE = new TypeToken<HashMap<String, Seen>>() { }.getType();

	private static Map<String, Seen> prices;
	private static boolean dirty;

	public static final class Seen {
		public double min;
		public long minAt;
		public double last;
		public long lastAt;
	}

	private AuctionPriceMemory() {
	}

	/** Key for a stack: the item id, or the enchantment id for a book with exactly one enchantment. Null for renamed items. */
	public static String keyOf(ItemStack stack) {
		if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_NAME)) {
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

	public static void record(ItemStack stack, double perItem) {
		String key = keyOf(stack);
		if (key == null || perItem <= 0) {
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
		dirty = true;
	}

	public static Seen get(String key) {
		return key == null ? null : all().get(key);
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
