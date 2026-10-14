package com.hugosmpenhanced.client.feature.auction;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Limited HugoSMP items run out a fixed time after they were made. This finds out when one runs
 * out, from whatever the item carries:
 * <ol>
 * <li>a timestamp in the item's custom data (a key with "created"/"expire" and the like, in seconds or milliseconds),</li>
 * <li>a creation date in the lore ("Erstellt: 10.10.2026 14:30", "Erstellt vor 5 Stunden") plus the lifetime,</li>
 * <li>an end date in the lore ("Verfällt am 13.10.2026 um 14:30 Uhr"),</li>
 * <li>a remaining time in the lore ("Läuft ab in: 2d 4h", "Verbleibend: 1 Tag 3 Stunden", "Restzeit: 47:12:05").</li>
 * </ol>
 * The real lore format on HugoSMP is not known, so all of these are accepted. Lines the auction
 * house adds below an item (price, seller, how long the offer runs) are skipped, so the offer's
 * own end is never taken for the item's. Dates are read as German server time.
 */
public final class LimitedItems {
	static final ZoneId SERVER_ZONE = ZoneId.of("Europe/Berlin");
	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	/** Words that mark an item as limited even before a time is found. */
	private static final Pattern LIMITED = Pattern.compile(
		"limitiert|limited|zeitlich begrenzt|befristet|tempor(ä|ae)r|temporary|verg(ä|ae)nglich|verf(ä|ae)llt|haltbar|expires", FLAGS);
	private static final Pattern CREATED = Pattern.compile(
		"erstellt|hergestellt|gecraftet|gefertigt|erschaffen|created|crafted|ausgestellt|erhalten am|obtained", FLAGS);
	private static final Pattern EXPIRES = Pattern.compile(
		"verf(ä|ae)llt|l(ä|ae)uft (noch )?(ab|aus)|ablauf|g(ü|ue)ltig|haltbar|endet|expires|expiry|expiration|valid until"
			+ "|verbleibend|restzeit|restlaufzeit|(ü|ue)brig|noch|remaining|left|time left", FLAGS);
	/** Lines about the offer itself, not the item. */
	private static final Pattern LISTING = Pattern.compile(
		"angebot|auktion|inserat|listing|auction|order|verk(ä|ae)ufer|seller|anbieter|eingestellt|klick", FLAGS);
	private static final Pattern SEPARATOR = Pattern.compile("^[\\s\\-=_~*·•|▬━─]*$");

	private static final Pattern DATE_DE = Pattern.compile(
		"(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2,4})(?:\\D{1,8}?(\\d{1,2})[:.](\\d{2})(?::(\\d{2}))?)?");
	private static final Pattern DATE_ISO = Pattern.compile(
		"(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ,]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?");
	private static final Pattern DATE_SLASH = Pattern.compile(
		"(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:\\D{1,8}?(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?");
	private static final Pattern DURATION_PART = Pattern.compile(
		"(\\d+(?:[.,]\\d+)?)\\s*(tagen|tage|tag|days|day|t|d|stunden|stunde|std\\.?|hours|hour|hrs|hr|h"
			+ "|minuten|minute|min\\.?|minutes|m|sekunden|sekunde|sek\\.?|seconds|second|sec|s)(?![\\p{L}])", FLAGS);
	private static final Pattern CLOCK = Pattern.compile("(?<![\\d.:])(\\d{1,3}):(\\d{2})(?::(\\d{2}))?(?![\\d.:])");
	private static final Pattern AGO = Pattern.compile("\\bvor\\b|\\bago\\b", FLAGS);
	/** "Verfällt 3 Tage nach der Erstellung" describes the lifetime, it is not a countdown. */
	private static final Pattern DESCRIBES = Pattern.compile("\\bnach\\b|\\bafter\\b|\\bfor\\b|\\bf(ü|ue)r\\b", FLAGS);
	private static final Pattern REMOVED = Pattern.compile(
		"entfernt|gel(ö|oe)scht|verschwindet|zerf(ä|ae)llt|removed|deleted|disappears", FLAGS);
	private static final Pattern RUNS_OUT = Pattern.compile("l(ä|ae)uft|verf(ä|ae)llt|endet|expires", FLAGS);
	private static final Pattern TIME_OF_DAY = Pattern.compile("\\bum\\b|\\buhr\\b|\\bat\\b", FLAGS);

