package com.hugosmpenhanced.client.feature.spawnerprices;

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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * VOID Market's spawner buy and sell prices, from the public list on hugosmptools.com/trading
 * (voidbot pushes them there). Other servers on that list are ignored on purpose. Refreshed every five
 * minutes while on HugoSMP; the last answer is kept on disk, so tooltips have prices right after
 * joining and while the site is down.
 *
 * <p>Wording as on the site: {@code buy} (Ankauf) is what a server pays you for a spawner,
 * {@code sell} (Verkauf) is what it charges you.
 */
public final class SpawnerPrices {
	public static final String SITE = "https://hugosmptools.com/trading";
	/** The only server whose prices the mod shows. */
	public static final String SERVER = "VOID Market";
	private static final String DEFAULT_API = "https://hugosmptools.com/api/public/trading";
	private static final String USER_AGENT = "Mozilla/5.0 (compatible; HugoSMPEnhanced/1.0; +https://github.com/Voidlesh/HugoSMPEnhanced)";
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced-spawner-prices.json");
	private static final long REFRESH_MS = 5 * 60 * 1000L;
	private static final long RETRY_MS = 60 * 1000L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	/** One offer direction; null price = no offer. */
	public record Offer(String mobId, Long buy, Long sell) {
	}

	public record Server(String id, String name, String invite, int members, boolean verified, String note, List<Offer> offers) {
		public Offer offer(SpawnerMob mob) {
			for (Offer offer : this.offers) {
				if (offer.mobId().equals(mob.id)) {
					return offer;
				}
			}
			return null;
		}

		public Long buy(SpawnerMob mob) {
			Offer offer = this.offer(mob);
			return offer != null ? offer.buy() : null;
		}

		public Long sell(SpawnerMob mob) {
			Offer offer = this.offer(mob);
			return offer != null ? offer.sell() : null;
		}
	}

	/** VOID Market's entry, or a null server if the list no longer has it. */
	public record Snapshot(Server server, long fetchedAt) {
	}

	public enum State {
		/** Nothing loaded yet and nothing on disk. */
		EMPTY, LOADING, OK, FAILED
	}

	private static volatile Snapshot snapshot;
	private static volatile State state = State.EMPTY;
	private static volatile String lastError;
	private static long nextFetchAt;
	private static volatile boolean fetching;
	/** Lets game tests use the prices outside HugoSMP. */
	public static boolean forceActive;

	private SpawnerPrices() {
	}

