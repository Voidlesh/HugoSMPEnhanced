package com.hugosmpenhanced.client.feature.discord;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;

/**
 * Keeps one connection to the Discord app on a background thread: finds it, says hello with the
 * application id, sends the newest activity and answers pings. The game thread only hands over
 * what it wants shown ({@link #show}); nothing here ever blocks the game.
 */
final class DiscordConnection {
	/** Discord allows 5 activity updates per 20 seconds; one every 4 s stays under that. */
	private static final long MIN_UPDATE_GAP = 4_000;
	private static final long RETRY_FIRST = 5_000;
	private static final long RETRY_MAX = 30_000;
	private static final long HANDSHAKE_TIMEOUT = 8_000;

	enum Status {
		OFF, SEARCHING, NOT_FOUND, BAD_ID, CONNECTED
	}

	private final Object lock = new Object();
	private Thread thread;

	// Written by the game thread, read by the worker, all under the lock.
	private boolean enabled;
	private String clientId = "";
	private JsonObject wanted;
	private boolean wantedChanged;

	// Written by the worker, read by the game thread.
	private volatile Status status = Status.OFF;
	private volatile String userName = "";
	private volatile String lastError = "";
	private volatile String rejectedId;

	Status status() {
		return this.status;
	}

	/** Discord display name once connected. */
	String userName() {
		return this.userName;
	}

	String lastError() {
		return this.lastError;
	}

	/** What to show; {@code null} clears the presence. Unchanged values are not sent again. */
	void show(boolean enabled, String clientId, JsonObject activity) {
		synchronized (this.lock) {
			boolean idChanged = !Objects.equals(this.clientId, clientId);
			if (this.enabled == enabled && !idChanged && Objects.equals(this.wanted, activity)) {
				return;
			}
			this.enabled = enabled;
			this.clientId = clientId;
			this.wanted = activity == null ? null : activity.deepCopy();
			this.wantedChanged = true;
			if (enabled && (this.thread == null || !this.thread.isAlive())) {
				this.thread = new Thread(this::run, "HugoSMP Enhanced Discord");
				this.thread.setDaemon(true);
				this.thread.start();
			}
			this.lock.notifyAll();
		}
	}

