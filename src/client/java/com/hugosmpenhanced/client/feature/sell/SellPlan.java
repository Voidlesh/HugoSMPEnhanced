package com.hugosmpenhanced.client.feature.sell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hugosmpenhanced.client.config.ModConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * What the sell assistant would do with the inventory right now: for every slot whether it is sold,
 * kept for a rule, protected or left alone, plus the totals the preview shows. Only the main
 * inventory (slots 9-35) is ever sold from; hotbar, offhand and armour count towards a rule's
 * reserve but are never touched.
 */
public final class SellPlan {
	public static final int MAIN_START = 9;
	public static final int MAIN_END = 36;

	public enum Status {
		/** Goes into the sell menu. */
		SELL,
		/** Kept because of a rule's reserve or minimum amount. */
		KEEP,
		/** An item a rule names, but changed (renamed, enchanted ...). */
		PROTECTED,
		/** In a slot locked with Alt + right click. */
		LOCKED,
		/** Nothing to do with selling. */
		NONE
	}

	/** One line of the preview: an example stack, how many, and why (for kept items). */
	public record Line(ItemStack stack, int count, String reason) {
	}

	private final Status[] status = new Status[MAIN_END];
	private final Map<String, Line> sell = new LinkedHashMap<>();
	private final List<Line> kept = new ArrayList<>();
	private int sellStacks;
	private int sellItems;

	private SellPlan() {
		Arrays.fill(this.status, Status.NONE);
	}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static SellPlan compute(Inventory inventory, ModConfig config) {
		SellPlan plan = new SellPlan();
		Map<String, SellRule> rules = new LinkedHashMap<>();
		for (SellRule rule : config.sellRules) {
			if (rule.enabled && rule.item != null && !rule.item.isEmpty()) {
				rules.putIfAbsent(rule.item, rule);
			}
		}
		if (rules.isEmpty()) {
			return plan;
		}
		Map<String, Integer> protectedCounts = new LinkedHashMap<>();
		Map<String, ItemStack> protectedExamples = new LinkedHashMap<>();
		int lockedCount = 0;
		ItemStack lockedExample = ItemStack.EMPTY;
		int hotbarCount = 0;
		ItemStack hotbarExample = ItemStack.EMPTY;

		for (Map.Entry<String, SellRule> entry : rules.entrySet()) {
			String id = entry.getKey();
			SellRule rule = entry.getValue();
			List<Integer> candidates = new ArrayList<>();
			int plainOutside = 0;
			int candidateTotal = 0;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack.isEmpty() || !itemId(stack).equals(id)) {
					continue;
				}
				String reason = SellGuard.protectedReason(stack);
				boolean main = i >= MAIN_START && i < MAIN_END;
				if (reason != null) {
					if (main) {
						plan.status[i] = Status.PROTECTED;
						protectedCounts.merge(reason, stack.getCount(), Integer::sum);
						protectedExamples.putIfAbsent(reason, stack);
					}
					continue;
				}
				if (!main) {
					plainOutside += stack.getCount();
					if (i < MAIN_START) {
						hotbarCount += stack.getCount();
						if (hotbarExample.isEmpty()) {
							hotbarExample = stack;
						}
					}
					continue;
				}
				if (config.sellLockedSlots.contains(i)) {
					plan.status[i] = Status.LOCKED;
					plainOutside += stack.getCount();
					lockedCount += stack.getCount();
					if (lockedExample.isEmpty()) {
						lockedExample = stack;
					}
					continue;
				}
				if (config.sellFullStacksOnly && stack.getCount() < stack.getMaxStackSize()) {
					plan.status[i] = Status.KEEP;
					plainOutside += stack.getCount();
					continue;
				}
				candidates.add(i);
				candidateTotal += stack.getCount();
			}
			if (candidates.isEmpty()) {
				continue;
			}

			// Keep as few items as possible while honouring the reserve: the smallest stack that
			// covers what is still missing, otherwise the biggest one, and repeat.
			int need = Math.max(0, rule.keep - plainOutside);
			List<Integer> pool = new ArrayList<>(candidates);
			int keptForReserve = 0;
			while (need > 0 && !pool.isEmpty()) {
				int missing = need;
				Integer pick = pool.stream()
					.filter(i -> inventory.getItem(i).getCount() >= missing)
					.min(Comparator.comparingInt(i -> inventory.getItem(i).getCount()))
					.orElseGet(() -> pool.stream().max(Comparator.comparingInt(i -> inventory.getItem(i).getCount())).get());
				pool.remove(pick);
				int count = inventory.getItem(pick).getCount();
				plan.status[pick] = Status.KEEP;
				keptForReserve += count;
				need -= count;
			}
			int sellable = candidateTotal - keptForReserve;
			ItemStack example = inventory.getItem(candidates.get(0));
			if (keptForReserve > 0) {
				plan.kept.add(new Line(example, keptForReserve, "Regel: behalten"));
			}
			if (sellable <= 0) {
				continue;
			}
			if (sellable < Math.max(1, rule.minAmount)) {
				for (int i : pool) {
					plan.status[i] = Status.KEEP;
				}
				plan.kept.add(new Line(example, sellable, "erst ab " + rule.minAmount));
				continue;
			}
			for (int i : pool) {
				plan.status[i] = Status.SELL;
				plan.sellStacks++;
			}
			plan.sellItems += sellable;
			plan.sell.put(id, new Line(example, sellable, null));
		}
		protectedCounts.forEach((reason, count) -> plan.kept.add(new Line(protectedExamples.get(reason), count, reason)));
		if (lockedCount > 0) {
			plan.kept.add(new Line(lockedExample, lockedCount, "gesperrter Slot"));
		}
		if (hotbarCount > 0) {
			plan.kept.add(new Line(hotbarExample, hotbarCount, "Hotbar"));
		}
		return plan;
	}

	public Status status(int inventoryIndex) {
		return inventoryIndex >= 0 && inventoryIndex < MAIN_END ? this.status[inventoryIndex] : Status.NONE;
	}

	public boolean sells(int inventoryIndex) {
		return this.status(inventoryIndex) == Status.SELL;
	}

	public boolean isEmpty() {
		return this.sellStacks == 0;
	}

	public int stacks() {
		return this.sellStacks;
	}

	public int items() {
		return this.sellItems;
	}

	public List<Line> sellLines() {
		return new ArrayList<>(this.sell.values());
	}

	public List<Line> keptLines() {
		return this.kept;
	}

	/** Estimated /sell income from learned prices, or -1 when a price is still unknown. */
	public double estimate(ModConfig config) {
		double total = 0;
		for (Map.Entry<String, Line> entry : this.sell.entrySet()) {
			Double price = config.sellPrices.get(entry.getKey());
			if (price == null) {
				return -1;
			}
			total += price * entry.getValue().count();
		}
		return total;
	}

	/** Everything that changes what gets sold; the preview asks again when this changes. */
	public static String rulesKey(ModConfig config) {
		StringBuilder key = new StringBuilder();
		config.sellRules.stream().filter(rule -> rule.enabled).sorted(Comparator.comparing(rule -> rule.item))
			.forEach(rule -> key.append(rule.item).append(':').append(rule.keep).append(':').append(rule.minAmount).append(';'));
		key.append('|').append(config.sellLockedSlots.stream().sorted().toList()).append('|').append(config.sellFullStacksOnly);
		return key.toString();
	}
}
