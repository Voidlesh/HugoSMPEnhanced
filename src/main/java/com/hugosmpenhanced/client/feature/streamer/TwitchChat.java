package com.hugosmpenhanced.client.feature.streamer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads a Twitch channel's chat without an account: the anonymous "justinfan" login of Twitch's
 * IRC-over-WebSocket chat can read every public chat but never write. Reconnects on its own.
 * Plain Java, no Minecraft classes, so it can be tried outside the game.
 */
public final class TwitchChat {
	public static final URI SERVER = URI.create("wss://irc-ws.chat.twitch.tv:443");

	public enum Status {
		OFF, CONNECTING, CONNECTED, FAILED
	}

	/** A chat message: display name, text, and whether the sender is a sub, VIP, mod or the streamer. */
	public record Message(String user, String text, boolean subOrVip) {
	}

	private final Consumer<Message> listener;
	private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "HugoSMP Enhanced Twitch chat");
		thread.setDaemon(true);
		return thread;
	});
	private volatile Status status = Status.OFF;
	private volatile String channel;
	private volatile String error;
	private volatile WebSocket socket;
	private volatile int generation;
	private int failures;
	private int failedGeneration = -1;
	private final StringBuilder partial = new StringBuilder();

	public TwitchChat(Consumer<Message> listener) {
		this.listener = listener;
	}

	public Status status() {
		return this.status;
	}

	public String channel() {
		return this.channel;
	}

	public String error() {
		return this.error;
	}

	/** Connects to the channel, or does nothing when already on it. */
	public synchronized void connect(String name) {
		String wanted = normalize(name);
		if (wanted.isEmpty()) {
			this.disconnect();
			return;
		}
		if (wanted.equals(this.channel) && (this.status == Status.CONNECTED || this.status == Status.CONNECTING)) {
			return;
		}
		this.disconnect();
		this.channel = wanted;
		this.failures = 0;
		this.open(++this.generation);
	}

	public synchronized void disconnect() {
		this.generation++;
		this.channel = null;
		this.status = Status.OFF;
		this.error = null;
		WebSocket old = this.socket;
		this.socket = null;
		if (old != null) {
			old.abort();
		}
	}

	/** "https://twitch.tv/Voidlesh_Hugo" or "#voidlesh_hugo" become "voidlesh_hugo". */
	public static String normalize(String name) {
		if (name == null) {
			return "";
		}
		String value = name.strip().toLowerCase(Locale.ROOT);
		int slash = value.lastIndexOf('/');
		if (slash >= 0) {
			value = value.substring(slash + 1);
		}
		value = value.replace("#", "").replace("@", "");
		return value.matches("[a-z0-9_]{2,25}") ? value : "";
	}

	private void open(int gen) {
		this.status = Status.CONNECTING;
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(SERVER, new Listener(gen))
			.whenComplete((ws, failure) -> {
				if (gen != this.generation) {
					if (ws != null) {
						ws.abort();
					}
					return;
				}
				if (failure != null) {
					this.failed(gen, failure.getMessage());
					return;
				}
				this.socket = ws;
				int nick = 10000 + ThreadLocalRandom.current().nextInt(89999);
				ws.sendText("CAP REQ :twitch.tv/tags twitch.tv/commands", true);
				ws.sendText("PASS SCHMOOPIIE", true);
				ws.sendText("NICK justinfan" + nick, true);
				ws.sendText("JOIN #" + this.channel, true);
			});
	}

	private synchronized void failed(int gen, String reason) {
		if (gen != this.generation || gen == this.failedGeneration) {
			return;
		}
		this.failedGeneration = gen;
		this.status = Status.FAILED;
		this.error = reason == null ? "Verbindung fehlgeschlagen" : reason;
		this.socket = null;
		long delay = Math.min(60, 2L << Math.min(5, this.failures++));
		this.timer.schedule(() -> {
			synchronized (this) {
				if (gen == this.generation && this.channel != null) {
					this.open(++this.generation);
				}
			}
		}, delay, TimeUnit.SECONDS);
	}

	/** Handles one IRC line. Package-visible for tests. */
	void handleLine(String line, WebSocket ws) {
		if (line.isEmpty()) {
			return;
		}
		String tags = "";
		String rest = line;
		if (rest.startsWith("@")) {
			int space = rest.indexOf(' ');
			if (space < 0) {
				return;
			}
			tags = rest.substring(1, space);
			rest = rest.substring(space + 1);
		}
		String prefix = "";
		if (rest.startsWith(":")) {
			int space = rest.indexOf(' ');
			if (space < 0) {
				return;
			}
			prefix = rest.substring(1, space);
			rest = rest.substring(space + 1);
		}
		int space = rest.indexOf(' ');
		String command = space < 0 ? rest : rest.substring(0, space);
		String params = space < 0 ? "" : rest.substring(space + 1);
		switch (command) {
			case "PING" -> {
				if (ws != null) {
					ws.sendText("PONG " + params, true);
				}
			}
			case "ROOMSTATE", "366" -> {
				this.status = Status.CONNECTED;
				this.error = null;
				this.failures = 0;
			}
			case "RECONNECT" -> {
				if (ws != null) {
					ws.abort();
				}
				this.failed(this.generation, "Twitch startet den Chat neu");
			}
			case "PRIVMSG" -> {
				int colon = params.indexOf(" :");
				if (colon < 0) {
					return;
				}
				String text = params.substring(colon + 2);
				Map<String, String> tagMap = parseTags(tags);
				String user = tagMap.getOrDefault("display-name", "");
				if (user.isBlank() || !user.matches("[A-Za-z0-9_]+")) {
					// Display names may contain other letters; the login is always plain.
					int bang = prefix.indexOf('!');
					user = bang > 0 ? prefix.substring(0, bang) : prefix;
				}
				String badges = tagMap.getOrDefault("badges", "");
				boolean special = badges.contains("subscriber/") || badges.contains("vip/") || badges.contains("moderator/")
					|| badges.contains("broadcaster/") || badges.contains("founder/");
				if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
					text = text.substring(8, text.length() - 1);
				}
				this.listener.accept(new Message(user, text, special));
			}
			default -> {
			}
		}
	}

	private static Map<String, String> parseTags(String tags) {
		Map<String, String> map = new HashMap<>();
		for (String part : tags.split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) {
				map.put(part.substring(0, eq), part.substring(eq + 1).replace("\\s", " "));
			}
		}
		return map;
	}

	private final class Listener implements WebSocket.Listener {
		private final int gen;

		Listener(int gen) {
			this.gen = gen;
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if (this.gen == TwitchChat.this.generation) {
				synchronized (TwitchChat.this.partial) {
					TwitchChat.this.partial.append(data);
					if (last) {
						String all = TwitchChat.this.partial.toString();
						TwitchChat.this.partial.setLength(0);
						for (String line : all.split("\r\n")) {
							TwitchChat.this.handleLine(line.strip(), webSocket);
						}
					}
				}
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			TwitchChat.this.failed(this.gen, "Getrennt (" + statusCode + ")");
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			TwitchChat.this.failed(this.gen, error.getMessage());
		}
	}
}