	void shutdown() {
		this.show(false, this.clientId, null);
		Thread worker = this.thread;
		if (worker != null) {
			try {
				worker.join(1_000);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void run() {
		long retry = RETRY_FIRST;
		while (true) {
			String id;
			synchronized (this.lock) {
				if (!this.enabled) {
					this.status = Status.OFF;
					this.thread = null;
					return;
				}
				id = this.clientId;
			}
			if (!id.matches("\\d{15,25}")) {
				this.rejectedId = null;
				this.status = Status.BAD_ID;
				this.lastError = id.isBlank() ? "Application-ID fehlt" : "Application-ID ist keine Zahl";
				this.sleep(1_000);
				continue;
			}
			if (this.status == Status.BAD_ID && id.equals(this.rejectedId)) {
				// Wait for a new id instead of asking Discord again; show() wakes us up.
				this.sleep(RETRY_MAX);
				continue;
			}
			if (this.status != Status.NOT_FOUND) {
				this.status = Status.SEARCHING;
			}
			try (DiscordIpc ipc = DiscordIpc.open()) {
				retry = RETRY_FIRST;
				this.session(ipc, id);
			} catch (IOException e) {
				if (!id.equals(this.rejectedId)) {
					this.status = Status.NOT_FOUND;
				}
				this.userName = "";
				this.sleep(retry);
				retry = Math.min(retry * 2, RETRY_MAX);
			}
		}
	}

	/** One connection from hello to goodbye; returns when the game turns it off or the id changes. */
	private void session(DiscordIpc ipc, String id) throws IOException {
		JsonObject hello = new JsonObject();
		hello.addProperty("v", 1);
		hello.addProperty("client_id", id);
		ipc.send(DiscordIpc.OP_HANDSHAKE, hello);

		long deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT;
		boolean ready = false;
		while (!ready) {
			for (DiscordIpc.Frame frame : ipc.poll()) {
				if (frame.op() == DiscordIpc.OP_CLOSE) {
					this.closed(frame.data(), id);
					return;
				}
				if ("READY".equals(string(frame.data(), "evt"))) {
					ready = true;
					JsonObject user = object(object(frame.data(), "data"), "user");
					String global = string(user, "global_name");
					this.userName = global != null ? global : Objects.requireNonNullElse(string(user, "username"), "");
				}
			}
			if (System.currentTimeMillis() > deadline) {
				throw new IOException("Discord did not answer the handshake");
			}
			this.sleep(50);
		}
		this.status = Status.CONNECTED;
		this.lastError = "";
		HugoSmpEnhancedClient.LOGGER.info("Discord Rich Presence connected as {}", this.userName);

		long lastSent = 0;
		boolean sentOnce = false;
		while (true) {
			JsonObject activity = null;
			boolean send = false;
			synchronized (this.lock) {
				if (!this.enabled || !this.clientId.equals(id)) {
					// Closing the socket clears the presence in Discord.
					return;
				}
				long now = System.currentTimeMillis();
				if ((this.wantedChanged || !sentOnce) && now - lastSent >= MIN_UPDATE_GAP) {
					activity = this.wanted;
					this.wantedChanged = false;
					send = true;
				}
			}
			if (send) {
				ipc.send(DiscordIpc.OP_FRAME, setActivity(activity));
				lastSent = System.currentTimeMillis();
				sentOnce = true;
			}
			for (DiscordIpc.Frame frame : ipc.poll()) {
				switch (frame.op()) {
					case DiscordIpc.OP_PING -> ipc.send(DiscordIpc.OP_PONG, frame.data());
					case DiscordIpc.OP_CLOSE -> {
						this.closed(frame.data(), id);
						throw new IOException("Discord closed the connection");
					}
					default -> {
						if ("ERROR".equals(string(frame.data(), "evt"))) {
							this.lastError = Objects.requireNonNullElse(string(object(frame.data(), "data"), "message"), "Fehler");
							HugoSmpEnhancedClient.LOGGER.warn("Discord rejected the activity: {}", this.lastError);
						} else if ("SET_ACTIVITY".equals(string(frame.data(), "cmd"))) {
							this.lastError = "";
						}
					}
				}
			}
			synchronized (this.lock) {
				long gap = MIN_UPDATE_GAP - (System.currentTimeMillis() - lastSent);
				long wait = this.wantedChanged ? Math.min(250, gap) : 250;
				if (wait > 0 && this.enabled && this.clientId.equals(id)) {
					try {
						this.lock.wait(wait);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		}
	}

	private void closed(JsonObject data, String id) {
		String message = Objects.requireNonNullElse(string(data, "message"), "");
		int code = data.has("code") ? data.get("code").getAsInt() : 0;
		this.lastError = message;
		if (code == 4000 || message.toLowerCase(java.util.Locale.ROOT).contains("client id")) {
			this.status = Status.BAD_ID;
			this.rejectedId = id;
			this.lastError = "Discord lehnt die App ab";
		}
	}

	private static JsonObject setActivity(JsonObject activity) {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		args.add("activity", activity == null ? JsonNull.INSTANCE : activity);
		JsonObject frame = new JsonObject();
		frame.addProperty("cmd", "SET_ACTIVITY");
		frame.add("args", args);
		frame.addProperty("nonce", UUID.randomUUID().toString());
		return frame;
	}

	private void sleep(long millis) {
		if (millis <= 0) {
			return;
		}
		synchronized (this.lock) {
			try {
				this.lock.wait(millis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static JsonObject object(JsonObject parent, String key) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static String string(JsonObject parent, String key) {
		JsonElement element = parent == null ? null : parent.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}
}
