package com.hugosmpenhanced.client.feature.streamer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfigManager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * A donation auction: viewers send money with /pay, the highest total wins. The money is already
 * paid when a bid arrives, so every payment is written to a log file and losers can be paid back
 * afterwards (only after the streamer confirms the list).
 */
public final class DonationAuction {
	public enum State {
		IDLE, RUNNING, ENDED
	}

	/** One bidder: total paid in this auction, when they last paid and how much. */
	public static final class Bidder {
		public final String name;
		public long total;
		public long lastAmount;
		public long lastAt;
		public long firstReachedAt;
		/** Animation helpers for the overlay: shown amount and row position. */
		public float shownTotal;
		public float shownRow = -1;

		Bidder(String name) {
			this.name = name;
		}
	}

	/** Something the overlay should animate: a new bid, a new leader, more time. */
	public record Event(Kind kind, String name, long amount, long at) {
		public enum Kind {
			BID, LEADER, EXTENDED, TOO_LOW, ENDED
		}
	}

	private static final Path LOG = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced/streamer-zahlungen.log");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static State state = State.IDLE;
	private static ItemStack item = ItemStack.EMPTY;
	private static String title = "";
	private static long startedAt;
	private static long endsAt;
	private static long endedAt;
	private static long startBid;
	private static long minStep;
	private static boolean sumPayments;
	private static boolean extend;
	private static boolean demo;
	private static final Map<String, Bidder> BIDDERS = new LinkedHashMap<>();
	private static String leader;
	private static String winner;
	private static final List<Event> EVENTS = new ArrayList<>();
	private static boolean refunded;
	private static boolean cancelled;

	private DonationAuction() {
	}

	// ------------------------------------------------------------------ lifecycle

	public static void start(ItemStack stack, String name, boolean demoMode) {
		var config = ModConfigManager.CONFIG;
		state = State.RUNNING;
		item = stack.copy();
		title = name == null || name.isBlank() ? stack.getHoverName().getString() : name.strip();
		startedAt = System.currentTimeMillis();
		endsAt = startedAt + (demoMode ? 30 : Math.max(10, config.streamerAuctionSeconds)) * 1000L;
		// The test run has fixed numbers so its made-up bids always fit.
		startBid = demoMode ? 10_000 : Math.max(1, config.streamerStartBid);
		minStep = demoMode ? 5_000 : Math.max(0, config.streamerMinStep);
		sumPayments = config.streamerSumPayments;
		extend = config.streamerExtendLateBids;
		demo = demoMode;
		BIDDERS.clear();
		EVENTS.clear();
		leader = null;
		winner = null;
		refunded = false;
		cancelled = false;
		if (!demo) {
			log("START  " + title + "  Startgebot " + Money.format(startBid) + "  Dauer " + config.streamerAuctionSeconds + " s");
		}
		StreamerFeature.onAuctionStarted();
	}

	/** Ends now: the leader wins. */
	public static void finish() {
		if (state != State.RUNNING) {
			return;
		}
		state = State.ENDED;
		endedAt = System.currentTimeMillis();
		winner = leader;
		EVENTS.add(new Event(Event.Kind.ENDED, winner, winner == null ? 0 : BIDDERS.get(key(winner)).total, endedAt));
		if (!demo) {
			log("ENDE   Gewinner " + (winner == null ? "keiner" : winner + " mit " + Money.format(BIDDERS.get(key(winner)).total)));
		}
		StreamerFeature.onAuctionEnded();
	}

