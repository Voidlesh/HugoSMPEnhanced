package com.hugosmpenhanced.test;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Stands in for the Discord desktop app: listens on discord-ipc-0 in a temp folder, answers the
 * handshake like Discord (READY with a user, or close code 4000 for an unknown id) and records
 * every activity it is sent.
 */
final class FakeDiscord {
	static final String BAD_ID = "1111111111111111111";

	final Path dir;
	final List<JsonElement> activities = new CopyOnWriteArrayList<>();
	final List<String> handshakes = new CopyOnWriteArrayList<>();
	volatile int closedConnections;
	private final ServerSocketChannel server;

	FakeDiscord() throws IOException {
		this.dir = Files.createTempDirectory("dc");
		this.server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
		this.server.bind(UnixDomainSocketAddress.of(this.dir.resolve("discord-ipc-0")));
		Thread accept = new Thread(this::acceptLoop, "Fake Discord");
		accept.setDaemon(true);
		accept.start();
	}

	/** The newest activity, or null if none (or a clear) was sent. */
	JsonObject last() {
		if (this.activities.isEmpty()) {
			return null;
		}
		JsonElement last = this.activities.get(this.activities.size() - 1);
		return last.isJsonObject() ? last.getAsJsonObject() : null;
	}

	String lastField(String key) {
		JsonObject last = this.last();
		return last != null && last.has(key) ? last.get(key).getAsString() : null;
	}

	private void acceptLoop() {
		while (true) {
			try {
				SocketChannel client = this.server.accept();
				Thread connection = new Thread(() -> this.serve(client), "Fake Discord connection");
				connection.setDaemon(true);
				connection.start();
			} catch (IOException e) {
				return;
			}
		}
	}

	private void serve(SocketChannel client) {
		try (client) {
			while (true) {
				ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
				readFully(client, header);
				header.flip();
				int op = header.getInt();
				int length = header.getInt();
				ByteBuffer body = ByteBuffer.allocate(length);
				readFully(client, body);
				JsonObject data = JsonParser.parseString(new String(body.array(), StandardCharsets.UTF_8)).getAsJsonObject();
				if (op == 0) {
					String id = data.get("client_id").getAsString();
					this.handshakes.add(id);
					if (BAD_ID.equals(id)) {
						JsonObject close = new JsonObject();
						close.addProperty("code", 4000);
						close.addProperty("message", "Invalid Client ID");
						send(client, 2, close);
						return;
					}
					send(client, 1, JsonParser.parseString("{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{\"v\":1,"
						+ "\"user\":{\"id\":\"1\",\"username\":\"voidlesh\",\"global_name\":\"Voidlesh\"}}}").getAsJsonObject());
				} else if (op == 1 && "SET_ACTIVITY".equals(data.get("cmd").getAsString())) {
					JsonElement activity = data.getAsJsonObject("args").get("activity");
					this.activities.add(activity);
					System.out.println("FAKE DISCORD activity: " + activity);
					JsonObject reply = new JsonObject();
					reply.addProperty("cmd", "SET_ACTIVITY");
					reply.add("data", activity);
					reply.add("nonce", data.get("nonce"));
					send(client, 1, reply);
				}
			}
		} catch (IOException | RuntimeException e) {
			this.closedConnections++;
		}
	}

	private static void readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			if (channel.read(buffer) < 0) {
				throw new IOException("closed");
			}
		}
	}

	private static void send(SocketChannel channel, int op, JsonObject data) throws IOException {
		byte[] json = data.toString().getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(op).putInt(json.length).put(json).flip();
		while (buffer.hasRemaining()) {
			channel.write(buffer);
		}
	}
}
