package com.hugosmpenhanced.client.feature.messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.reflect.TypeToken;
import com.hugosmpenhanced.client.util.JsonStore;

import net.minecraft.client.Minecraft;

/**
 * Every private conversation, kept across restarts. One {@link Conversation} per player; sent
 * messages come from the typed command, received ones from the server's chat line, and a line the
 * server echoes back for a message we already logged is dropped.
 */
public final class MessageLog {
	private static final int KEEP_PER_CHAT = 500;
	private static final int KEEP_CHATS = 150;
	private static final long ECHO_MS = 6_000;
	private static final long DUPLICATE_MS = 1_200;

	/** One line in a conversation. {@code note} lines are the mod's own remarks ("TPA gesendet"), {@code failed} a message the server refused. */
	public static final class Message {
		public long time;
		public boolean out;
		public String text;
		public boolean failed;
		public boolean note;

		Message(long time, boolean out, String text, boolean note) {
			this.time = time;
			this.out = out;
			this.text = text;
			this.note = note;
		}
	}

	public static final class Conversation {
		public String name;
		public List<Message> messages = new ArrayList<>();
		public int unread;

		Conversation(String name) {
			this.name = name;
		}

		public long lastTime() {
			return this.messages.isEmpty() ? 0 : this.messages.get(this.messages.size() - 1).time;
		}

		/** The newest real message (not a note), or null. */
		public Message last() {
			for (int i = this.messages.size() - 1; i >= 0; i--) {
				if (!this.messages.get(i).note) {
					return this.messages.get(i);
				}
			}
			return null;
		}
	}

	private static final JsonStore<List<Conversation>> STORE =
		new JsonStore<>("nachrichten.json", new TypeToken<ArrayList<Conversation>>() { }.getType(), ArrayList::new);
	private static List<Conversation> chats;
	private static String lastPartner;
	/** Who the newest sent message went to (for a refusal that follows it). */
	private static String lastSentTo;
	/** The conversation the player has open right now; its new messages count as read. */
	private static String viewing;
	private static String lastKey = "";
	private static long lastKeyAt;
	private static final List<String[]> RECENT_OUT = new ArrayList<>();
	private static final List<Long> RECENT_OUT_AT = new ArrayList<>();

	private MessageLog() {
	}

	private static synchronized List<Conversation> chats() {
		if (chats == null) {
			chats = new ArrayList<>(STORE.load());
			chats.removeIf(chat -> chat == null || chat.name == null || chat.messages == null);
			for (Conversation chat : chats) {
				chat.messages.removeIf(message -> message == null || message.text == null);
			}
		}
		return chats;
	}

	// ------------------------------------------------------------------ questions

	/** All conversations, the newest first. */
	public static synchronized List<Conversation> conversations() {
		List<Conversation> sorted = new ArrayList<>(chats());
		sorted.sort(Comparator.comparingLong(Conversation::lastTime).reversed());
		return sorted;
	}

	public static synchronized Conversation find(String name) {
		if (name == null) {
			return null;
		}
		for (Conversation chat : chats()) {
			if (chat.name.equalsIgnoreCase(name)) {
				return chat;
			}
		}
		return null;
	}

	public static synchronized int unreadTotal() {
		int total = 0;
		for (Conversation chat : chats()) {
			total += chat.unread;
		}
		return total;
	}

	public static synchronized String lastPartner() {
		return lastPartner;
	}

	public static synchronized int count() {
		return chats().size();
	}

	// ------------------------------------------------------------------ writing

	private static boolean self(String name) {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.player.getGameProfile().name().equalsIgnoreCase(name);
	}