	/**
	 * Stops without a winner. When real money came in, the auction ends without a winner instead of
	 * vanishing, so everyone who paid shows up under "zurückzahlen".
	 */
	public static void cancel() {
		if (state == State.RUNNING && !demo && !BIDDERS.isEmpty()) {
			log("ABBRUCH  ohne Gewinner, " + BIDDERS.size() + " Bieter");
			state = State.ENDED;
			endedAt = System.currentTimeMillis();
			winner = null;
			leader = null;
			cancelled = true;
			EVENTS.add(new Event(Event.Kind.ENDED, null, 0, endedAt));
			StreamerFeature.info(net.minecraft.network.chat.Component.literal(
				"Auktion abgebrochen, niemand gewinnt. Über \"zurückzahlen\" bekommen alle ihr Geld wieder.")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			return;
		}
		if (state == State.RUNNING && !demo) {
			log("ABBRUCH");
		}
		state = State.IDLE;
		item = ItemStack.EMPTY;
		BIDDERS.clear();
		EVENTS.clear();
		leader = null;
		winner = null;
	}

	/** Hides the result. */
	public static void close() {
		state = State.IDLE;
		item = ItemStack.EMPTY;
		BIDDERS.clear();
		EVENTS.clear();
		leader = null;
		winner = null;
	}

	public static void addTime(int seconds) {
		if (state == State.RUNNING) {
			endsAt += seconds * 1000L;
			EVENTS.add(new Event(Event.Kind.EXTENDED, null, seconds, System.currentTimeMillis()));
		}
	}

	public static void tick() {
		if (state == State.RUNNING && System.currentTimeMillis() >= endsAt) {
			finish();
		}
		long now = System.currentTimeMillis();
		EVENTS.removeIf(event -> now - event.at() > 8000 && event.kind() != Event.Kind.ENDED);
	}

	// ------------------------------------------------------------------ bids

	/** A payment arrived. Returns false when no auction is running. */
	public static boolean onPayment(String name, long amount) {
		if (state != State.RUNNING) {
			return false;
		}
		long now = System.currentTimeMillis();
		Bidder bidder = BIDDERS.computeIfAbsent(key(name), k -> new Bidder(name));
		long before = bidder.total;
		bidder.total = sumPayments ? bidder.total + amount : Math.max(bidder.total, amount);
		bidder.lastAmount = amount;
		bidder.lastAt = now;
		if (bidder.total != before) {
			bidder.firstReachedAt = now;
		}
		if (!demo) {
			log("ZAHLUNG " + name + " " + Money.format(amount) + "  (gesamt " + Money.format(bidder.total) + ")");
		}

		String oldLeader = leader;
		if (bidder.total >= nextMinimum() || name.equalsIgnoreCase(leader)) {
			if (bidder.total >= startBid) {
				leader = bidder.name;
			}
		}
		EVENTS.add(new Event(Event.Kind.BID, name, amount, now));
		if (leader != null && !leader.equalsIgnoreCase(oldLeader == null ? "" : oldLeader)) {
			EVENTS.add(new Event(Event.Kind.LEADER, leader, bidder.total, now));
		} else if (!name.equalsIgnoreCase(leader == null ? "" : leader)) {
			EVENTS.add(new Event(Event.Kind.TOO_LOW, name, nextMinimum() - bidder.total, now));
		}
		if (extend && name.equalsIgnoreCase(leader == null ? "" : leader)) {
			long extendMs = Math.max(5, ModConfigManager.CONFIG.streamerExtendSeconds) * 1000L;
			if (endsAt - now < extendMs) {
				endsAt = now + extendMs;
				EVENTS.add(new Event(Event.Kind.EXTENDED, null, extendMs / 1000, now));
			}
		}
		StreamerFeature.onBid(name, amount, leader != null && !leader.equalsIgnoreCase(oldLeader == null ? "" : oldLeader));
		return true;
	}

	/** The lowest total that takes the lead. */
	public static long nextMinimum() {
		if (leader == null) {
			return startBid;
		}
		return BIDDERS.get(key(leader)).total + Math.max(1, minStep);
	}

	/** Leader first, then the others by total (the earlier one first on equal totals). */
	public static List<Bidder> ranking() {
		List<Bidder> list = new ArrayList<>(BIDDERS.values());
		list.sort(Comparator.comparing((Bidder b) -> !b.name.equalsIgnoreCase(leader == null ? "" : leader))
			.thenComparing(Comparator.comparingLong((Bidder b) -> b.total).reversed())
			.thenComparingLong(b -> b.firstReachedAt));
		return list;
	}

	/** Moves the win to the next bidder (when the winner is offline or doesn't answer). */
	public static String nextWinner() {
		if (state != State.ENDED || winner == null) {
			return null;
		}
		List<Bidder> ranking = ranking();
		int index = -1;
		for (int i = 0; i < ranking.size(); i++) {
			if (ranking.get(i).name.equalsIgnoreCase(winner)) {
				index = i;
			}
		}
		for (int i = index + 1; i < ranking.size(); i++) {
			if (ranking.get(i).total >= startBid) {
				winner = ranking.get(i).name;
				leader = winner;
				if (!demo) {
					log("NACHRÜCKER " + winner);
				}
				return winner;
			}
		}
		return null;
	}

	/** Everyone who paid and did not win, with what they paid. */
	public static Map<String, Long> refunds() {
		Map<String, Long> out = new LinkedHashMap<>();
		for (Bidder bidder : ranking()) {
			if (!bidder.name.equalsIgnoreCase(winner == null ? "" : winner) && bidder.total > 0) {
				out.put(bidder.name, bidder.total);
			}
		}
		return out;
	}

	public static void markRefunded() {
		refunded = true;
		if (!demo) {
			log("RÜCKZAHLUNG gestartet: " + refunds());
		}
	}

	// ------------------------------------------------------------------ getters

	public static State state() {
		return state;
	}

	public static boolean demo() {
		return demo;
	}

	public static ItemStack item() {
		return item;
	}

	public static String title() {
		return title;
	}

	public static long startedAt() {
		return startedAt;
	}

	public static long endsAt() {
		return endsAt;
	}

	public static long endedAt() {
		return endedAt;
	}

	public static long durationMs() {
		return Math.max(1, endsAt - startedAt);
	}

	public static long startBid() {
		return startBid;
	}

	public static String leader() {
		return leader;
	}

	public static String winner() {
		return winner;
	}

	public static long total(String name) {
		Bidder bidder = name == null ? null : BIDDERS.get(key(name));
		return bidder == null ? 0 : bidder.total;
	}

	public static long sum() {
		return BIDDERS.values().stream().mapToLong(b -> b.total).sum();
	}

	public static int bidderCount() {
		return BIDDERS.size();
	}

	public static boolean refunded() {
		return refunded;
	}

	/** Ended by "Abbrechen" after money came in: no winner, everyone can be paid back. */
	public static boolean cancelled() {
		return cancelled;
	}

	public static List<Event> events() {
		return EVENTS;
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	static void log(String line) {
		try {
			Files.createDirectories(LOG.getParent());
			Files.writeString(LOG, LocalDateTime.now().format(TIME) + "  " + line + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			HugoSmpEnhancedClient.LOGGER.warn("Could not write the streamer payment log", e);
		}
	}

	public static Path logFile() {
		return LOG;
	}
}