	public static void register() {
		snapshot = readCache();
		if (snapshot != null) {
			state = State.OK;
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!active() || fetching) {
				return;
			}
			if (Util.getMillis() >= nextFetchAt) {
				refresh();
			}
		});
	}

	/** Whether prices are shown at all: only on HugoSMP, where they mean something. */
	public static boolean active() {
		return forceActive || ServerCheck.isHugoSmp();
	}

	/** Loads the prices now in the background, unless a load is already running. */
	public static CompletableFuture<Boolean> refresh() {
		if (fetching) {
			return CompletableFuture.completedFuture(false);
		}
		fetching = true;
		if (snapshot == null) {
			state = State.LOADING;
		}
		return CompletableFuture.supplyAsync(SpawnerPrices::fetch, Util.ioPool()).whenComplete((ok, error) -> {
			fetching = false;
			boolean success = error == null && Boolean.TRUE.equals(ok);
			nextFetchAt = Util.getMillis() + (success ? REFRESH_MS : RETRY_MS);
			if (!success && snapshot == null) {
				state = State.FAILED;
			}
		});
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static State state() {
		return state;
	}

	public static boolean loading() {
		return fetching;
	}

	/** Why the last load failed, for the overview; null after a successful load. */
	public static String lastError() {
		return lastError;
	}

	/** VOID Market's prices, or null before the first load or if it is not listed. */
	public static Server voidMarket() {
		Snapshot current = snapshot;
		return current != null ? current.server() : null;
	}

	private static boolean fetch() {
		String url = System.getProperty("hugosmpenhanced.spawnerPricesUrl", DEFAULT_API);
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
			Snapshot parsed = parse(JsonParser.parseString(response.body()), System.currentTimeMillis());
			snapshot = parsed;
			state = State.OK;
			lastError = null;
			writeCache(response.body(), parsed.fetchedAt());
			return true;
		} catch (Exception e) {
			lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			HugoSmpEnhancedClient.LOGGER.warn("Spawner prices could not be loaded from {}: {}", url, e.toString());
			return false;
		}
	}

	/** Reads the {@code /api/public/trading} answer; throws if it is not one. */
	public static Snapshot parse(JsonElement root, long fetchedAt) {
		JsonObject body = root.getAsJsonObject();
		if (!body.has("ok") || !body.get("ok").getAsBoolean() || !body.has("servers") || !body.get("servers").isJsonArray()) {
			throw new IllegalStateException("unexpected answer");
		}
		for (JsonElement element : body.getAsJsonArray("servers")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject server = element.getAsJsonObject();
			String id = string(server, "id");
			String name = string(server, "name");
			if (id == null || name == null || !normalize(name).equals(normalize(SERVER))) {
				continue;
			}
			List<Offer> offers = new ArrayList<>();
			JsonArray rawOffers = server.has("offers") && server.get("offers").isJsonArray() ? server.getAsJsonArray("offers") : new JsonArray();
			for (JsonElement rawOffer : rawOffers) {
				if (rawOffer.isJsonObject() && string(rawOffer.getAsJsonObject(), "mobId") != null) {
					JsonObject offer = rawOffer.getAsJsonObject();
					offers.add(new Offer(string(offer, "mobId"), price(offer, "buy"), price(offer, "sell")));
				}
			}
			String invite = string(server, "invite");
			offers = List.copyOf(offers);
			return new Snapshot(new Server(id, name, invite != null && invite.startsWith("https://") ? invite : null,
				server.has("members") && server.get("members").isJsonPrimitive() ? server.get("members").getAsInt() : 0,
				server.has("verified") && server.get("verified").isJsonPrimitive() && server.get("verified").getAsBoolean(),
				string(server, "note"), offers), fetchedAt);
		}
		return new Snapshot(null, fetchedAt);
	}

	private static Snapshot readCache() {
		if (!Files.exists(CACHE)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(CACHE, StandardCharsets.UTF_8)) {
			JsonObject cached = JsonParser.parseReader(reader).getAsJsonObject();
			return parse(cached.get("response"), cached.get("fetchedAt").getAsLong());
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Cached spawner prices could not be read: {}", e.toString());
			return null;
		}
	}

	private static void writeCache(String response, long fetchedAt) {
		JsonObject cached = new JsonObject();
		cached.addProperty("fetchedAt", fetchedAt);
		cached.add("response", JsonParser.parseString(response));
		try (Writer writer = Files.newBufferedWriter(CACHE, StandardCharsets.UTF_8)) {
			writer.write(cached.toString());
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Spawner prices could not be cached: {}", e.toString());
		}
	}

	/** "12,5 Mio", "750.000", "5 Mio": the way the trading page writes spawner prices. */
	public static String format(long price) {
		if (price >= 1_000_000) {
			double millions = price / 1_000_000.0;
			String number = millions == Math.rint(millions) ? String.format(Locale.GERMANY, "%,.0f", millions)
				: millions * 10 == Math.rint(millions * 10) ? String.format(Locale.GERMANY, "%,.1f", millions)
				: String.format(Locale.GERMANY, "%,.2f", millions);
			return "$" + number + " Mio";
		}
		return String.format(Locale.GERMANY, "$%,d", price);
	}

	/** "gerade eben", "vor 4 Min.", "vor 2 Std.", "vor 3 Tagen". */
	public static String age(long at) {
		long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
		if (minutes < 1) {
			return "gerade eben";
		}
		if (minutes < 60) {
			return "vor " + minutes + " Min.";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return "vor " + hours + " Std.";
		}
		long days = hours / 24;
		return days == 1 ? "vor 1 Tag" : "vor " + days + " Tagen";
	}

	private static String normalize(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static Long price(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double price = value.getAsDouble();
		return Double.isFinite(price) && price > 0 ? Math.round(price) : null;
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
