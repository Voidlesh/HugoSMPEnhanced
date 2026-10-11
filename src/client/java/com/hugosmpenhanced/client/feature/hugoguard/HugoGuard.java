package com.hugosmpenhanced.client.feature.hugoguard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.Util;

/**
 * Keeps the lists of players HugoGuard (hugoguard.net) has marked as scammers or as trusted. Both
 * lists are fetched page by page from the public, keyless API while connected to HugoSMP and refreshed every
 * ten minutes, so seeing a player never triggers a request of its own. Uses the {@code /api/public}
 * path: the plain {@code /public} path sits behind a Cloudflare challenge (as in the voidbot
 * client).
 */
public final class HugoGuard {
	private static final String API = "https://hugoguard.net/api/public/v1";
	private static final String USER_AGENT = "Mozilla/5.0 (compatible; HugoSMPEnhanced/1.0; +https://github.com/Voidlesh/HugoSMPEnhanced)";
	private static final int PAGE_SIZE = 48;
	private static final int MAX_PAGES = 50;
	private static final long REFRESH_MS = 10 * 60 * 1000L;
	private static final long RETRY_MS = 60 * 1000L;

	/** Which HugoGuard list a player is on. */
	public enum Status {
		SCAMMER, TRUSTED
	}

	/** One listed player: name, list, report count, reputation and the admin label HugoGuard shows, if any. */
	public record Entry(String username, UUID uuid, Status status, int reports, int repScore, String label) {
		public String profileUrl() {
			return "https://hugoguard.net/players/" + this.uuid.toString().replace("-", "");
		}
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private static volatile Map<UUID, Entry> byUuid = Map.of();
	private static volatile Map<String, Entry> byName = Map.of();
	/** Lets game tests use the list outside HugoSMP. */
	public static boolean forceActive;
	private static long nextFetchAt;
	private static boolean fetching;

	private HugoGuard() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!ModConfigManager.CONFIG.hugoGuardEnabled || !ServerCheck.isHugoSmp() || fetching) {
				return;
			}
			long now = Util.getMillis();
			if (now >= nextFetchAt) {
				fetching = true;
				CompletableFuture.supplyAsync(HugoGuard::fetchAll, Util.ioPool()).whenCompleteAsync((ok, error) -> {
					fetching = false;
					nextFetchAt = Util.getMillis() + (error == null && ok ? REFRESH_MS : RETRY_MS);
				}, client);
			}
		});
	}

	/** Whether checks are on and we are on HugoSMP; every lookup below returns nothing otherwise. */
	public static boolean active() {
		return ModConfigManager.CONFIG.hugoGuardEnabled && (forceActive || ServerCheck.isHugoSmp());
	}

	/** The player's entry on either list, or null. */
	public static Entry lookup(UUID uuid) {
		return active() && uuid != null ? byUuid.get(uuid) : null;
	}

	/** The player's entry on either list, or null. */
	public static Entry lookup(String name) {
		return active() && name != null ? byName.get(name.toLowerCase(Locale.ROOT)) : null;
	}

	public static Entry scammer(UUID uuid) {
		return only(lookup(uuid), Status.SCAMMER);
	}

	public static Entry scammer(String name) {
		return only(lookup(name), Status.SCAMMER);
	}

	public static Entry trusted(UUID uuid) {
		return only(lookup(uuid), Status.TRUSTED);
	}

	public static Entry trusted(String name) {
		return only(lookup(name), Status.TRUSTED);
	}

	/** The entry to mark the player with: scammers always, trusted players only if that is turned on. */
	public static Entry shown(UUID uuid) {
		Entry entry = lookup(uuid);
		return entry != null && (entry.status() == Status.SCAMMER || ModConfigManager.CONFIG.hugoGuardTrusted) ? entry : null;
	}

	/** Same as {@link #shown(UUID)}, by name. */
	public static Entry shown(String name) {
		Entry entry = lookup(name);
		return entry != null && (entry.status() == Status.SCAMMER || ModConfigManager.CONFIG.hugoGuardTrusted) ? entry : null;
	}

	private static Entry only(Entry entry, Status status) {
		return entry != null && entry.status() == status ? entry : null;
	}

	/** Loads the whole list now, blocking; returns false if it could not be loaded. */
	public static boolean fetchAll() {
		Map<UUID, Entry> uuids = new HashMap<>();
		Map<String, Entry> names = new HashMap<>();
		try {
			// Trusted first, so a player somehow on both lists ends up marked as scammer.
			for (Status status : new Status[] {Status.TRUSTED, Status.SCAMMER}) {
				for (int page = 1; page <= MAX_PAGES; page++) {
					JsonObject body = get(API + "/players?status=" + status + "&page=" + page + "&pageSize=" + PAGE_SIZE);
					JsonArray results = body.has("results") && body.get("results").isJsonArray() ? body.getAsJsonArray("results") : new JsonArray();
					for (JsonElement element : results) {
						Entry entry = parse(element.getAsJsonObject(), status);
						if (entry != null) {
							uuids.put(entry.uuid(), entry);
							names.put(entry.username().toLowerCase(Locale.ROOT), entry);
						}
					}
					int total = body.has("total") ? body.get("total").getAsInt() : 0;
					if (results.isEmpty() || page * PAGE_SIZE >= total) {
						break;
					}
				}
			}
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("HugoGuard scammer list could not be loaded: {}", e.toString());
			return false;
		}
		byUuid = Map.copyOf(uuids);
		byName = Map.copyOf(names);
		HugoSmpEnhancedClient.LOGGER.info("HugoGuard: {} listed players loaded", uuids.size());
		return true;
	}

	/**
	 * Everything HugoGuard knows about one player ({@code player}, {@code reports}, ...), blocking.
	 * Returns null if HugoGuard does not know the name; throws if the request fails.
	 */
	public static JsonObject fetchPlayer(String name) throws Exception {
		String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
		try {
			return get(API + "/players/by-username/" + encoded);
		} catch (IllegalStateException e) {
			if ("HTTP 404".equals(e.getMessage())) {
				return null;
			}
			throw e;
		}
	}

	private static JsonObject get(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(10))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET().build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + response.statusCode());
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private static Entry parse(JsonObject player, Status status) {
		String name = string(player, "username");
		String rawUuid = string(player, "uuid");
		if (name == null || rawUuid == null || rawUuid.replace("-", "").length() != 32) {
			return null;
		}
		String hex = rawUuid.replace("-", "");
		UUID uuid = new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
		int reports = player.has("approvedReportCount") && player.get("approvedReportCount").isJsonPrimitive()
			? player.get("approvedReportCount").getAsInt() : 0;
		int repScore = player.has("repScore") && player.get("repScore").isJsonPrimitive() ? player.get("repScore").getAsInt() : 0;
		return new Entry(name, uuid, status, reports, repScore, string(player, "adminLabel"));
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
