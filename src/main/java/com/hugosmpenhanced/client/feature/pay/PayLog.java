package com.hugosmpenhanced.client.feature.pay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import com.google.gson.reflect.TypeToken;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.streamer.ChatPatterns;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.util.JsonStore;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.network.chat.Component;

/**
 * Every payment in and out on HugoSMP, kept across restarts. Incoming payments come from the
 * server's chat line; outgoing ones from the typed {@code /pay}, confirmed by the server's
 * "Du hast ... gezahlt" line. A {@code /pay} the server answers with an error is dropped; one
 * without any answer stays in the log, marked as unconfirmed.
 */
public final class PayLog {
	private static final int KEEP = 1000;
	private static final long CONFIRM_MS = 8_000;
	private static final String[] ERRORS = {"nicht genug", "not enough", "zu wenig", "insufficient", "nicht online", "not online",
		"nicht gefunden", "not found", "existiert nicht", "does not exist", "kein spieler", "no player", "unbekannt", "unknown",
		"nicht dir selbst", "yourself", "dir selbst", "ungültig", "invalid", "fehler", "error", "kannst nicht", "cannot", "can't",
		"mindestens", "minimum", "nicht möglich"};

	/** One payment. {@code confirmed} is false for a sent /pay the server never confirmed. */
	public record Entry(long time, boolean incoming, String name, long amount, boolean confirmed) {
	}

	private record Pending(String name, long amount, long at) {
	}

	private static final JsonStore<List<Entry>> STORE = new JsonStore<>("zahlungen.json", new TypeToken<ArrayList<Entry>>() { }.getType(), ArrayList::new);
	private static final List<Consumer<Entry>> LISTENERS = new ArrayList<>();
	private static List<Entry> entries;
	private static final List<Pending> PENDING = new ArrayList<>();

	private PayLog() {
	}

	public static void register() {
		ClientSendMessageEvents.COMMAND.register(PayLog::onCommand);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> expirePending(false));
	}

	/** Called for each new entry, e.g. by the money tracker. */
	public static void listen(Consumer<Entry> listener) {
		LISTENERS.add(listener);
	}

	private static boolean active() {
		return ModConfigManager.CONFIG.payLogEnabled && ServerCheck.isHugoSmp();
	}

	public static synchronized List<Entry> entries() {
		if (entries == null) {
			entries = new ArrayList<>(STORE.load());
			entries.removeIf(entry -> entry == null || entry.name() == null);
		}
		return entries;
	}

	// ------------------------------------------------------------------ input

	public static void onCommand(String command) {
		if (!active()) {
			return;
		}
		PayCommand pay = PayCommand.parseSent(command);
		if (pay == null || pay.amount() <= 0) {
			return;
		}
		expirePending(false);
		PENDING.add(new Pending(pay.name(), pay.amount(), System.currentTimeMillis()));
	}

	/** Reads one system chat line; public for game tests. */
	public static void onMessage(Component message) {
		if (!active()) {
			return;
		}
		String line = HugoFormats.clean(message.getString());
		ChatPatterns.Payment received = ChatPatterns.parsePayment(line);
		if (received != null) {
			add(new Entry(System.currentTimeMillis(), true, received.name(), received.amount(), true));
			return;
		}
		for (var pattern : HugoFormats.patterns("pay.sent")) {
			Matcher matcher = pattern.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String name = HugoFormats.group(matcher, "name");
			long amount = Money.parse(HugoFormats.group(matcher, "amount"));
			if (name == null || amount <= 0) {
				continue;
			}
			confirmSent(name, amount);
			return;
		}
		if (!PENDING.isEmpty() && System.currentTimeMillis() - PENDING.get(PENDING.size() - 1).at() < 3_000) {
			String lower = line.toLowerCase(Locale.ROOT);
			for (String error : ERRORS) {
				if (lower.contains(error)) {
					// The server refused the last /pay.
					PENDING.remove(PENDING.size() - 1);
					return;
				}
			}
		}
	}

	private static void confirmSent(String name, long amount) {
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			if (pending.name().equalsIgnoreCase(name)) {
				it.remove();
				name = pending.name().length() == name.length() ? name : pending.name();
				break;
			}
		}
		add(new Entry(System.currentTimeMillis(), false, name, amount, true));
	}

	/** Pending payments the server never answered go into the log unconfirmed. */
	static void expirePending(boolean all) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			if (all || now - pending.at() > CONFIRM_MS) {
				it.remove();
				add(new Entry(pending.at(), false, pending.name(), pending.amount(), false));
			}
		}
	}

	private static synchronized void add(Entry entry) {
		List<Entry> list = entries();
		list.add(entry);
		while (list.size() > KEEP) {
			list.remove(0);
		}
		STORE.save(new ArrayList<>(list));
		for (Consumer<Entry> listener : LISTENERS) {
			listener.accept(entry);
		}
	}

	public static synchronized void clear() {
		entries().clear();
		STORE.save(new ArrayList<>());
	}

	// ------------------------------------------------------------------ questions

	/** The newest entry with this player, or null. */
	public static synchronized Entry last(String name) {
		List<Entry> list = entries();
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).name().equalsIgnoreCase(name)) {
				return list.get(i);
			}
		}
		return null;
	}

	/** {received, sent} with this player over the whole log. */
	public static synchronized long[] totals(String name) {
		long[] sums = new long[2];
		for (Entry entry : entries()) {
			if (entry.name().equalsIgnoreCase(name) && entry.confirmed()) {
				sums[entry.incoming() ? 0 : 1] += entry.amount();
			}
		}
		return sums;
	}

	/** "gerade eben", "vor 5 Min.", "vor 3 Std.", "vor 2 Tagen". */
	public static String ago(long time) {
		long seconds = Math.max(0, (System.currentTimeMillis() - time) / 1000);
		if (seconds < 60) {
			return "gerade eben";
		}
		if (seconds < 3600) {
			return "vor " + seconds / 60 + " Min.";
		}
		if (seconds < 86_400) {
			return "vor " + seconds / 3600 + " Std.";
		}
		long days = seconds / 86_400;
		return days == 1 ? "gestern" : "vor " + days + " Tagen";
	}
}
