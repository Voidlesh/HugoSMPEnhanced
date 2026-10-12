package com.hugosmpenhanced.client.feature.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Looks for new releases of the mod on GitHub: once when the game has started and then every few
 * hours. A newer version shows up as a badge on the title screen, a toast and one chat line after
 * joining a world; {@link UpdateScreen} shows its changelog and installs it via
 * {@link UpdateInstaller}. With "Automatisch installieren" on, it is downloaded right away and
 * swapped in when the game closes.
 *
 * <p>Uses the public GitHub API without a token (60 requests per hour and IP). Answers are cached
 * with their ETag; GitHub does not count a "not modified" answer against that limit.
 */
public final class ModUpdates {
	public static final String REPO = "Voidlesh/HugoSMPEnhanced";
	public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
	private static final String DEFAULT_API = "https://api.github.com/repos/" + REPO + "/releases?per_page=20";
	private static final String USER_AGENT = "HugoSMPEnhanced-Updater (+https://github.com/" + REPO + ")";
	static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced-update");
	private static final Path CACHE = DIR.resolve("releases.json");
	private static final long RECHECK_MS = 6 * 60 * 60 * 1000L;
	private static final long RETRY_MS = 30 * 60 * 1000L;
	/** Ticks after joining a world before the chat line, so it is not lost in the server's join messages. */
	private static final int CHAT_DELAY_TICKS = 100;
	static final int GOLD = 0xFFFFC83D;
	static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	public enum Status {
		/** Checking is switched off. */
		OFF,
		/** Not checked yet in this session. */
		IDLE,
		CHECKING,
		UP_TO_DATE,
		AVAILABLE,
		FAILED
	}

	/** A jar attached to a release. {@code sha256} is null when GitHub gives no digest. */
	public record Asset(String name, String url, long size, String sha256) {
	}

	/** A release that is newer than the running version; {@code jar} is null if it has none. */
	public record Release(String version, String tag, String name, String body, String url, String publishedAt,
			boolean prerelease, Asset jar) {
		/** "14.10.2026", or empty. */
		public String date() {
			try {
				return OffsetDateTime.parse(this.publishedAt).atZoneSameInstant(ZoneId.systemDefault())
					.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
			} catch (Exception e) {
				return "";
			}
		}
	}

	private static volatile Status status = Status.IDLE;
	private static volatile Release latest;
	private static volatile String lastError;
	private static volatile boolean checking;
	private static CompletableFuture<Void> running = CompletableFuture.completedFuture(null);
	private static long nextCheckAt = Long.MAX_VALUE;
	private static String toastShownFor;
	private static String chatShownFor;
	private static int chatCountdown = -1;

	private ModUpdates() {
	}

