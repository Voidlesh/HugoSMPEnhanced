package com.hugosmpenhanced.client.feature.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;
import com.hugosmpenhanced.updater.UpdateHelper;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Downloads a release jar, checks it and swaps it in when the game closes.
 *
 * <p>Checks before anything is installed: the SHA-256 GitHub lists for the file, that the jar is
 * this mod in exactly the version of the release, and that Minecraft, Fabric and every other mod it
 * needs match what is installed. The jar waits in {@code config/hugosmpenhanced-update/} until the
 * game closes; then {@link UpdateHelper} replaces the old jar in the mods folder in its own process
 * (a running game keeps its jars locked on Windows). The next start reports the result.
 */
public final class UpdateInstaller {
	private static final long MAX_SIZE = 64L * 1024 * 1024;
	private static final Path PENDING = ModUpdates.DIR.resolve("pending.json");
	private static final Path RESULT = ModUpdates.DIR.resolve("result.json");
	private static final Path HELPER_JAR = ModUpdates.DIR.resolve("helper.jar");
	private static final Path HELPER_LOG = ModUpdates.DIR.resolve("helper.log");

	public enum State {
		NONE, DOWNLOADING, READY, FAILED,
		/** The jar needs another Minecraft, Fabric or mod version than installed. */
		INCOMPATIBLE
	}

	/** The release jar is fine but does not fit this game. */
	static final class IncompatibleException extends IOException {
		IncompatibleException(String message) {
			super(message);
		}
	}

	/** A downloaded and checked jar that is installed when the game closes. */
	private record Pending(String version, Path file, String fileName) {
	}

	private static volatile State state = State.NONE;
	private static volatile String stateVersion;
	private static volatile String message;
	private static volatile long downloaded;
	private static volatile long total;
	private static volatile Pending pending;

	private UpdateInstaller() {
	}

	/** Picks up a jar downloaded in an earlier session, or cleans up after an installed one. */
	static void init() {
		try {
			Files.deleteIfExists(HELPER_JAR);
			if (!Files.isRegularFile(PENDING)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(PENDING, StandardCharsets.UTF_8)).getAsJsonObject();
			String version = ModUpdates.string(json, "version");
			String fileName = ModUpdates.string(json, "fileName");
			Path file = fileName != null ? ModUpdates.DIR.resolve(fileName) : null;
			if (version == null || file == null || !Files.isRegularFile(file) || !ModUpdates.newer(version, ModUpdates.currentVersion())) {
				if (file != null) {
					deleteQuietly(file);
				}
				discardPending();
				return;
			}
			pending = new Pending(version, file, fileName);
			state = State.READY;
			stateVersion = version;
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Pending update unreadable, dropping it", e);
			discardPending();
		}
	}

	public static State state(ModUpdates.Release release) {
		return release != null && release.version().equals(stateVersion) ? state : State.NONE;
	}

	public static boolean isReady(ModUpdates.Release release) {
		return state(release) == State.READY;
	}

	/** Why the last download failed, or why this release cannot be installed. */
	public static String message() {
		return message;
	}

	/** 0..1 while downloading, -1 if the size is unknown. */
	public static float progress() {
		long all = total;
		return all > 0 ? Math.min(1.0f, downloaded / (float) all) : -1.0f;
	}

	/** Why this release cannot be installed from inside the game, or null if it can. */
	public static String blocker(ModUpdates.Release release) {
		if (release.jar() == null) {
			return "Das Release hat keine Jar-Datei";
		}
		if (modJar() == null) {
			return "Mod liegt nicht als Jar im mods-Ordner";
		}
		return null;
	}

	public static boolean canInstall(ModUpdates.Release release) {
		State current = state(release);
		return blocker(release) == null && current != State.FAILED && current != State.INCOMPATIBLE;
	}

	/** Downloads and checks the release jar in the background; nothing happens if it is already there. */
	public static void download(ModUpdates.Release release) {
		State current = state(release);
		if (current == State.DOWNLOADING || current == State.READY || current == State.INCOMPATIBLE || blocker(release) != null) {
			return;
		}
		state = State.DOWNLOADING;
		stateVersion = release.version();
		message = null;
		downloaded = 0;
		total = release.jar().size();
		CompletableFuture.runAsync(() -> fetch(release), Util.ioPool()).whenComplete((ignored, error) -> {
			if (error != null) {
				fail(release, reason(error));
				if (incompatible(error)) {
					state = State.INCOMPATIBLE;
				}
				HugoSmpEnhancedClient.LOGGER.warn("Update {} could not be downloaded", release.version(), error);
				return;
			}
			Minecraft.getInstance().execute(() -> TpaToast.show(Component.literal("Update " + release.version() + " bereit"),
				Component.literal("Wird beim Beenden des Spiels installiert"), new ItemStack(Items.NETHER_STAR), 6000, ModUpdates.GOLD));
		});
	}

