package com.hugosmpenhanced.client.feature.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * Replaces HugoSMP's search sign ("Suchbegriff (min. 3 Zeichen)") for the auction house and for
 * orders with {@link AuctionSearchScreen}, and knows the item and enchantment names it suggests.
 */
public final class AuctionSearch {
	public static final int MIN_LENGTH = 3;
	private static final int HISTORY_SIZE = 8;
	private static final Set<String> HIDDEN_ITEMS = Set.of("air", "barrier", "light", "debug_stick", "knowledge_book",
		"jigsaw", "structure_block", "structure_void", "test_block", "test_instance_block");

	/** Lets tests open the search outside HugoSMP. */
	public static boolean forceActive;
	/** Set while the sign is opened on purpose ("Schild benutzen"), so it is not replaced again. */
	private static boolean openingVanilla;

	private static List<Entry> index;
	private static String indexLanguage;
	private static ClientLanguage english;

	/** One thing that can be searched for: an item or an enchantment. */
	public record Entry(String gameName, String englishName, ItemStack icon, String priceKey, boolean enchantment,
		String gameFolded, String englishFolded) {
		public String sendName() {
			return ModConfigManager.CONFIG.auctionSearchLanguage == AuctionSearchLanguage.ENGLISH ? this.englishName : this.gameName;
		}
	}

	/** A search result: the entry, how well it matched, and where the query sits in the game name (-1 if not there). */
	public record Match(Entry entry, int score, int highlight) {
	}

	private AuctionSearch() {
	}

	public static void register() {
		AuctionMarket.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AuctionPriceMemory.saveIfDirty();
			// Enchantments come from the server's registries.
			index = null;
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AuctionPriceMemory.saveIfDirty());
	}

	/** Called when the server opens a sign editor; returns true if the search screen was opened instead. */
	public static boolean tryOpen(SignBlockEntity sign, boolean front) {
		if (openingVanilla || !ModConfigManager.CONFIG.auctionSearchEnabled || !(forceActive || ServerCheck.isHugoSmp())) {
			return false;
		}
		if (!isSearchSign(sign, front)) {
			return false;
		}
		Minecraft.getInstance().setScreen(new AuctionSearchScreen(sign, front, AuctionMarket.recent()));
		return true;
	}

	private static boolean isSearchSign(SignBlockEntity sign, boolean front) {
		for (int i = 0; i < 4; i++) {
			if (sign.getText(front).getMessage(i, false).getString().toLowerCase(Locale.ROOT).contains("suchbegriff")) {
				return true;
			}
		}
		return false;
	}

	/** Sends the search term on line 1 and keeps the sign's other lines as the server wrote them. */
	public static void send(SignBlockEntity sign, boolean front, String query) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return;
		}
		String[] lines = new String[4];
		lines[0] = query;
		for (int i = 1; i < 4; i++) {
			lines[i] = sign.getText(front).getMessage(i, false).getString();
		}
		client.getConnection().send(new ServerboundSignUpdatePacket(sign.getBlockPos(), front, lines[0], lines[1], lines[2], lines[3]));
	}

	public static void openVanilla(SignBlockEntity sign, boolean front) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		openingVanilla = true;
		try {
			client.player.openTextEdit(sign, front);
		} finally {
			openingVanilla = false;
		}
	}

	public static void remember(String query) {
		List<String> history = ModConfigManager.CONFIG.auctionSearchHistory;
		history.removeIf(entry -> entry.equalsIgnoreCase(query));
		history.add(0, query);
		while (history.size() > HISTORY_SIZE) {
			history.remove(history.size() - 1);
		}
		ModConfigManager.save();
	}

	public static boolean isFavorite(String term) {
		return ModConfigManager.CONFIG.auctionSearchFavorites.stream().anyMatch(entry -> entry.equalsIgnoreCase(term));
	}

	public static void toggleFavorite(String term) {
		List<String> favorites = ModConfigManager.CONFIG.auctionSearchFavorites;
		if (!favorites.removeIf(entry -> entry.equalsIgnoreCase(term))) {
			favorites.add(term);
		}
		ModConfigManager.save();
	}

	public static List<Match> search(String query, int limit) {
		String folded = fold(query.strip());
		List<Match> matches = new ArrayList<>();
		if (folded.isEmpty()) {
			return matches;
		}
		for (Entry entry : index()) {
			int game = score(entry.gameFolded(), folded);
			int eng = score(entry.englishFolded(), folded);
			if (game < 0 && eng < 0) {
				continue;
			}
			int score = game >= 0 && (eng < 0 || game <= eng) ? game * 2 : eng * 2 + 1;
			// Enchantments sort after items that match as well, so "Schutz" still lists the item first.
			matches.add(new Match(entry, score * 2 + (entry.enchantment() ? 1 : 0), entry.gameFolded().indexOf(folded)));
		}
		matches.sort(Comparator.comparingInt(Match::score)
			.thenComparingInt(match -> match.entry().gameName().length())
			.thenComparing(match -> match.entry().gameName()));
		return matches.size() > limit ? matches.subList(0, limit) : matches;
	}

	/** The entry whose name is exactly {@code term}, or null. */
	public static Entry exact(String term) {
		String folded = fold(term.strip());
		for (Entry entry : index()) {
			if (entry.gameFolded().equals(folded) || entry.englishFolded().equals(folded)) {
				return entry;
			}
		}
		return null;
	}

	/** 0 exact, 1 starts with, 2 a word starts with, 3 contains, -1 no match. */
	private static int score(String name, String query) {
		if (name.equals(query)) {
			return 0;
		}
		if (name.startsWith(query)) {
			return 1;
		}
		int at = name.indexOf(query);
		if (at < 0) {
			return -1;
		}
		while (at > 0) {
			char before = name.charAt(at - 1);
			if (before == ' ' || before == '-' || before == '(') {
				return 2;
			}
			at = name.indexOf(query, at + 1);
			if (at < 0) {
				break;
			}
		}
		return 3;
	}

	/** Lower case with umlauts folded, keeping the length so match positions stay valid. */
	static String fold(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
			out.append(switch (c) {
				case 'ä' -> 'a';
				case 'ö' -> 'o';
				case 'ü' -> 'u';
				default -> c;
			});
		}
		return out.toString();
	}

	private static List<Entry> index() {
		Minecraft client = Minecraft.getInstance();
		String language = client.getLanguageManager().getSelected();
		if (index != null && language.equals(indexLanguage)) {
			return index;
		}
		if (english == null) {
			english = ClientLanguage.loadFrom(client.getResourceManager(), List.of("en_us"), false);
		}
		List<Entry> entries = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			if (HIDDEN_ITEMS.contains(path) || path.contains("command_block")
				|| client.level != null && !item.isEnabled(client.level.enabledFeatures())) {
				continue;
			}
			ItemStack stack = new ItemStack(item);
			String game = stack.getHoverName().getString();
			String eng = english.getOrDefault(item.getDescriptionId(), game);
			entries.add(new Entry(game, eng, stack, BuiltInRegistries.ITEM.getKey(item).toString(), false, fold(game), fold(eng)));
		}
		if (client.level != null) {
			client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder -> {
				String game = holder.value().description().getString();
				String eng = holder.value().description().getContents() instanceof TranslatableContents translatable
					? english.getOrDefault(translatable.getKey(), game)
					: game;
				entries.add(new Entry(game, eng, new ItemStack(Items.ENCHANTED_BOOK), "enchantment:" + holder.key().identifier(), true,
					fold(game), fold(eng)));
			});
		}
		index = entries;
		indexLanguage = language;
		return index;
	}
}
