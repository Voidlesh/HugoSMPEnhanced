package com.hugosmpenhanced.client.feature.money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.reflect.TypeToken;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.discord.StatusReader;
import com.hugosmpenhanced.client.feature.pay.PayCommand;
import com.hugosmpenhanced.client.feature.pay.PayLog;
import com.hugosmpenhanced.client.util.JsonStore;
import com.hugosmpenhanced.client.util.MenuContext;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/**
 * Where the money comes from and goes to. HugoSMP shows the balance in the sidebar only roughly
 * ("Geld 260.90M", so to 10.000), which is enough for the total. Sources come from what is exact
 * (payments, AFK farm sales) and, for the rest, from what was open when the balance moved: a
 * spawner, the orders, the auction house, another menu, or a command typed just before.
 * Whatever has no source lands in "Sonstiges"; changes while offline in "Offline".
 */
public final class MoneyTracker {
	public static final String PAY = "Zahlungen";
	public static final String SELL = "AFK-Farm";
	public static final String OTHER = "Sonstiges";
	public static final String OFFLINE = "Offline";
	private static final long CONTEXT_MS = 4_000;
	private static final long EXPLAIN_MS = 15_000;
	private static final long SAMPLE_MS = 5 * 60_000;
	private static final int KEEP_DAYS = 60;
	private static final int KEEP_SAMPLES = 12 * 24 * 30;

	/** Income and spending per source for one day or the session. */
	public static final class Book {
		public Map<String, Long> income = new LinkedHashMap<>();
		public Map<String, Long> spent = new LinkedHashMap<>();

		void add(String source, long amount) {
			if (amount > 0) {
				this.income.merge(source, amount, Long::sum);
			} else if (amount < 0) {
				this.spent.merge(source, -amount, Long::sum);
			}
		}

		/** Takes back an earlier {@link #add} of the same amount. */
		void remove(String source, long amount) {
			Map<String, Long> map = amount > 0 ? this.income : this.spent;
			long abs = Math.abs(amount);
			map.computeIfPresent(source, (k, v) -> v - abs <= 0 ? null : v - abs);
		}

		public long totalIncome() {
			return this.income.values().stream().mapToLong(Long::longValue).sum();
		}

		public long totalSpent() {
			return this.spent.values().stream().mapToLong(Long::longValue).sum();
		}

		void addAll(Book other) {
			other.income.forEach((k, v) -> this.income.merge(k, v, Long::sum));
			other.spent.forEach((k, v) -> this.spent.merge(k, v, Long::sum));
		}
	}

	/** Saved between restarts. */
	public static final class Data {
		public Map<String, Book> days = new HashMap<>();
		/** {time, balance} every five minutes while on HugoSMP. */
		public List<long[]> samples = new ArrayList<>();
		public long lastBalance = -1;
		public long lastSeen;
	}

	private static final JsonStore<Data> STORE = new JsonStore<>("geld.json", new TypeToken<Data>() { }.getType(), Data::new);
	private static Data data;
	private static Book session = new Book();
	private static long sessionStart = -1;
	private static long sessionStartedAt;
	private static long balance = -1;
	private static long explained;
	private static long explainedAt;
	private static String lastCommand;
	private static long lastCommandAt;
	private static boolean joined;
	private static int ticks;
	private static boolean dirty;
	private static long savedAt;
	/** The last change booked from the sidebar alone, in case the exact event for it comes late. */
	private static String guessSource;
	private static long guessAmount;
	private static long guessAt;

	private MoneyTracker() {
	}

