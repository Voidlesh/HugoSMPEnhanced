package com.hugosmpenhanced.client.feature.spawnerprices;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;

/**
 * Adds VOID Market's Ankauf and Verkauf to the tooltip of every spawner item: in the inventory, in
 * chests, in the auction house. With a stack of several the Ankauf is also given for the whole stack.
 */
public final class SpawnerPricesTooltip {
	private static final long STALE_MS = 60 * 60 * 1000L;

	private SpawnerPricesTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!ModConfigManager.CONFIG.spawnerPricesTooltip || !SpawnerPrices.active()) {
				return;
			}
			SpawnerMob mob = mobOf(stack);
			if (mob != null) {
				lines.addAll(lines(mob, stack.getCount()));
			}
		});
	}

	/**
	 * The mob of a spawner item: from the spawner's own data if the server keeps it, otherwise from
	 * its name or lore ("Skelett-Spawner", "Spawner: Iron Golem"). Null for anything else.
	 */
	public static SpawnerMob mobOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String name = stack.getHoverName().getString();
		boolean spawnerItem = stack.is(Items.SPAWNER) || stack.is(Items.TRIAL_SPAWNER);
		if (!spawnerItem && !name.toLowerCase(Locale.ROOT).contains("spawner")) {
			return null;
		}
		TypedEntityData<?> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (data != null) {
			String entity = data.getUnsafe().getCompound("SpawnData")
				.flatMap(spawnData -> spawnData.getCompound("entity"))
				.flatMap(entityTag -> entityTag.getString("id")).orElse(null);
			SpawnerMob fromData = SpawnerMob.byEntityId(entity);
			if (fromData != null) {
				return fromData;
			}
		}
		SpawnerMob fromName = SpawnerMob.inText(name);
		if (fromName != null) {
			return fromName;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				SpawnerMob fromLore = SpawnerMob.inText(line.getString());
				if (fromLore != null) {
					return fromLore;
				}
			}
		}
		return null;
	}

	/** The tooltip lines for a stack of {@code count} spawners of this mob. */
	public static List<Component> lines(SpawnerMob mob, int count) {
		MutableComponent header = Component.literal(SpawnerPrices.SERVER).withStyle(ChatFormatting.LIGHT_PURPLE);
		SpawnerPrices.Snapshot snapshot = SpawnerPrices.snapshot();
		if (snapshot == null) {
			return List.of(header, Component.literal(SpawnerPrices.state() == SpawnerPrices.State.FAILED
				? " Preise gerade nicht erreichbar" : " Preise werden geladen…").withStyle(ChatFormatting.DARK_GRAY));
		}
		SpawnerPrices.Server server = snapshot.server();
		Long buy = server != null ? server.buy(mob) : null;
		Long sell = server != null ? server.sell(mob) : null;
		List<Component> lines = new ArrayList<>();
		lines.add(header);
		if (buy == null && sell == null) {
			lines.add(Component.literal(" Handelt gerade keine " + mob.label + "-Spawner").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			MutableComponent buyLine = Component.literal(" Ankauf: ").withStyle(ChatFormatting.GRAY);
			if (buy != null) {
				buyLine.append(Component.literal(SpawnerPrices.format(buy)).withStyle(ChatFormatting.GREEN));
				if (count > 1) {
					buyLine.append(Component.literal("  (" + count + "× = " + SpawnerPrices.format(buy * count) + ")")
						.withStyle(ChatFormatting.DARK_GREEN));
				}
			} else {
				buyLine.append(Component.literal("kein Angebot").withStyle(ChatFormatting.DARK_GRAY));
			}
			lines.add(buyLine);
			MutableComponent sellLine = Component.literal(" Verkauf: ").withStyle(ChatFormatting.GRAY);
			sellLine.append(sell != null ? Component.literal(SpawnerPrices.format(sell)).withStyle(ChatFormatting.GOLD)
				: Component.literal("kein Angebot").withStyle(ChatFormatting.DARK_GRAY));
			lines.add(sellLine);
		}
		if (System.currentTimeMillis() - snapshot.fetchedAt() > STALE_MS) {
			lines.add(Component.literal(" Stand " + SpawnerPrices.age(snapshot.fetchedAt())).withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}
}
