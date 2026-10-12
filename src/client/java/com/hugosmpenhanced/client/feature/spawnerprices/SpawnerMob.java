package com.hugosmpenhanced.client.feature.spawnerprices;

import java.util.List;
import java.util.Locale;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The spawner kinds hugosmptools.com lists, with the words a HugoSMP spawner item's name or lore
 * may use for them. {@code pig} is listed there as "Zombie Pigman"; voidbot also calls it
 * "Schwein", so both mobs map to it.
 */
public enum SpawnerMob {
	SKELETON("skeleton", "Skelett", Items.SKELETON_SPAWN_EGG, List.of("minecraft:skeleton"), List.of("skelett", "skeleton")),
	CREEPER("creeper", "Creeper", Items.CREEPER_SPAWN_EGG, List.of("minecraft:creeper"), List.of("creeper")),
	IRON_GOLEM("iron_golem", "Eisengolem", Items.IRON_GOLEM_SPAWN_EGG, List.of("minecraft:iron_golem"),
		List.of("eisengolem", "eisen-golem", "eisen golem", "iron golem", "iron_golem", "irongolem")),
	SPIDER("spider", "Spinne", Items.SPIDER_SPAWN_EGG, List.of("minecraft:spider"), List.of("spinnen", "spinne", "spider")),
	BLAZE("blaze", "Lohe", Items.BLAZE_SPAWN_EGG, List.of("minecraft:blaze"), List.of("lohe", "blaze")),
	PIG("pig", "Zombie Pigman", Items.ZOMBIFIED_PIGLIN_SPAWN_EGG, List.of("minecraft:zombified_piglin", "minecraft:pig"),
		List.of("zombie pigman", "zombiepigman", "pigman", "zombified piglin", "zombifizierter piglin", "schweinezombie", "schwein", "pig")),
	COW("cow", "Kuh", Items.COW_SPAWN_EGG, List.of("minecraft:cow"), List.of("kuh", "cow"));

	/** The id hugosmptools.com uses. */
	public final String id;
	public final String label;
	/** Shown in the overview next to the name. */
	public final Item icon;
	final List<String> entityIds;
	/** Lower case, checked as whole words in that order: longer ones first where they overlap. */
	final List<String> words;

	SpawnerMob(String id, String label, Item icon, List<String> entityIds, List<String> words) {
		this.id = id;
		this.label = label;
		this.icon = icon;
		this.entityIds = entityIds;
		this.words = words;
	}

	public static SpawnerMob byId(String id) {
		for (SpawnerMob mob : values()) {
			if (mob.id.equals(id)) {
				return mob;
			}
		}
		return null;
	}

	public static SpawnerMob byEntityId(String entityId) {
		if (entityId == null) {
			return null;
		}
		String id = entityId.toLowerCase(Locale.ROOT);
		if (!id.contains(":")) {
			id = "minecraft:" + id;
		}
		for (SpawnerMob mob : values()) {
			if (mob.entityIds.contains(id)) {
				return mob;
			}
		}
		return null;
	}

	/**
	 * The mob a text such as "Skelett-Spawner" or "Spawner: Iron Golem" names, or null. Cave
	 * spiders and mooshrooms are their own spawners and must not count as spider or cow.
	 */
	public static SpawnerMob inText(String text) {
		String lower = " " + text.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ') + " ";
		if (lower.contains("höhlenspinne") || lower.contains("cave spider") || lower.contains("mooshroom") || lower.contains("pilzkuh")) {
			return null;
		}
		SpawnerMob found = null;
		int foundAt = Integer.MAX_VALUE;
		for (SpawnerMob mob : values()) {
			for (String word : mob.words) {
				int at = wordAt(lower, word.replace('-', ' ').replace('_', ' '));
				// The earliest mention wins, so "Skelett-Spawner (wie Kuh)" stays a skeleton.
				if (at >= 0 && at < foundAt) {
					found = mob;
					foundAt = at;
				}
			}
		}
		return found;
	}

	private static int wordAt(String text, String word) {
		int from = 0;
		while (true) {
			int at = text.indexOf(word, from);
			if (at < 0) {
				return -1;
			}
			boolean startOk = !Character.isLetter(text.charAt(at - 1));
			int end = at + word.length();
			// German compounds: "Skelettspawner", "Kuhspawner".
			boolean endOk = end >= text.length() || !Character.isLetter(text.charAt(end)) || text.startsWith("spawner", end);
			if (startOk && endOk) {
				return at;
			}
			from = at + 1;
		}
	}
}