	public static void register() {
		UpdateInstaller.init();
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			UpdateInstaller.showLastResult();
			nextCheckAt = Util.getMillis();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> UpdateInstaller.installOnExit());
		ClientTickEvents.END_CLIENT_TICK.register(ModUpdates::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> chatCountdown = CHAT_DELAY_TICKS);
	}

	private static void tick(Minecraft client) {
		if (!ModConfigManager.CONFIG.updateCheck) {
			if (status != Status.OFF && !checking) {
				status = Status.OFF;
			}
		} else {
			if (status == Status.OFF) {
				status = Status.IDLE;
				nextCheckAt = Util.getMillis();
			}
			if (!checking && Util.getMillis() >= nextCheckAt) {
				check();
			}
		}
		if (chatCountdown >= 0 && client.player != null && --chatCountdown < 0) {
			announceInChat(client);
		}
	}

	public static Status status() {
		return status;
	}

	/** The newest release above the running version, while {@link #status()} is AVAILABLE. */
	public static Release available() {
		return status == Status.AVAILABLE ? latest : null;
	}

	/** Available and not skipped: what the title screen badge, toast and chat line react to. */
	public static Release notifiable() {
		Release release = available();
		if (release == null || release.version().equals(ModConfigManager.CONFIG.updateSkippedVersion)) {
			return null;
		}
		return release;
	}

	public static String lastError() {
		return lastError;
	}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(HugoSmpEnhancedClient.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
	}

	/** Checks now in the background, unless a check is already running. */
	public static synchronized CompletableFuture<Void> check() {
		if (checking) {
			return running;
		}
		checking = true;
		status = Status.CHECKING;
		running = CompletableFuture.runAsync(ModUpdates::fetch, Util.ioPool()).whenComplete((ignored, error) -> {
			if (error != null) {
				fail(error);
			}
			checking = false;
			nextCheckAt = Util.getMillis() + (status == Status.FAILED ? RETRY_MS : RECHECK_MS);
			Minecraft.getInstance().execute(ModUpdates::afterCheck);
		});
		return running;
	}

	private static void fetch() {
		String url = System.getProperty("hugosmpenhanced.updateUrl", DEFAULT_API);
		try {
			JsonObject cache = readCache();
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.header("User-Agent", USER_AGENT)
				.GET();
			if (cache != null && url.equals(string(cache, "url")) && string(cache, "etag") != null) {
				request.header("If-None-Match", string(cache, "etag"));
			}
			HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			String body;
			if (response.statusCode() == 304 && cache != null) {
				body = string(cache, "body");
			} else if (response.statusCode() == 200) {
				body = response.body();
				writeCache(url, response.headers().firstValue("ETag").orElse(null), body);
			} else if (response.statusCode() == 403 || response.statusCode() == 429) {
				throw new IllegalStateException("GitHub-Limit erreicht, später nochmal");
			} else {
				throw new IllegalStateException("GitHub antwortet " + response.statusCode());
			}
			Release newest = newest(JsonParser.parseString(body), currentVersion(), ModConfigManager.CONFIG.updatePrereleases);
			latest = newest;
			lastError = null;
			status = newest != null ? Status.AVAILABLE : Status.UP_TO_DATE;
		} catch (Exception e) {
			fail(e);
		}
	}

	private static void fail(Throwable error) {
		Throwable cause = error.getCause() != null && error instanceof java.util.concurrent.CompletionException ? error.getCause() : error;
		lastError = cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpTimeoutException
			|| cause instanceof java.nio.channels.UnresolvedAddressException
			? "GitHub nicht erreichbar" : String.valueOf(cause.getMessage());
		status = Status.FAILED;
		HugoSmpEnhancedClient.LOGGER.warn("Update check failed: {}", cause.toString());
	}

	/** On the render thread after every check: toast, and the automatic download if wanted. */
	private static void afterCheck() {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof TitleScreen title) {
			// lays the title screen out again, so the badge appears or goes away
			title.resize(title.width, title.height);
		}
		Release release = notifiable();
		if (release == null) {
			return;
		}
		ModConfig config = ModConfigManager.CONFIG;
		if (config.updateAutoInstall && UpdateInstaller.canInstall(release)) {
			UpdateInstaller.download(release);
			return;
		}
		if (client.player != null) {
			// in a world the chat line says it, with a link
			announceInChat(client);
		} else if (!release.version().equals(toastShownFor) && !(client.screen instanceof UpdateScreen)) {
			toastShownFor = release.version();
			TpaToast.show(Component.literal("Update verfügbar: " + release.version()),
				Component.literal("Oben im Hauptmenü ansehen"), new ItemStack(Items.NETHER_STAR), 7000, GOLD);
		}
	}

	private static void announceInChat(Minecraft client) {
		Release release = notifiable();
		if (release == null || release.version().equals(chatShownFor) || client.player == null) {
			return;
		}
		chatShownFor = release.version();
		boolean ready = UpdateInstaller.isReady(release);
		MutableComponent line = Component.literal("[HugoSMP Enhanced] ").withStyle(Style.EMPTY.withColor(GOLD & 0xFFFFFF))
			.append(Component.literal(ready
				? "Version " + release.version() + " wird beim Beenden des Spiels installiert. "
				: "Version " + release.version() + " ist da. ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("[Ansehen]").withStyle(Style.EMPTY.withColor(GOLD & 0xFFFFFF).withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand("/hugosmpenhanced update"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Änderungen ansehen und installieren")))));
		client.player.displayClientMessage(line, false);
	}

	/** {@code /hugosmpenhanced update}: the update screen, or a fresh check with the answer in chat. */
	public static void openOrCheck(Minecraft client) {
		Release release = available();
		if (release != null) {
			client.setScreen(new UpdateScreen(client.screen, release));
			return;
		}
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal("Suche nach Updates…").withStyle(ChatFormatting.GRAY), false);
		}
		check().thenRun(() -> client.execute(() -> {
			if (client.player == null) {
				return;
			}
			Release found = available();
			Component answer = switch (status) {
				case AVAILABLE -> Component.literal("Version " + found.version() + " ist da, öffne mit /hugosmpenhanced update")
					.withStyle(Style.EMPTY.withColor(GOLD & 0xFFFFFF));
				case FAILED -> Component.literal("Update-Suche fehlgeschlagen: " + lastError).withStyle(ChatFormatting.RED);
				default -> Component.literal("HugoSMP Enhanced " + currentVersion() + " ist aktuell.").withStyle(ChatFormatting.GREEN);
			};
			client.player.displayClientMessage(answer, false);
		}));
	}

	// ------------------------------------------------------------------ parsing

	/**
	 * The newest non-draft release above {@code current} from GitHub's release list, or null.
	 * Pre-releases only with {@code prereleases}.
	 */
	public static Release newest(JsonElement root, String current, boolean prereleases) {
		if (!root.isJsonArray()) {
			throw new IllegalStateException("Unerwartete Antwort von GitHub");
		}
		Version running = parse(current);
		Release best = null;
		Version bestVersion = null;
		for (JsonElement element : root.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			boolean prerelease = bool(json, "prerelease");
			if (bool(json, "draft") || (prerelease && !prereleases)) {
				continue;
			}
			String tag = string(json, "tag_name");
			Version version = tag != null ? parse(stripV(tag)) : null;
			if (version == null || running == null || version.compareTo(running) <= 0
				|| (bestVersion != null && version.compareTo(bestVersion) <= 0)) {
				continue;
			}
			String name = string(json, "name");
			best = new Release(stripV(tag), tag, name != null && !name.isBlank() ? name : tag,
				nonNull(string(json, "body")), nonNull(string(json, "html_url")), nonNull(string(json, "published_at")),
				prerelease, jar(json));
			bestVersion = version;
		}
		return best;
	}

	/** The mod jar among a release's files: the first .jar that is not a sources or dev jar. */
	private static Asset jar(JsonObject release) {
		if (!release.has("assets") || !release.get("assets").isJsonArray()) {
			return null;
		}
		JsonArray assets = release.getAsJsonArray("assets");
		for (JsonElement element : assets) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject asset = element.getAsJsonObject();
			String name = string(asset, "name");
			String url = string(asset, "browser_download_url");
			if (name == null || url == null) {
				continue;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if (!lower.endsWith(".jar") || lower.endsWith("-sources.jar") || lower.endsWith("-dev.jar")
				|| name.contains("/") || name.contains("\\")) {
				continue;
			}
			String digest = string(asset, "digest");
			String sha256 = digest != null && digest.startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;
			long size = asset.has("size") && asset.get("size").isJsonPrimitive() ? asset.get("size").getAsLong() : -1;
			return new Asset(name, url, size, sha256);
		}
		return null;
	}

	static Version parse(String version) {
		try {
			return Version.parse(version);
		} catch (Exception e) {
			return null;
		}
	}

	static boolean newer(String version, String than) {
		Version a = parse(version);
		Version b = parse(than);
		return a != null && b != null && a.compareTo(b) > 0;
	}

	private static String stripV(String tag) {
		return tag.length() > 1 && (tag.charAt(0) == 'v' || tag.charAt(0) == 'V') && Character.isDigit(tag.charAt(1))
			? tag.substring(1) : tag;
	}

	static String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static boolean bool(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsBoolean();
	}

	private static String nonNull(String value) {
		return value != null ? value : "";
	}

	// ------------------------------------------------------------------ cache

	private static JsonObject readCache() {
		try {
			if (Files.isRegularFile(CACHE)) {
				JsonElement json = JsonParser.parseString(Files.readString(CACHE, StandardCharsets.UTF_8));
				return json.isJsonObject() ? json.getAsJsonObject() : null;
			}
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.debug("Update cache unreadable", e);
		}
		return null;
	}

	private static void writeCache(String url, String etag, String body) {
		JsonObject json = new JsonObject();
		json.addProperty("url", url);
		json.addProperty("etag", etag);
		json.addProperty("body", body);
		try {
			Files.createDirectories(DIR);
			Files.writeString(CACHE, json.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.debug("Update cache not written", e);
		}
	}
}
