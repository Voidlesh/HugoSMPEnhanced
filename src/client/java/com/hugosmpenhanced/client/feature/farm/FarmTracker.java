package com.hugosmpenhanced.client.feature.farm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.sell.SellPlan;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Counts what you pick up while farming, from the server's pickup packets, so moving items out of
 * chests never counts. Gives items and value per hour (prices learned by the AFK farm's sales)
 * and warns when the inventory is full or the tool in hand is about to break. Time only runs while
 * items come in; after two quiet minutes the session pauses on its own.
 */
public final class FarmTracker {
	private static final long IDLE_MS = 2 * 60_000;

	/** One kind of item picked up this session. */
	public static final class Drop {
		public final String id;
		public final ItemStack example;
		public long count;

		Drop(String id, ItemStack example) {
			this.id = id;
			this.example = example;
		}

		/** Value per item from the AFK farm's sales, or -1. */
		public double price() {
			Double price = ModConfigManager.CONFIG.sellPrices.get(this.id);
			return price == null ? -1 : price;
		}
	}

	private static final Map<String, Drop> DROPS = new LinkedHashMap<>();
	private static long activeMs;
	private static long lastPickupAt;
	private static long lastTickAt;
	private static boolean warnedFull;
	private static ItemStack warnedTool = ItemStack.EMPTY;

	private FarmTracker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FarmTracker::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> lastPickupAt = 0);
	}

	/** From the pickup packet; the item entity is still in the level at that point. */
	public static void onPickup(int itemEntityId, int collectorId, int amount) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (!ModConfigManager.CONFIG.farmTrackerEnabled || player == null || client.level == null || collectorId != player.getId()) {
			return;
		}
		Entity entity = client.level.getEntity(itemEntityId);
		if (!(entity instanceof ItemEntity item) || item.getItem().isEmpty()) {
			return;
		}
		add(item.getItem(), amount > 0 ? amount : item.getItem().getCount());
	}

	/** Counts a pickup; public for game tests. */
	public static void add(ItemStack stack, int amount) {
		long now = System.currentTimeMillis();
		if (lastPickupAt == 0 || now - lastPickupAt > IDLE_MS) {
			lastTickAt = now;
		}
		lastPickupAt = now;
		String id = SellPlan.itemId(stack);
		DROPS.computeIfAbsent(id, key -> new Drop(key, stack.copyWithCount(1))).count += amount;
	}

	private static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (running(now)) {
			activeMs += now - lastTickAt;
		}
		lastTickAt = now;
		LocalPlayer player = client.player;
		if (player == null || !ModConfigManager.CONFIG.farmTrackerEnabled || !running(now)) {
			return;
		}
		if (ModConfigManager.CONFIG.farmWarnFull) {
			boolean full = player.getInventory().getFreeSlot() < 0;
			if (full && !warnedFull) {
				warnedFull = true;
				warn("Inventar voll", "Neue Drops bleiben liegen");
			} else if (!full) {
				warnedFull = false;
			}
		}
		if (ModConfigManager.CONFIG.farmWarnDurability) {
			ItemStack tool = player.getMainHandItem();
			if (tool.isDamageableItem() && tool.getMaxDamage() > 0) {
				int left = tool.getMaxDamage() - tool.getDamageValue();
				boolean low = left * 100 <= tool.getMaxDamage() * ModConfigManager.CONFIG.farmDurabilityPercent;
				if (low && tool != warnedTool) {
					warnedTool = tool;
					warn("Werkzeug fast kaputt", tool.getHoverName().getString() + " · noch " + left + " Haltbarkeit");
				}
			}
		}
	}

	private static void warn(String title, String text) {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.0f));
		TpaToast.show(Component.literal(title), Component.literal(text), new ItemStack(Items.HOPPER), 7_000, TpaToast.WARNING_COLOR);
	}

	private static boolean running(long now) {
		return lastPickupAt > 0 && now - lastPickupAt <= IDLE_MS;
	}

	// ------------------------------------------------------------------ questions

	public static boolean running() {
		return running(System.currentTimeMillis());
	}

	public static boolean hasData() {
		return !DROPS.isEmpty();
	}

	/** Most picked up first. */
	public static List<Drop> drops() {
		List<Drop> list = new ArrayList<>(DROPS.values());
		list.sort(Comparator.comparingLong((Drop d) -> -d.count));
		return list;
	}

	public static long activeMillis() {
		return activeMs;
	}

	public static double perHour(long count) {
		return activeMs < 30_000 ? -1 : count * 3_600_000.0 / activeMs;
	}

	/** Total value of everything with a known price, or -1 when none has one. */
	public static double value() {
		double sum = 0;
		boolean any = false;
		for (Drop drop : DROPS.values()) {
			double price = drop.price();
			if (price > 0) {
				sum += price * drop.count;
				any = true;
			}
		}
		return any ? sum : -1;
	}

	public static long totalItems() {
		long sum = 0;
		for (Drop drop : DROPS.values()) {
			sum += drop.count;
		}
		return sum;
	}

	public static void reset() {
		DROPS.clear();
		activeMs = 0;
		lastPickupAt = 0;
		warnedFull = false;
		warnedTool = ItemStack.EMPTY;
	}

	/** "1:05 h", "12 min". */
	public static String duration(long ms) {
		long minutes = ms / 60_000;
		return minutes < 60 ? minutes + " min" : minutes / 60 + ":" + String.format("%02d", minutes % 60) + " h";
	}
}
