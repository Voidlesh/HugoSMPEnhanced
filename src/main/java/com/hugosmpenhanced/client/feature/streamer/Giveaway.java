package com.hugosmpenhanced.client.feature.streamer;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.world.item.ItemStack;

/**
 * Giveaway: viewers join with a private message in game or with a word in the Twitch chat, each
 * person once. The wheel then picks one with a secure random number; the winner can be taken out
 * for the next spin.
 */
public final class Giveaway {
	public enum Source {
		INGAME, TWITCH
	}

	/** mcName is the Minecraft name (in game always, from Twitch only with "!join Name"). */
	public record Participant(String name, Source source, String mcName, long joinedAt) {
		public String key() {
			return (source == Source.TWITCH ? "tw:" : "mc:") + name.toLowerCase(Locale.ROOT);
		}
	}

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Map<String, Participant> PARTICIPANTS = new LinkedHashMap<>();
	private static boolean open;
	private static ItemStack prize = ItemStack.EMPTY;
	private static String prizeName = "";
	private static Participant winner;
	private static long lastJoinAt;

	private Giveaway() {
	}

	public static boolean open() {
		return open;
	}

	public static void setOpen(boolean value) {
		open = value;
		StreamerFeature.updateTwitchConnection();
	}

	public static void setPrize(ItemStack stack, String name) {
		prize = stack.copy();
		prizeName = name == null ? "" : name.strip();
	}

	public static ItemStack prize() {
		return prize;
	}

	public static String prizeName() {
		if (!prizeName.isEmpty()) {
			return prizeName;
		}
		return prize.isEmpty() ? "" : prize.getHoverName().getString();
	}

	public static List<Participant> participants() {
		return new ArrayList<>(PARTICIPANTS.values());
	}

	public static int count() {
		return PARTICIPANTS.size();
	}

	public static int count(Source source) {
		return (int) PARTICIPANTS.values().stream().filter(p -> p.source() == source).count();
	}

	public static long lastJoinAt() {
		return lastJoinAt;
	}

	/** Adds someone; returns false when closed or already in. */
	public static boolean join(String name, Source source, String mcName) {
		if (!open || name == null || name.isBlank()) {
			return false;
		}
		Participant participant = new Participant(name.strip(), source, mcName, System.currentTimeMillis());
		if (PARTICIPANTS.containsKey(participant.key())) {
			// A Twitch viewer may add their Minecraft name later.
			Participant old = PARTICIPANTS.get(participant.key());
			if (source == Source.TWITCH && mcName != null && old.mcName() == null) {
				PARTICIPANTS.put(old.key(), new Participant(old.name(), old.source(), mcName, old.joinedAt()));
			}
			return false;
		}
		PARTICIPANTS.put(participant.key(), participant);
		lastJoinAt = participant.joinedAt();
		StreamerFeature.onJoin(participant);
		return true;
	}

	public static void remove(Participant participant) {
		PARTICIPANTS.remove(participant.key());
		if (participant.equals(winner)) {
			winner = null;
		}
	}

	public static void clear() {
		PARTICIPANTS.clear();
		winner = null;
	}

	/** Picks the winner for a spin. The wheel only shows the way there. */
	public static Participant draw() {
		List<Participant> list = participants();
		if (list.isEmpty()) {
			return null;
		}
		winner = list.get(RANDOM.nextInt(list.size()));
		return winner;
	}

	public static Participant winner() {
		return winner;
	}

	public static void clearWinner() {
		winner = null;
	}

	/** Twitch chat line: "!join" or "!join MinecraftName". */
	static void onTwitchMessage(String user, String text, boolean subOrVip) {
		var config = ModConfigManager.CONFIG;
		String word = config.streamerTwitchJoinWord.strip();
		if (!open || !config.streamerTwitchJoin || word.isEmpty()) {
			return;
		}
		String[] parts = text.strip().split("\\s+");
		if (parts.length == 0 || !parts[0].equalsIgnoreCase(word)) {
			return;
		}
		if (config.streamerTwitchSubsOnly && !subOrVip) {
			return;
		}
		String mcName = parts.length > 1 && parts[1].matches("[A-Za-z0-9_]{3,16}") ? parts[1] : null;
		join(user, Source.TWITCH, mcName);
	}

	/** Private message in game: the text must be the join word. */
	static boolean onWhisper(String name, String text) {
		var config = ModConfigManager.CONFIG;
		String word = config.streamerIngameJoinWord.strip();
		if (!open || !config.streamerIngameJoin || word.isEmpty()) {
			return false;
		}
		String first = text.strip().split("\\s+")[0];
		if (!first.equalsIgnoreCase(word) && !first.equalsIgnoreCase("!" + word)) {
			return false;
		}
		return join(name, Source.INGAME, name);
	}
}
