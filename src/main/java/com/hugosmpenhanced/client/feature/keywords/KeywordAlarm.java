package com.hugosmpenhanced.client.feature.keywords;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.chatfilter.ChatFilterService;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
//? if modern
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
//? else
import net.minecraft.client.GuiMessageTag;
//? end
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Chat alarm for your own keywords, e.g. "verkaufe elytra": a sound, a toast and the words marked
 * in the line. Every word of an entry must appear in the line, in any order, as the start of a
 * word ("verkauf" also finds "verkaufe"), ignoring case and umlauts.
 */
public final class KeywordAlarm {
	private static final int STRIPE = 0x3CC8F0;
	private static final int WORD = 0xFF55E0FF;
	private static final long SOUND_GAP_MS = 2_500;

	/** Lets game tests run it outside HugoSMP. */
	public static boolean forceActive;

	private record Pending(String text, String keyword, long seenAt) {
	}

	private static Pending pending;
	private static long lastSoundAt;

	private KeywordAlarm() {
	}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null && sender != null && sender.id().equals(client.player.getUUID())) {
				return;
			}
			check(message.getString());
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			if (overlay || ChatFilterService.isOwnMessage(text)) {
				return;
			}
			if (ModConfigManager.CONFIG.keywordAlarmPublicOnly && ChatFilterService.isSystemLine(text)) {
				return;
			}
			check(text);
		});
	}

	public static void check(String raw) {
		if (!ModConfigManager.CONFIG.keywordAlarmEnabled || !(forceActive || ServerCheck.isHugoSmp())) {
			return;
		}
		String text = HugoFormats.clean(raw);
		String keyword = match(text, ModConfigManager.CONFIG.keywordAlarmWords);
		if (keyword == null) {
			return;
		}
		pending = new Pending(raw, keyword, Util.getMillis());
		Minecraft client = Minecraft.getInstance();
		long now = Util.getMillis();
		if (ModConfigManager.CONFIG.keywordAlarmSound && now - lastSoundAt > SOUND_GAP_MS) {
			lastSoundAt = now;
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f));
		}
		if (ModConfigManager.CONFIG.keywordAlarmToast) {
			String shown = text.length() > 60 ? text.substring(0, 59) + "…" : text;
			TpaToast.show(Component.literal("Stichwort: " + keyword), Component.literal(shown), new ItemStack(Items.BELL), 6_000, 0xFF3CC8F0);
		}
	}

	/** The first entry whose words are all in the text, or null. Public for game tests. */
	public static String match(String text, List<String> entries) {
		String folded = fold(text);
		for (String entry : entries) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			boolean all = true;
			for (String word : words(entry)) {
				if (findWord(folded, word) < 0) {
					all = false;
					break;
				}
			}
			if (all) {
				return entry.strip();
			}
		}
		return null;
	}

	private static List<String> words(String entry) {
		List<String> words = new ArrayList<>();
		for (String word : fold(entry).split("\\s+")) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}
		return words;
	}

	/** Where a word starting with {@code prefix} begins in the folded text, or -1. */
	private static int findWord(String folded, String prefix) {
		int at = folded.indexOf(prefix);
		while (at >= 0) {
			if (at == 0 || !Character.isLetterOrDigit(folded.charAt(at - 1))) {
				return at;
			}
			at = folded.indexOf(prefix, at + 1);
		}
		return -1;
	}

	/** Lower case without umlauts, same length as the input. */
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

	// ------------------------------------------------------------------ marking the chat line

	private static boolean isPending(Component message) {
		Pending p = pending;
		//? if modern
		return p != null && Util.getMillis() - p.seenAt() < 1000 && message.getString().endsWith(p.text());
		//? else
		return p != null && Util.getMillis() - p.seenAt() < 1000 && message.getString().endsWith(p.text());
		//? end
	}

	public static GuiMessageTag tag(Component message, GuiMessageTag tag) {
		if (!isPending(message)) {
			return tag;
		}
		return new GuiMessageTag(STRIPE, null, Component.literal("Stichwort: " + pending.keyword()), "Keyword");
	}

	/** The line with the found words marked. */
	public static Component marked(Component message) {
		if (!isPending(message)) {
			return message;
		}
		String plain = message.getString();
		String folded = fold(plain);
		boolean[] mark = new boolean[plain.length()];
		for (String word : words(pending.keyword())) {
			int at = findWord(folded, word);
			while (at >= 0) {
				int end = at;
				while (end < plain.length() && Character.isLetterOrDigit(plain.charAt(end))) {
					end++;
				}
				for (int i = at; i < end; i++) {
					mark[i] = true;
				}
				int next = folded.indexOf(word, end);
				while (next >= 0 && next > 0 && Character.isLetterOrDigit(folded.charAt(next - 1))) {
					next = folded.indexOf(word, next + 1);
				}
				at = next;
			}
		}
		MutableComponent out = Component.empty();
		int[] position = {0};
		message.visit((style, text) -> {
			int start = position[0];
			int i = 0;
			while (i < text.length()) {
				boolean on = start + i < mark.length && mark[start + i];
				int j = i;
				while (j < text.length() && (start + j < mark.length && mark[start + j]) == on) {
					j++;
				}
				Style piece = on ? style.withColor(WORD).withUnderlined(true) : style;
				out.append(Component.literal(text.substring(i, j)).withStyle(piece));
				i = j;
			}
			position[0] += text.length();
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Clears the pending line once it is in the chat. */
	public static void afterAdded(Component message) {
		if (isPending(message)) {
			pending = null;
		}
	}
}