	private static String normalize(String text) {
		return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static Conversation chat(String name) {
		Conversation chat = find(name);
		if (chat == null) {
			chat = new Conversation(name);
			chats().add(chat);
			if (chats().size() > KEEP_CHATS) {
				// Drop the quietest chat that is not unread.
				Conversation oldest = null;
				for (Conversation other : chats()) {
					if (other != chat && other.unread == 0 && (oldest == null || other.lastTime() < oldest.lastTime())) {
						oldest = other;
					}
				}
				if (oldest != null) {
					chats().remove(oldest);
				}
			}
		}
		return chat;
	}

	private static void append(Conversation chat, Message message) {
		chat.messages.add(message);
		while (chat.messages.size() > KEEP_PER_CHAT) {
			chat.messages.remove(0);
		}
		save();
	}

	/** A message from {@code name}. */
	public static synchronized void incoming(String name, String text) {
		if (name == null || text == null || text.isBlank() || self(name)) {
			return;
		}
		long now = System.currentTimeMillis();
		String key = "in\n" + name.toLowerCase(Locale.ROOT) + "\n" + text;
		if (key.equals(lastKey) && now - lastKeyAt < DUPLICATE_MS) {
			return;
		}
		lastKey = key;
		lastKeyAt = now;
		Conversation chat = chat(name);
		append(chat, new Message(now, false, text.strip(), false));
		lastPartner = chat.name;
		if (!chat.name.equalsIgnoreCase(viewing)) {
			chat.unread++;
		}
	}

	/** A message to {@code name} that the player just sent. */
	public static synchronized void outgoing(String name, String text) {
		if (name == null || text == null || text.isBlank() || self(name)) {
			return;
		}
		Conversation chat = chat(name);
		long now = System.currentTimeMillis();
		append(chat, new Message(now, true, text.strip(), false));
		lastPartner = chat.name;
		lastSentTo = chat.name;
		RECENT_OUT.add(new String[] {chat.name.toLowerCase(Locale.ROOT), normalize(text)});
		RECENT_OUT_AT.add(now);
		while (RECENT_OUT.size() > 8) {
			RECENT_OUT.remove(0);
			RECENT_OUT_AT.remove(0);
		}
	}

	/**
	 * The server's own line for a message we sent. When it matches one we logged nothing is added
	 * twice; otherwise the message is logged now.
	 */
	public static synchronized void echo(String name, String text) {
		if (name == null || text == null || text.isBlank()) {
			return;
		}
		long now = System.currentTimeMillis();
		String lower = name.toLowerCase(Locale.ROOT);
		String normal = normalize(text);
		for (int i = RECENT_OUT.size() - 1; i >= 0; i--) {
			if (now - RECENT_OUT_AT.get(i) > ECHO_MS) {
				continue;
			}
			if (RECENT_OUT.get(i)[0].equals(lower) && RECENT_OUT.get(i)[1].equals(normal)) {
				RECENT_OUT.remove(i);
				RECENT_OUT_AT.remove(i);
				return;
			}
		}
		outgoing(name, text);
		// This one came from the server, so it needs no echo of its own.
		if (!RECENT_OUT.isEmpty()) {
			RECENT_OUT.remove(RECENT_OUT.size() - 1);
			RECENT_OUT_AT.remove(RECENT_OUT_AT.size() - 1);
		}
	}

	/** The server refused the newest message sent within the last few seconds. */
	public static synchronized void markLastFailed() {
		Conversation chat = find(lastSentTo);
		if (chat == null || chat.messages.isEmpty()) {
			return;
		}
		Message last = chat.messages.get(chat.messages.size() - 1);
		if (last.out && !last.note && !last.failed && System.currentTimeMillis() - last.time < 3_000) {
			last.failed = true;
			save();
		}
	}

	/** A remark of the mod inside a conversation, e.g. "TPA-Anfrage gesendet". */
	public static synchronized void note(String name, String text, boolean createIfMissing) {
		if (name == null || self(name)) {
			return;
		}
		Conversation chat = createIfMissing ? chat(name) : find(name);
		if (chat != null) {
			append(chat, new Message(System.currentTimeMillis(), false, text, true));
		}
	}

	public static synchronized void markRead(String name) {
		Conversation chat = find(name);
		if (chat != null && chat.unread != 0) {
			chat.unread = 0;
			save();
		}
	}

	public static synchronized void setViewing(String name) {
		viewing = name;
		if (name != null) {
			markRead(name);
		}
	}

	/** Makes sure a conversation exists (a new chat started from the list). */
	public static synchronized Conversation open(String name) {
		return chat(name);
	}

	public static synchronized void delete(String name) {
		Conversation chat = find(name);
		if (chat != null) {
			chats().remove(chat);
			save();
		}
	}

	public static synchronized void clear() {
		chats().clear();
		lastPartner = null;
		save();
	}

	private static void save() {
		List<Conversation> copy = new ArrayList<>();
		for (Conversation chat : chats()) {
			// Empty chats (opened but never written) are not worth keeping.
			if (!chat.messages.isEmpty()) {
				copy.add(chat);
			}
		}
		STORE.save(copy);
	}

	/** Game tests: start with nothing. */
	public static synchronized void resetForTest() {
		chats = new ArrayList<>();
		lastPartner = null;
		viewing = null;
		lastKey = "";
		RECENT_OUT.clear();
		RECENT_OUT_AT.clear();
	}
}
