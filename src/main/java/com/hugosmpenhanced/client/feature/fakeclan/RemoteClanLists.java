package com.hugosmpenhanced.client.feature.fakeclan;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * The real and fake clan lists as the admins keep them on the Voidbot (api.voidlesh.de/v1/fake-clans), so
 * new fakes reach everyone without a new mod version. Loaded when first on HugoSMP and then every hour;
 * the last good answer is kept on disk. The lists in the config stay in force and are added to, so the
 * warning also works offline and with a blocked server.
 *
 * <p>A bad answer never replaces the stored lists: not a 200, not the expected JSON, or both lists empty
 * while lists are stored (an empty or half-written file on the server must not switch the warning off).
 */
public final class RemoteClanLists {
	private static final String DEFAULT_API = "https://api.voidlesh.de/v1/fake-clans";
	private static final String USER_AGENT = "Mozilla/5.0 (compatible; HugoSMPEnhanced/1.0; +https://github.com/Voidlesh/HugoSMPEnhanced)";
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced-clans.json");
	private static final long REFRESH_MS = 60 * 60 * 1000L;
	private static final long RETRY_MS = 5 * 60 * 1000L;
	private static final int MAX_TAGS = 2000;
	private static final int MAX_TAG_LENGTH = 32;
	private static final int MAX_BYTES = 256 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	/** The clan lists of one answer; both lists are never null. */
	public record Lists(List<String> real, List<String> fake, long fetchedAt) {
		boolean isEmpty() {
			return this.real.isEmpty() && this.fake.isEmpty();
		}
	}

	private static volatile Lists lists;
	private static volatile String lastError;
	private static volatile boolean fetching;
	private static long nextFetchAt;
	/** Lets game tests load the lists outside HugoSMP. */
	public static boolean forceActive;

	private RemoteClanLists() {
	}

	public static void register() {
		lists = readCache();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (fetching || !active() || Util.getMillis() < nextFetchAt) {
				return;
			}
			refresh();
		});
	}

	private static boolean active() {
		return (forceActive || ServerCheck.isHugoSmp()) && ModConfigManager.CONFIG.clanListsRemote;
	}

	/** Loads the lists now in the background, unless a load is already running. */
	public static CompletableFuture<Boolean> refresh() {
		if (fetching) {
			return CompletableFuture.completedFuture(false);
		}
		fetching = true;
		return CompletableFuture.supplyAsync(() -> fetch(System.getProperty("hugosmpenhanced.clanListsUrl", DEFAULT_API)), Util.ioPool())
			.whenComplete((ok, error) -> {
				fetching = false;
				nextFetchAt = Util.getMillis() + (error == null && Boolean.TRUE.equals(ok) ? REFRESH_MS : RETRY_MS);
			});
	}

	/** The stored remote lists, or null before the first good answer. */
	public static Lists lists() {
		return lists;
	}

	/** Why the last load failed; null after a successful one. */
	public static String lastError() {
		return lastError;
	}

	/** Real clans: the config's plus the remote ones, no duplicates (ignoring case). */
	public static List<String> real(List<String> local) {
		Lists current = lists;
		return merge(local, current != null ? current.real() : List.of());
	}

	/** Fake clans: the config's plus the remote ones, no duplicates (ignoring case). */
	public static List<String> fake(List<String> local) {
		Lists current = lists;
		return merge(local, current != null ? current.fake() : List.of());
	}

	private static List<String> merge(List<String> local, List<String> remote) {
		if (remote.isEmpty()) {
			return local;
		}
		Map<String, String> merged = new LinkedHashMap<>();
		for (String tag : local) {
			if (tag != null) {
				merged.putIfAbsent(tag.toLowerCase(java.util.Locale.ROOT), tag);
			}
		}
		for (String tag : remote) {
			merged.putIfAbsent(tag.toLowerCase(java.util.Locale.ROOT), tag);
		}
		return List.copyOf(merged.values());
	}

	/** Loads {@code url} synchronously and stores the answer if it is a good one. */
	public static boolean fetch(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			if (response.body().length() > MAX_BYTES) {
				throw new IllegalStateException("answer too large");
			}
			Lists parsed = parse(JsonParser.parseString(response.body()), System.currentTimeMillis());
			Lists stored = lists;
			if (parsed.isEmpty() && stored != null && !stored.isEmpty()) {
				throw new IllegalStateException("empty answer ignored");
			}
			lists = parsed;
			lastError = null;
			writeCache(parsed);
			return true;
		} catch (Exception e) {
			lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			HugoSmpEnhancedClient.LOGGER.warn("Clan lists could not be loaded from {}: {}", url, e.toString());
			return false;
		}
	}

	/** Reads a {@code /v1/fake-clans} answer; throws if it is not one. */
	public static Lists parse(JsonElement root, long fetchedAt) {
		JsonObject body = root.getAsJsonObject();
		if (!body.has("ok") || !body.get("ok").getAsBoolean()) {
			throw new IllegalStateException("unexpected answer");
		}
		return new Lists(tags(body, "real"), tags(body, "fake"), fetchedAt);
	}

	private static List<String> tags(JsonObject body, String key) {
		JsonElement array = body.get(key);
		if (array == null || !array.isJsonArray()) {
			throw new IllegalStateException("\"" + key + "\" missing");
		}
		List<String> tags = new ArrayList<>();
		for (JsonElement entry : (JsonArray) array) {
			if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				continue;
			}
			String tag = entry.getAsString().trim();
			if (!tag.isEmpty() && tag.length() <= MAX_TAG_LENGTH && tags.size() < MAX_TAGS) {
				tags.add(tag);
			}
		}
		return List.copyOf(tags);
	}

	private static Lists readCache() {
		if (!Files.exists(CACHE)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(CACHE, StandardCharsets.UTF_8)) {
			JsonObject cached = JsonParser.parseReader(reader).getAsJsonObject();
			return parse(cached.get("response"), cached.get("fetchedAt").getAsLong());
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Cached clan lists could not be read: {}", e.toString());
			return null;
		}
	}

	/** Written to a temp file first so a crash mid-write cannot leave half a cache behind. */
	private static void writeCache(Lists current) {
		JsonObject response = new JsonObject();
		response.addProperty("ok", true);
		response.add("real", toArray(current.real()));
		response.add("fake", toArray(current.fake()));
		JsonObject cached = new JsonObject();
		cached.addProperty("fetchedAt", current.fetchedAt());
		cached.add("response", response);
		Path temp = CACHE.resolveSibling(CACHE.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			writer.write(cached.toString());
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Clan lists could not be cached: {}", e.toString());
			return;
		}
		try {
			Files.move(temp, CACHE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Clan lists could not be cached: {}", e.toString());
		}
	}

	private static JsonArray toArray(List<String> tags) {
		JsonArray array = new JsonArray();
		tags.forEach(array::add);
		return array;
	}

	/** Test hook: forget the stored lists, or load them again from the cache file. */
	public static void resetForTest(boolean reloadCache) {
		lists = reloadCache ? readCache() : null;
		lastError = null;
	}
}
