package com.hugosmpenhanced.client.feature.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.hud.HudText;

/**
 * The second chat: the lines of HugoSMP that are not player talk (private messages, server
 * messages, friends, clan), collected in one place. This is the data; {@link SideChatHud} draws it.
 */
public final class SideChat {
	public enum Channel {
		PRIVATE("Privat", 0xFF7CC4FF),
		SERVER("Server", HudText.GOLD),
		FRIENDS("Freunde", HudText.GREEN),
		CLAN("Clan", HudText.ORANGE);

		public final String label;
		public final int color;

		Channel(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	public record Entry(Channel channel, long time, String text) {
	}

	private static final int KEEP = 80;
	private static final long DUPLICATE_MS = 800;
	private static final long HIDE_WINDOW_MS = 2_500;
	private static final List<Entry> ENTRIES = new ArrayList<>();
	/** Chat lines the main chat should not show, by their text; they expire quickly. */
	private static final Map<String, Long> HIDE = new HashMap<>();

	private SideChat() {
	}

	public static boolean wanted(Channel channel) {
		ModConfig config = ModConfigManager.CONFIG;
		return config.sideChatEnabled && switch (channel) {
			case PRIVATE -> config.sideChatPrivate;
			case SERVER -> config.sideChatServer;
			case FRIENDS -> config.sideChatFriends;
			case CLAN -> config.sideChatClan;
		};
	}

	/** Adds a line; {@code rawLine} is the text as the main chat shows it (for hiding it there). */
	public static synchronized void add(Channel channel, String text, String rawLine) {
		if (!wanted(channel) || text == null || text.isBlank()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!ENTRIES.isEmpty()) {
			Entry last = ENTRIES.get(ENTRIES.size() - 1);
			//? if modern
			if (last.channel() == channel && last.text().equals(text) && now - last.time() < DUPLICATE_MS) {
			//? else
			if (last.channel() == channel && last.text().equals(text) && now - last.time() < DUPLICATE_MS) {
			//? end
				return;
			}
		}
		ENTRIES.add(new Entry(channel, now, text.strip()));
		while (ENTRIES.size() > KEEP) {
			ENTRIES.remove(0);
		}
		if (rawLine != null && !rawLine.isBlank() && com.hugosmpenhanced.client.config.ModConfigManager.CONFIG.sideChatHideFromMain) {
			HIDE.put(rawLine, now);
		}
	}

	/** The newest entries, oldest first; with {@code all} also those past the fade time. */
	public static synchronized List<Entry> recent(int max, boolean all) {
		long fade = ModConfigManager.CONFIG.sideChatFade * 1000L;
		long now = System.currentTimeMillis();
		List<Entry> result = new ArrayList<>();
		for (int i = ENTRIES.size() - 1; i >= 0 && result.size() < max; i--) {
			Entry entry = ENTRIES.get(i);
			if (!all && fade > 0 && now - entry.time() > fade) {
				break;
			}
			result.add(0, entry);
		}
		return result;
	}

	public static synchronized boolean hasAny() {
		return !ENTRIES.isEmpty();
	}

	/** Whether the main chat should swallow this line because the second chat shows it now. */
	public static synchronized boolean hideFromMain(String line) {
		if (!ModConfigManager.CONFIG.sideChatHideFromMain || !ModConfigManager.CONFIG.sideChatEnabled || HIDE.isEmpty()) {
			return false;
		}
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<String, Long>> it = HIDE.entrySet().iterator();
		boolean hide = false;
		String clean = line == null ? "" : com.hugosmpenhanced.client.formats.HugoFormats.clean(line);
		while (it.hasNext()) {
			Map.Entry<String, Long> entry = it.next();
			if (now - entry.getValue() > HIDE_WINDOW_MS) {
				it.remove();
			} else if (entry.getKey().equals(clean)) {
				hide = true;
			}
		}
		return hide;
	}

	public static synchronized void clear() {
		ENTRIES.clear();
		HIDE.clear();
	}
}