	/** Downloads again after a failed attempt. */
	public static void retry(ModUpdates.Release release) {
		if (state(release) == State.FAILED) {
			state = State.NONE;
		}
		download(release);
	}

	private static void fail(ModUpdates.Release release, String why) {
		state = State.FAILED;
		stateVersion = release.version();
		message = why;
	}

	private static void fetch(ModUpdates.Release release) {
		ModUpdates.Asset asset = release.jar();
		Path part = ModUpdates.DIR.resolve(asset.name() + ".part");
		Path file = ModUpdates.DIR.resolve(asset.name());
		try {
			Files.createDirectories(ModUpdates.DIR);
			HttpRequest request = HttpRequest.newBuilder(URI.create(asset.url()))
				.timeout(Duration.ofSeconds(60))
				.header("Accept", "application/octet-stream")
				.header("User-Agent", "HugoSMPEnhanced-Updater")
				.GET().build();
			HttpResponse<InputStream> response = ModUpdates.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException("Download fehlgeschlagen (HTTP " + response.statusCode() + ")");
			}
			long length = response.headers().firstValueAsLong("Content-Length").orElse(asset.size());
			if (length > MAX_SIZE) {
				response.body().close();
				throw new IOException("Datei ist unerwartet groß");
			}
			total = length;
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
				byte[] buffer = new byte[16 * 1024];
				long count = 0;
				int read;
				while ((read = in.read(buffer)) != -1) {
					count += read;
					if (count > MAX_SIZE) {
						throw new IOException("Datei ist unerwartet groß");
					}
					out.write(buffer, 0, read);
					sha.update(buffer, 0, read);
					downloaded = count;
				}
			}
			if (asset.sha256() != null && !asset.sha256().equals(HexFormat.of().formatHex(sha.digest()))) {
				throw new IOException("Prüfsumme stimmt nicht, Datei beschädigt");
			}
			verify(part, release.version());
			Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
			JsonObject json = new JsonObject();
			json.addProperty("version", release.version());
			json.addProperty("fileName", asset.name());
			Files.writeString(PENDING, json.toString(), StandardCharsets.UTF_8);
			Pending old = pending;
			if (old != null && !old.file().equals(file)) {
				Files.deleteIfExists(old.file());
			}
			pending = new Pending(release.version(), file, asset.name());
			state = State.READY;
		} catch (Exception e) {
			deleteQuietly(part);
			throw new IllegalStateException(e);
		}
	}

	private static boolean incompatible(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof IncompatibleException) {
				return true;
			}
		}
		return false;
	}

	/** A short German reason for the player from a failed download. */
	private static String reason(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
			|| cause instanceof IllegalStateException && cause.getCause() != null)) {
			cause = cause.getCause();
		}
		if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpTimeoutException
			|| cause instanceof java.nio.channels.UnresolvedAddressException) {
			return "GitHub nicht erreichbar";
		}
		if (cause instanceof java.nio.file.AccessDeniedException) {
			return "Kein Schreibrecht im config-Ordner";
		}
		return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
	}

	/** Throws with a message for the player if the jar is not this mod in {@code version} or does not fit this game. */
	static void verify(Path jar, String version) throws IOException {
		JsonObject meta;
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				throw new IOException("Keine Fabric-Mod");
			}
			try (InputStream in = zip.getInputStream(entry)) {
				meta = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (java.util.zip.ZipException e) {
			throw new IOException("Datei ist keine gültige Jar");
		}
		if (!HugoSmpEnhancedClient.MOD_ID.equals(ModUpdates.string(meta, "id"))) {
			throw new IOException("Jar ist nicht HugoSMP Enhanced");
		}
		String jarVersion = ModUpdates.string(meta, "version");
		if (jarVersion == null || !jarVersion.equals(version)) {
			throw new IOException("Jar hat Version " + jarVersion + " statt " + version);
		}
		List<String> missing = unmetDependencies(meta);
		if (!missing.isEmpty()) {
			throw new IncompatibleException("Braucht " + String.join(", ", missing));
		}
	}

	/** Readable names of the "depends" entries the installed game does not satisfy. */
	static List<String> unmetDependencies(JsonObject meta) {
		List<String> missing = new ArrayList<>();
		if (!meta.has("depends") || !meta.get("depends").isJsonObject()) {
			return missing;
		}
		for (Map.Entry<String, JsonElement> entry : meta.getAsJsonObject("depends").entrySet()) {
			String id = entry.getKey();
			List<String> ranges = new ArrayList<>();
			if (entry.getValue().isJsonArray()) {
				entry.getValue().getAsJsonArray().forEach(e -> ranges.add(e.getAsString()));
			} else if (entry.getValue().isJsonPrimitive()) {
				ranges.add(entry.getValue().getAsString());
			}
			Optional<ModContainer> installed = FabricLoader.getInstance().getModContainer(id);
			String name = switch (id) {
				case "minecraft" -> "Minecraft";
				case "fabricloader" -> "Fabric Loader";
				case "fabric-api" -> "Fabric API";
				case "java" -> "Java";
				default -> id;
			};
			if (installed.isEmpty()) {
				missing.add("die Mod " + name);
				continue;
			}
			Version version = installed.get().getMetadata().getVersion();
			boolean ok = ranges.isEmpty();
			for (String range : ranges) {
				try {
					ok |= VersionPredicate.parse(range).test(version);
				} catch (Exception e) {
					ok = true;
				}
			}
			if (!ok) {
				missing.add(name + " " + String.join(" oder ", ranges).replace("*", "").trim());
			}
		}
		return missing;
	}

	// ------------------------------------------------------------------ install on exit

	/**
	 * The mod's own jar in the mods folder, or null when it runs from somewhere it cannot be
	 * replaced (dev environment, nested jar). {@code hugosmpenhanced.updateModJar} overrides it for tests.
	 */
	static Path modJar() {
		String override = System.getProperty("hugosmpenhanced.updateModJar");
		if (override != null) {
			return Path.of(override);
		}
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(HugoSmpEnhancedClient.MOD_ID);
		if (mod.isEmpty() || mod.get().getOrigin().getKind() != ModOrigin.Kind.PATH) {
			return null;
		}
		List<Path> paths = mod.get().getOrigin().getPaths();
		if (paths.size() != 1) {
			return null;
		}
		Path jar = paths.get(0).toAbsolutePath().normalize();
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
		if (!Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar") || !jar.startsWith(mods)) {
			return null;
		}
		return jar;
	}

	/** Called while the game closes: starts the helper that swaps the jar once the game is gone. */
	static void installOnExit() {
		Pending update = pending;
		Path jar = modJar();
		if (state != State.READY || update == null || jar == null || !Files.isRegularFile(update.file())) {
			return;
		}
		try {
			// the helper runs from a copy, so the jar it replaces is not held open by it
			Files.copy(jar, HELPER_JAR, StandardCopyOption.REPLACE_EXISTING);
			Path target = jar.resolveSibling(update.fileName());
			List<String> command = List.of(javaExecutable(), "-cp", HELPER_JAR.toString(), UpdateHelper.class.getName(),
				Long.toString(ProcessHandle.current().pid()), jar.toString(), update.file().toString(), target.toString(),
				RESULT.toString());
			Files.deleteIfExists(RESULT);
			new ProcessBuilder(command).redirectErrorStream(true)
				.redirectOutput(HELPER_LOG.toFile()).start();
			HugoSmpEnhancedClient.LOGGER.info("Installing update {} after the game has closed", update.version());
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.error("Could not start the update helper", e);
		}
	}

	private static String javaExecutable() {
		Optional<String> running = ProcessHandle.current().info().command();
		if (running.isPresent() && Files.isExecutable(Path.of(running.get()))) {
			return running.get();
		}
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		return Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString();
	}

	/** At start: a toast for what the helper did when the game last closed. */
	static void showLastResult() {
		if (!Files.isRegularFile(RESULT)) {
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(RESULT, StandardCharsets.UTF_8)).getAsJsonObject();
			boolean ok = json.has("ok") && json.get("ok").getAsBoolean();
			if (ok) {
				TpaToast.show(Component.literal("HugoSMP Enhanced aktualisiert"),
					Component.literal("Jetzt auf Version " + ModUpdates.currentVersion()), new ItemStack(Items.NETHER_STAR), 7000, ModUpdates.GOLD);
			} else {
				String why = ModUpdates.string(json, "message");
				HugoSmpEnhancedClient.LOGGER.warn("Last update install failed: {}", why);
				// start over next time instead of failing at every exit
				discardPending();
				state = State.NONE;
				TpaToast.show(Component.literal("Update nicht installiert"),
					Component.literal("Die alte Version läuft weiter, Details in den Logs"), new ItemStack(Items.BARRIER), 8000, TpaToast.WARNING_COLOR);
			}
			Files.deleteIfExists(RESULT);
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Update result unreadable", e);
			deleteQuietly(RESULT);
		}
	}

	private static void discardPending() {
		Pending old = pending;
		pending = null;
		if (old != null) {
			deleteQuietly(old.file());
		}
		deleteQuietly(PENDING);
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// a leftover file in the update folder does no harm
		}
	}
}