	/** When an item runs out, and where that came from (for tests and the debug tooltip). */
	public record Expiry(long expiresAt, String source) {
		public long remainingMs(long now) {
			return this.expiresAt - now;
		}
	}

	private LimitedItems() {
	}

	/** True if the item looks limited: a limited word, or any creation or end time on it. */
	public static boolean isLimited(ItemStack stack, long lifetimeMs) {
		if (stack.isEmpty()) {
			return false;
		}
		List<String> lines = itemLines(stack);
		for (String line : lines) {
			if (LIMITED.matcher(line).find()) {
				return true;
			}
		}
		return expiry(stack, System.currentTimeMillis(), lifetimeMs).isPresent();
	}

	public static Optional<Expiry> expiry(ItemStack stack, long now, long lifetimeMs) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		List<String> all = loreLines(stack);
		Optional<Expiry> notice = fromRemovalNotice(all, now);
		if (notice.isPresent()) {
			return notice;
		}
		Optional<Expiry> fromLore = fromLore(withoutListingBlock(all), now, lifetimeMs);
		if (fromLore.isPresent()) {
			return fromLore;
		}
		// Data keys are guessed, so a time beyond the lifetime is someone else's (e.g. when the offer was made).
		return fromCustomData(stack.get(DataComponents.CUSTOM_DATA), lifetimeMs)
			.filter(expiry -> expiry.remainingMs(now) <= lifetimeMs + 3_600_000L);
	}

	/**
	 * HugoSMP repeats the item's countdown below the auction block: "Star-Tool läuft ab in: 0d 17h 21m" and
	 * "Achtung: Läuft in 0d 17h 21m ab" / "und wird dann entfernt!". The offer's own "Läuft ab in: 23h 14m"
	 * sits there too, so a line only counts if it names what runs out or says the item gets removed.
	 */
	static Optional<Expiry> fromRemovalNotice(List<String> lines, long now) {
		List<String> joined = joinLabels(lines);
		for (int i = 0; i < joined.size(); i++) {
			String line = joined.get(i).strip();
			Long duration = parseDuration(line);
			if (duration == null || parseDate(line) != null) {
				continue;
			}
			String next = i + 1 < joined.size() ? joined.get(i + 1) : "";
			boolean removed = REMOVED.matcher(line).find() || REMOVED.matcher(next).find();
			Matcher verb = RUNS_OUT.matcher(line);
			boolean named = false;
			if (verb.find()) {
				String before = line.substring(0, verb.start()).replaceAll("[^\\p{L}\\-]", " ").strip();
				named = !before.isEmpty() && !LISTING.matcher(before).find();
			}
			if (removed || named) {
				return Optional.of(new Expiry(now + duration, "restzeit"));
			}
		}
		return Optional.empty();
	}

	/** "Läuft ab in:" with the time on the line below reads as one line. */
	static List<String> joinLabels(List<String> lines) {
		List<String> joined = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (line.endsWith(":") && i + 1 < lines.size() && !lines.get(i + 1).isBlank()
				&& parseDuration(line) == null && parseDate(line) == null) {
				joined.add(line + " " + lines.get(i + 1).strip());
				i++;
			} else {
				joined.add(line);
			}
		}
		return joined;
	}

	/** The lore lines that belong to the item, without the block the auction house adds at the bottom. */
	static List<String> itemLines(ItemStack stack) {
		return withoutListingBlock(loreLines(stack));
	}

	private static List<String> loreLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> lines = new ArrayList<>();
		if (lore != null) {
			for (Component line : lore.lines()) {
				lines.add(line.getString());
			}
		}
		return lines;
	}

	public static List<String> withoutListingBlock(List<String> lines) {
		int end = lines.size();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (AuctionLore.parse(line).isPresent() || isListingHeader(line)) {
				end = i;
				break;
			}
		}
		// The separator line above the auction block belongs to it too.
		while (end > 0 && end < lines.size() && SEPARATOR.matcher(lines.get(end - 1)).matches()) {
			end--;
		}
		return lines.subList(0, end);
	}

	private static boolean isListingHeader(String line) {
		String lower = line.toLowerCase(Locale.ROOT);
		return lower.startsWith("verkäufer") || lower.startsWith("seller") || lower.startsWith("anbieter")
			|| lower.contains("klicke zum kaufen") || lower.contains("click to buy");
	}

	public static Optional<Expiry> fromLore(List<String> rawLines, long now, long lifetimeMs) {
		List<String> lines = joinLabels(rawLines);
		// A creation date is the most reliable: the lifetime is known, a countdown may be stale.
		for (String raw : lines) {
			String line = raw.strip();
			if (!CREATED.matcher(line).find() || LISTING.matcher(line).find()) {
				continue;
			}
			Long date = parseDate(line);
			if (date != null) {
				return Optional.of(new Expiry(date + lifetimeMs, "erstellt"));
			}
			if (AGO.matcher(line).find()) {
				Long duration = parseDuration(line);
				if (duration != null) {
					return Optional.of(new Expiry(now - duration + lifetimeMs, "erstellt vor"));
				}
			}
		}
		for (String raw : lines) {
			String line = raw.strip();
			if (!EXPIRES.matcher(line).find() || CREATED.matcher(line).find() || LISTING.matcher(line).find()) {
				continue;
			}
			Long date = parseDate(line);
			if (date != null) {
				return Optional.of(new Expiry(date, "endet am"));
			}
			if (DESCRIBES.matcher(line).find()) {
				continue;
			}
			if (TIME_OF_DAY.matcher(line).find()) {
				Long at = nextTimeOfDay(line, now);
				if (at != null) {
					return Optional.of(new Expiry(at, "endet um"));
				}
			}
			Long duration = parseDuration(line);
			if (duration != null) {
				boolean expired = line.toLowerCase(Locale.ROOT).contains("abgelaufen") || line.toLowerCase(Locale.ROOT).contains("expired");
				return Optional.of(new Expiry(expired ? now - duration : now + duration, "restzeit"));
			}
		}
		return Optional.empty();
	}

	/** Searches the custom data (also nested, e.g. Paper's PublicBukkitValues) for a creation or end timestamp. */
	static Optional<Expiry> fromCustomData(CustomData data, long lifetimeMs) {
		if (data == null || data.isEmpty()) {
			return Optional.empty();
		}
		long[] found = {0, 0};
		scanTimestamps(data.copyTag(), found, 0);
		if (found[1] > 0) {
			return Optional.of(new Expiry(found[1], "daten: ende"));
		}
		if (found[0] > 0) {
			return Optional.of(new Expiry(found[0] + lifetimeMs, "daten: erstellt"));
		}
		return Optional.empty();
	}

	private static void scanTimestamps(CompoundTag tag, long[] found, int depth) {
		if (depth > 3) {
			return;
		}
		for (String key : tag.keySet()) {
			Tag value = tag.get(key);
			Optional<CompoundTag> nested = value.asCompound();
			if (nested.isPresent()) {
				scanTimestamps(nested.get(), found, depth + 1);
				continue;
			}
			String name = key.toLowerCase(Locale.ROOT);
			if (name.contains("list") || name.contains("auction") || name.contains("offer") || name.contains("sell")
				|| name.contains("bought") || name.contains("price") || name.contains("last")) {
				continue;
			}
			boolean end = name.contains("expir") || name.contains("ablauf") || name.contains("until") || name.contains("verfall")
				|| name.contains("ends") || name.endsWith("end");
			boolean created = name.contains("creat") || name.contains("erstellt") || name.contains("crafted") || name.contains("born")
				|| name.contains("made");
			if (!end && !created) {
				continue;
			}
			Long millis = value.asNumber().map(number -> asEpochMillis(number.longValue()))
				.orElseGet(() -> value.asString().map(text -> {
					try {
						return asEpochMillis(Long.parseLong(text.strip()));
					} catch (NumberFormatException e) {
						return parseDate(text);
					}
				}).orElse(null));
			if (millis == null) {
				continue;
			}
			found[end ? 1 : 0] = millis;
		}
	}

	/** Accepts seconds or milliseconds since 1970 from 2020 to 2100; anything else is not a timestamp. */
	private static Long asEpochMillis(long value) {
		if (value >= 1_577_836_800L && value <= 4_102_444_800L) {
			return value * 1000;
		}
		if (value >= 1_577_836_800_000L && value <= 4_102_444_800_000L) {
			return value;
		}
		return null;
	}

	public static Long parseDate(String line) {
		Matcher m = DATE_DE.matcher(line);
		if (m.find()) {
			int year = Integer.parseInt(m.group(3));
			return toMillis(year < 100 ? 2000 + year : year, m.group(2), m.group(1), m.group(4), m.group(5), m.group(6));
		}
		m = DATE_ISO.matcher(line);
		if (m.find()) {
			return toMillis(Integer.parseInt(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
		}
		m = DATE_SLASH.matcher(line);
		if (m.find()) {
			return toMillis(Integer.parseInt(m.group(3)), m.group(2), m.group(1), m.group(4), m.group(5), m.group(6));
		}
		return null;
	}

	private static Long toMillis(int year, String month, String day, String hour, String minute, String second) {
		try {
			LocalDateTime time = LocalDateTime.of(year, Integer.parseInt(month), Integer.parseInt(day),
				hour == null ? 0 : Integer.parseInt(hour), minute == null ? 0 : Integer.parseInt(minute),
				second == null ? 0 : Integer.parseInt(second));
			return time.atZone(SERVER_ZONE).toInstant().toEpochMilli();
		} catch (DateTimeException | NumberFormatException e) {
			return null;
		}
	}

	/** "um 14:30 Uhr" without a date: the next time the clock shows that. */
	private static Long nextTimeOfDay(String line, long now) {
		Matcher clock = CLOCK.matcher(line);
		if (!clock.find()) {
			return null;
		}
		int hour = Integer.parseInt(clock.group(1));
		int minute = Integer.parseInt(clock.group(2));
		if (hour > 23 || minute > 59) {
			return null;
		}
		var today = java.time.Instant.ofEpochMilli(now).atZone(SERVER_ZONE).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
		long at = today.toInstant().toEpochMilli();
		return at > now ? at : today.plusDays(1).toInstant().toEpochMilli();
	}

	/** "2d 4h 30m", "1 Tag 3 Stunden", "2T 5Std", "47:12:05" or "12:30" (hours and minutes) in milliseconds. */
	static Long parseDuration(String line) {
		Matcher m = DURATION_PART.matcher(line);
		double total = 0;
		boolean any = false;
		while (m.find()) {
			double amount;
			try {
				amount = Double.parseDouble(m.group(1).replace(',', '.'));
			} catch (NumberFormatException e) {
				continue;
			}
			String unit = m.group(2).toLowerCase(Locale.ROOT);
			double factor;
			if (unit.startsWith("t") || unit.startsWith("d")) {
				factor = 86_400_000;
			} else if (unit.startsWith("st") || unit.startsWith("h")) {
				factor = 3_600_000;
			} else if (unit.startsWith("m")) {
				factor = 60_000;
			} else {
				factor = 1000;
			}
			total += amount * factor;
			any = true;
		}
		Matcher clock = CLOCK.matcher(line);
		if (clock.find()) {
			long h = Long.parseLong(clock.group(1));
			long min = Long.parseLong(clock.group(2));
			long s = clock.group(3) == null ? 0 : Long.parseLong(clock.group(3));
			total += (h * 3600 + min * 60 + s) * 1000.0;
			any = true;
		}
		return any ? (long) total : null;
	}

	/** "2T 4Std", "4Std 12Min", "12Min". */
	public static String formatRemaining(long ms) {
		if (ms <= 0) {
			return "abgelaufen";
		}
		long minutes = Math.max(1, ms / 60_000);
		long days = minutes / 1440;
		long hours = minutes % 1440 / 60;
		long mins = minutes % 60;
		if (days > 0) {
			return days + "T " + hours + "Std";
		}
		if (hours > 0) {
			return hours + "Std " + mins + "Min";
		}
		return mins + "Min";
	}
}
