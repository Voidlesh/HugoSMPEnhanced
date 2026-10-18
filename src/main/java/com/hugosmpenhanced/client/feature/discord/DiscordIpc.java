package com.hugosmpenhanced.client.feature.discord;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The local socket the Discord desktop app opens for Rich Presence (discord-ipc-0..9): a named
 * pipe on Windows, a Unix socket elsewhere. Frames are an op code and a length, both 32 bit
 * little endian, followed by UTF-8 JSON. No login and no token, only the application id.
 */
final class DiscordIpc implements Closeable {
	static final int OP_HANDSHAKE = 0;
	static final int OP_FRAME = 1;
	static final int OP_CLOSE = 2;
	static final int OP_PING = 3;
	static final int OP_PONG = 4;

	/** Overrides the folder searched for discord-ipc-N, for the game test's fake Discord. */
	static final String DIR_PROPERTY = "hugosmpenhanced.discordIpcDir";

	private static final String[] UNIX_SUBDIRS = {"", "app/com.discordapp.Discord/", "app/com.discordapp.DiscordCanary/",
		"snap.discord/", "snap.discord-canary/", "app/dev.vencord.Vesktop/", ".flatpak/dev.vencord.Vesktop/xdg-run/"};

	private final Transport transport;
	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

	private DiscordIpc(Transport transport) {
		this.transport = transport;
	}

	record Frame(int op, JsonObject data) {
	}

	/** Opens the first Discord socket that accepts a connection, or throws if none does. */
	static DiscordIpc open() throws IOException {
		IOException last = null;
		boolean windows = isWindows();
		for (String candidate : candidates(windows)) {
			try {
				return new DiscordIpc(windows ? new PipeTransport(candidate) : new UnixTransport(Path.of(candidate)));
			} catch (IOException | UnsupportedOperationException e) {
				last = e instanceof IOException io ? io : new IOException(e);
			}
		}
		throw last != null ? last : new IOException("no Discord socket");
	}

	private static boolean isWindows() {
		return System.getProperty(DIR_PROPERTY) == null
			&& System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static List<String> candidates(boolean windows) {
		List<String> paths = new ArrayList<>();
		String override = System.getProperty(DIR_PROPERTY);
		for (int i = 0; i < 10; i++) {
			if (windows) {
				paths.add("\\\\.\\pipe\\discord-ipc-" + i);
				continue;
			}
			for (String base : override != null ? List.of(override) : unixBases()) {
				for (String sub : override != null ? new String[] {""} : UNIX_SUBDIRS) {
					Path path = Path.of(base, sub + "discord-ipc-" + i);
					if (Files.exists(path)) {
						paths.add(path.toString());
					}
				}
			}
		}
		return paths;
	}

	private static List<String> unixBases() {
		List<String> bases = new ArrayList<>();
		for (String env : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
			String value = System.getenv(env);
			if (value != null && !value.isBlank() && !bases.contains(value)) {
				bases.add(value);
			}
		}
		bases.add("/tmp");
		return bases;
	}

	void send(int op, JsonObject data) throws IOException {
		byte[] json = data.toString().getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(op).putInt(json.length).put(json);
		this.transport.write(buffer.array());
	}

	/** Every complete frame that has arrived so far, without waiting for more. */
	List<Frame> poll() throws IOException {
		byte[] chunk = new byte[4096];
		IOException gone = null;
		try {
			int read;
			while ((read = this.transport.readAvailable(chunk)) > 0) {
				this.pending.write(chunk, 0, read);
			}
		} catch (IOException e) {
			// Discord often sends a close frame and hangs up at once: hand out the frame first.
			gone = e;
		}
		List<Frame> frames = new ArrayList<>();
		byte[] bytes = this.pending.toByteArray();
		int offset = 0;
		while (bytes.length - offset >= 8) {
			ByteBuffer header = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN);
			int op = header.getInt();
			int length = header.getInt();
			if (length < 0 || length > 1 << 20) {
				throw new IOException("bad frame length " + length);
			}
			if (bytes.length - offset - 8 < length) {
				break;
			}
			String json = new String(bytes, offset + 8, length, StandardCharsets.UTF_8);
			JsonObject data;
			try {
				data = JsonParser.parseString(json).getAsJsonObject();
			} catch (RuntimeException e) {
				data = new JsonObject();
			}
			frames.add(new Frame(op, data));
			offset += 8 + length;
		}
		this.pending.reset();
		this.pending.write(bytes, offset, bytes.length - offset);
		if (gone != null && frames.isEmpty()) {
			throw gone;
		}
		return frames;
	}

	@Override
	public void close() {
		try {
			this.transport.close();
		} catch (IOException ignored) {
			// already gone
		}
	}

	private interface Transport extends Closeable {
		void write(byte[] bytes) throws IOException;

		/** Bytes read without blocking, 0 if nothing is there; throws once the other side is gone. */
		int readAvailable(byte[] into) throws IOException;
	}

	private static final class UnixTransport implements Transport {
		private final SocketChannel channel;

		UnixTransport(Path path) throws IOException {
			this.channel = SocketChannel.open(StandardProtocolFamily.UNIX);
			try {
				this.channel.connect(UnixDomainSocketAddress.of(path));
				this.channel.configureBlocking(false);
			} catch (IOException e) {
				this.channel.close();
				throw e;
			}
		}

		@Override
		public void write(byte[] bytes) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			long deadline = System.currentTimeMillis() + 2000;
			while (buffer.hasRemaining()) {
				if (this.channel.write(buffer) == 0) {
					if (System.currentTimeMillis() > deadline) {
						throw new IOException("Discord socket does not accept data");
					}
					Thread.onSpinWait();
				}
			}
		}

		@Override
		public int readAvailable(byte[] into) throws IOException {
			int read = this.channel.read(ByteBuffer.wrap(into));
			if (read < 0) {
				throw new IOException("Discord closed the socket");
			}
			return read;
		}

		@Override
		public void close() throws IOException {
			this.channel.close();
		}
	}

	/** Windows named pipe; its length is the number of bytes waiting, so reads never block. */
	private static final class PipeTransport implements Transport {
		private final RandomAccessFile pipe;

		PipeTransport(String path) throws IOException {
			this.pipe = new RandomAccessFile(path, "rw");
		}

		@Override
		public void write(byte[] bytes) throws IOException {
			this.pipe.write(bytes);
		}

		@Override
		public int readAvailable(byte[] into) throws IOException {
			long available = this.pipe.length();
			if (available <= 0) {
				return 0;
			}
			return this.pipe.read(into, 0, (int) Math.min(available, into.length));
		}

		@Override
		public void close() throws IOException {
			this.pipe.close();
		}
	}
}