	public static void register() {
		PayLog.listen(entry -> {
			if (entry.confirmed()) {
				exact(PAY, entry.incoming() ? entry.amount() : -entry.amount());
			}
		});
		ClientSendMessageEvents.COMMAND.register(command -> {
			if (PayCommand.parseSent(command) != null) {
				return;
			}
			String word = command.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
			int colon = word.indexOf(':');
			lastCommand = "/" + (colon >= 0 ? word.substring(colon + 1) : word);
			lastCommandAt = System.currentTimeMillis();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joined = true);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			flush(true);
			balance = -1;
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flush(true));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks % 20 == 0) {
				tick(client);
				flush(false);
			}
		});
	}

	public static boolean active() {
		return ModConfigManager.CONFIG.moneyTrackerEnabled && ServerCheck.isHugoSmp();
	}

	static Data data() {
		if (data == null) {
			data = STORE.load();
		}
		return data;
	}

	// ------------------------------------------------------------------ events

	/** Money the mod knows to the dollar (a payment, a sale); the sidebar change it causes is not counted again. */
	public static void exact(String source, long amount) {
		if (!active() || amount == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (guessSource != null && now - guessAt < EXPLAIN_MS && Long.signum(guessAmount) == Long.signum(amount)
			&& Math.abs(guessAmount - amount) <= 2 * tolerance(balance) + 1) {
			// The sidebar was faster than the chat line: swap the guess for the exact amount.
			unbook(guessSource, guessAmount);
			guessSource = null;
		} else {
			explained += amount;
			explainedAt = now;
		}
		book(source, amount);
	}

	private static void tick(Minecraft client) {
		if (!active() || client.player == null) {
			return;
		}
		Double money = StatusReader.last().money();
		if (money == null) {
			return;
		}
		observe(Math.round(money), System.currentTimeMillis());
	}

	/** One balance reading; public for game tests. */
	public static void observe(long now, long time) {
		Data saved = data();
		if (balance < 0) {
			balance = now;
			if (sessionStart < 0) {
				sessionStart = now;
				sessionStartedAt = time;
			}
			if (joined && saved.lastBalance >= 0 && saved.lastBalance != now && Math.abs(now - saved.lastBalance) > tolerance(now)) {
				book(OFFLINE, now - saved.lastBalance);
				sessionStart = now;
			}
			joined = false;
			sample(now, time);
			return;
		}
		if (time - explainedAt > EXPLAIN_MS) {
			explained = 0;
		}
		long delta = now - balance;
		if (delta == 0) {
			sample(now, time);
			return;
		}
		long rest = delta - explained;
		explained = 0;
		if (Math.abs(rest) > tolerance(now)) {
			guessSource = source(time);
			guessAmount = rest;
			guessAt = time;
			book(guessSource, rest);
		}
		balance = now;
		sample(now, time);
	}

	/** How far off the rounded sidebar can be: "260.90M" shows tens of thousands. */
	static long tolerance(long balance) {
		long abs = Math.abs(balance);
		if (abs >= 1_000_000_000L) {
			return 10_000_000L;
		}
		if (abs >= 1_000_000L) {
			return 10_000L;
		}
		return abs >= 1_000L ? 10L : 0L;
	}

	private static String source(long time) {
		MenuContext.Kind kind = MenuContext.kind(CONTEXT_MS);
		if (kind != null) {
			if (kind.label != null) {
				return kind.label;
			}
			String title = MenuContext.title(CONTEXT_MS);
			if (title != null && !title.isBlank()) {
				return title.length() > 24 ? title.substring(0, 23) + "…" : title;
			}
		}
		if (lastCommand != null && time - lastCommandAt < CONTEXT_MS) {
			return lastCommand;
		}
		return OTHER;
	}

	private static void book(String source, long amount) {
		session.add(source, amount);
		Data saved = data();
		saved.days.computeIfAbsent(LocalDate.now().toString(), day -> new Book()).add(source, amount);
		save();
	}

	private static void unbook(String source, long amount) {
		session.remove(source, amount);
		Book day = data().days.get(LocalDate.now().toString());
		if (day != null) {
			day.remove(source, amount);
		}
		save();
	}

	private static void sample(long now, long time) {
		Data saved = data();
		saved.lastBalance = now;
		saved.lastSeen = time;
		List<long[]> samples = saved.samples;
		if (samples.isEmpty() || time - samples.get(samples.size() - 1)[0] >= SAMPLE_MS) {
			samples.add(new long[] {time, now});
			while (samples.size() > KEEP_SAMPLES) {
				samples.remove(0);
			}
			save();
		}
	}

	private static void save() {
		dirty = true;
	}

	/** Writes changes at most every five seconds, or now with {@code force}. */
	private static void flush(boolean force) {
		if (!dirty || !force && System.currentTimeMillis() - savedAt < 5_000) {
			return;
		}
		dirty = false;
		savedAt = System.currentTimeMillis();
		Data saved = data();
		String oldest = LocalDate.now().minusDays(KEEP_DAYS).toString();
		saved.days.keySet().removeIf(day -> day.compareTo(oldest) < 0);
		Data copy = new Data();
		saved.days.forEach((day, book) -> {
			Book b = new Book();
			b.addAll(book);
			copy.days.put(day, b);
		});
		copy.samples = new ArrayList<>(saved.samples);
		copy.lastBalance = saved.lastBalance;
		copy.lastSeen = saved.lastSeen;
		if (force) {
			STORE.saveNow(copy);
		} else {
			STORE.save(copy);
		}
	}

	// ------------------------------------------------------------------ questions

	/** Current balance as the sidebar shows it, or -1. */
	public static long balance() {
		return balance;
	}

	public static Book session() {
		return session;
	}

	/** Change since joining, or 0. */
	public static long sessionChange() {
		return balance < 0 || sessionStart < 0 ? 0 : balance - sessionStart;
	}

	public static long sessionMillis() {
		return sessionStart < 0 ? 0 : System.currentTimeMillis() - sessionStartedAt;
	}

	/** Change per hour this session, or 0 for the first five minutes. */
	public static long perHour() {
		long ms = sessionMillis();
		return ms < 5 * 60_000 ? 0 : Math.round(sessionChange() * 3_600_000.0 / ms);
	}

	/** The books of the last {@code days} days, today included. */
	public static Book days(int days) {
		Book sum = new Book();
		LocalDate today = LocalDate.now();
		for (int i = 0; i < days; i++) {
			Book day = data().days.get(today.minusDays(i).toString());
			if (day != null) {
				sum.addAll(day);
			}
		}
		return sum;
	}

	/** Balance samples since {@code since}, plus the current balance. */
	public static List<long[]> samples(long since) {
		List<long[]> out = new ArrayList<>();
		for (long[] sample : data().samples) {
			if (sample[0] >= since) {
				out.add(sample);
			}
		}
		if (balance >= 0) {
			out.add(new long[] {System.currentTimeMillis(), balance});
		}
		return out;
	}

	public static void resetSession() {
		session = new Book();
		sessionStart = balance;
		sessionStartedAt = System.currentTimeMillis();
	}

	public static void clearHistory() {
		Data saved = data();
		saved.days.clear();
		saved.samples.clear();
		save();
		resetSession();
	}

	/** For game tests: forget the session as if the game had just started. */
	public static void resetForTest() {
		session = new Book();
		sessionStart = -1;
		balance = -1;
		explained = 0;
		joined = false;
		guessSource = null;
	}
}
