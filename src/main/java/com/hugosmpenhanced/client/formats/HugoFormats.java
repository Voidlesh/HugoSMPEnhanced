package com.hugosmpenhanced.client.formats;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.feature.streamer.Money;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * HugoSMP's texts the mod reads (payment lines, menu titles, order and spawner lore), kept in one
 * JSON file instead of the code, like SkyHanni's repository. The copy inside the jar is the fallback;
 * at start and every hour the file is loaded from GitHub, so a changed server text is fixed for every
 * player by editing that file, without a new release. The last good download is kept on disk.
 * <p>
 * A newer download only replaces the keys it contains, and only with patterns that compile, so a
 * mistake in the online file can never break a key that worked before.
 */
public final class HugoFormats {
	public static final String REMOTE_URL =
		"https://raw.githubusercontent.com/Voidlesh/HugoSMPEnhanced/main/src/main/resources/assets/hugosmpenhanced/formats/hugosmp.json";
	public static final String EDIT_URL =
		"https://github.com/Voidlesh/HugoSMPEnhanced/blob/main/src/main/resources/assets/hugosmpenhanced/formats/hugosmp.json";
	private static final String BUNDLED = "/assets/hugosmpenhanced/formats/hugosmp.json";
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced").resolve("formats.json");
	private static final long REFRESH_MS = 60 * 60_000L;
	private static final long RETRY_MS = 5 * 60_000L;
	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL).build();

	public static final String NAME = "(?<name>[A-Za-z0-9_]{3,16})";
	public static final String AMOUNT = "(?<amount>" + Money.AMOUNT_REGEX + ")";

	public enum Source {
		BUNDLED("in der Mod"), CACHE("gespeichert"), ONLINE("online");

		public final String label;

		Source(String label) {
			this.label = label;
		}
	}

	/** One loaded set of formats. */
	private record Data(int version, Map<String, List<Pattern>> patterns, Map<String, List<String>> words) {
	}

	private static volatile Data data = new Data(0, Map.of(), Map.of());
	private static volatile Source source = Source.BUNDLED;
	private static volatile long loadedAt;
	private static volatile String lastError;
	private static volatile boolean fetching;
	private static long nextFetchAt;

	private HugoFormats() {
	}

	public static void register() {
		Data bundled = readBundled();
		data = bundled;
		Data cached = readCache();
		if (cached != null && cached.version() >= bundled.version()) {
			data = merge(bundled, cached);
			source = Source.CACHE;
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!fetching && Util.getMillis() >= nextFetchAt && !Boolean.getBoolean("hugosmpenhanced.offlineFormats")) {
				refresh();
			}
		});
	}

	// ------------------------------------------------------------------ lookups

	/** The compiled patterns for a key, in order; empty when the key is unknown. */
	public static List<Pattern> patterns(String key) {
		return data.patterns().getOrDefault(key, List.of());
	}

	/** The words for a key, lower case. */
	public static List<String> words(String key) {
		return data.words().getOrDefault(key, List.of());
	}

	/** The first pattern of a key that finds something in the text, as a matcher; null if none. */
	public static Matcher find(String key, String text) {
		if (text == null) {
			return null;
		}
		for (Pattern pattern : patterns(key)) {
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) {
				return matcher;
			}
		}
		return null;
	}

	/** The first pattern of a key that matches the whole text; null if none. */
	public static Matcher matchWhole(String key, String text) {
		if (text == null) {
			return null;
		}
		for (Pattern pattern : patterns(key)) {
			Matcher matcher = pattern.matcher(text);
			if (matcher.matches()) {
				return matcher;
			}
		}
		return null;
	}

	/** Whether one of the key's words stands as a whole word in the text (a menu title, a chat line). */
	public static boolean containsWord(String key, String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		for (String word : words(key)) {
			int at = lower.indexOf(word);
			while (at >= 0) {
				boolean startOk = at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1));
				int end = at + word.length();
				boolean endOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
				if (startOk && endOk) {
					return true;
				}
				at = lower.indexOf(word, at + 1);
			}
		}
		return false;
	}

	/** A group's value, or null if the pattern has no such group or it did not take part. */
	public static String group(Matcher matcher, String name) {
		try {
			return matcher.group(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ status

	public static int version() {
		return data.version();
	}

	public static Source source() {
		return source;
	}

	public static long loadedAt() {
		return loadedAt;
	}

	public static String lastError() {
		return lastError;
	}

	public static boolean fetching() {
		return fetching;
	}

	/** "Version 3, online vor 5 Min." for the settings. */
	public static String describe() {
		String text = "Version " + version() + ", " + source.label;
		if (source == Source.ONLINE && loadedAt > 0) {
			long minutes = (System.currentTimeMillis() - loadedAt) / 60_000;
			text += minutes < 1 ? " gerade eben" : " vor " + minutes + " Min.";
		}
		if (fetching) {
			text += ", lädt…";
		} else if (lastError != null) {
			text += " (online nicht erreichbar)";
		}
		return text;
	}

	// ------------------------------------------------------------------ loading

	/** Loads the online file now in the background, unless a load already runs. */
	public static CompletableFuture<Boolean> refresh() {
		if (fetching) {
			return CompletableFuture.completedFuture(false);
		}
		fetching = true;
		return CompletableFuture.supplyAsync(HugoFormats::fetch, Util.ioPool()).whenComplete((ok, error) -> {
			fetching = false;
			nextFetchAt = Util.getMillis() + (Boolean.TRUE.equals(ok) ? REFRESH_MS : RETRY_MS);
		});
	}

	private static boolean fetch() {
		String url = System.getProperty("hugosmpenhanced.formatsUrl", REMOTE_URL);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
				.header("User-Agent", "HugoSMPEnhanced").GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			Data online = parse(JsonParser.parseString(response.body()));
			if (online.version() < readBundled().version()) {
				// An old online file must not undo what a newer jar knows.
				lastError = null;
				return true;
			}
			data = merge(readBundled(), online);
			source = Source.ONLINE;
			loadedAt = System.currentTimeMillis();
			lastError = null;
			Files.createDirectories(CACHE.getParent());
			Files.writeString(CACHE, response.body(), StandardCharsets.UTF_8);
			return true;
		} catch (Exception e) {
			lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			HugoSmpEnhancedClient.LOGGER.warn("HugoSMP formats could not be loaded from {}: {}", url, e.toString());
			return false;
		}
	}

	private static Data readBundled() {
		try (InputStream in = HugoFormats.class.getResourceAsStream(BUNDLED)) {
			if (in == null) {
				return new Data(0, Map.of(), Map.of());
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader));
			}
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.error("Bundled HugoSMP formats are broken", e);
			return new Data(0, Map.of(), Map.of());
		}
	}

	private static Data readCache() {
		if (!Files.exists(CACHE)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(CACHE, StandardCharsets.UTF_8)) {
			return parse(JsonParser.parseReader(reader));
		} catch (Exception e) {
			return null;
		}
	}

	/** The base with every key the newer file has replaced by the newer file's list. */
	private static Data merge(Data base, Data newer) {
		Map<String, List<Pattern>> patterns = new HashMap<>(base.patterns());
		newer.patterns().forEach((key, list) -> {
			if (!list.isEmpty()) {
				patterns.put(key, list);
			}
		});
		Map<String, List<String>> words = new HashMap<>(base.words());
		newer.words().forEach((key, list) -> {
			if (!list.isEmpty()) {
				words.put(key, list);
			}
		});
		return new Data(Math.max(base.version(), newer.version()), Map.copyOf(patterns), Map.copyOf(words));
	}

	/** Reads one formats file; broken patterns are left out (and logged), the rest is kept. */
	static Data parse(JsonElement root) {
		JsonObject body = root.getAsJsonObject();
		int version = body.has("version") ? body.get("version").getAsInt() : 0;
		Map<String, List<Pattern>> patterns = new HashMap<>();
		if (body.has("patterns") && body.get("patterns").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : body.getAsJsonObject("patterns").entrySet()) {
				List<Pattern> list = new ArrayList<>();
				if (entry.getValue().isJsonArray()) {
					for (JsonElement element : entry.getValue().getAsJsonArray()) {
						Pattern pattern = compile(element.getAsString());
						if (pattern != null) {
							list.add(pattern);
						}
					}
				}
				patterns.put(entry.getKey(), List.copyOf(list));
			}
		}
		Map<String, List<String>> words = new HashMap<>();
		if (body.has("words") && body.get("words").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : body.getAsJsonObject("words").entrySet()) {
				List<String> list = new ArrayList<>();
				if (entry.getValue().isJsonArray()) {
					for (JsonElement element : entry.getValue().getAsJsonArray()) {
						String word = element.getAsString().strip().toLowerCase(Locale.ROOT);
						if (!word.isEmpty()) {
							list.add(word);
						}
					}
				}
				words.put(entry.getKey(), List.copyOf(list));
			}
		}
		return new Data(version, Map.copyOf(patterns), Map.copyOf(words));
	}

	/** Compiles one pattern with its placeholders filled in; null (logged) when it is broken. */
	public static Pattern compile(String source) {
		String regex = source.replace("{name}", NAME).replace("{amount}", AMOUNT).replace("{count}", "(?<count>\\d[\\d.,]*)");
		try {
			return Pattern.compile(regex, FLAGS);
		} catch (PatternSyntaxException e) {
			HugoSmpEnhancedClient.LOGGER.warn("Skipping broken HugoSMP format pattern {}: {}", source, e.getDescription());
			return null;
		}
	}

	/** Plain text without colour codes and odd spaces, as the patterns expect it. */
	public static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("§.", "").replace(' ', ' ').replaceAll("\\s+", " ").strip();
	}
}
