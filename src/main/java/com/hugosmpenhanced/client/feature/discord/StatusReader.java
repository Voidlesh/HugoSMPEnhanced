package com.hugosmpenhanced.client.feature.discord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.auction.AuctionLore;
import com.hugosmpenhanced.client.mixin.PlayerTabOverlayAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads what the presence shows from what HugoSMP sends anyway: the sidebar scoreboard, the tab
 * list header and footer, the world name, chat answers like "Kontostand: $1.234" and the
 * vanilla statistics. Nothing is asked from the server except the statistics, the same request
 * the vanilla statistics screen sends.
 */
public final class StatusReader {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern NUMBER = Pattern.compile("(\\d[\\d.,]*)\\s*(mrd|mio|tsd|[kmb](?![a-zäöü]))?", Pattern.CASE_INSENSITIVE);
	private static final Pattern MONEY_LABEL = Pattern.compile("(geld|kontostand|guthaben|money|balance|coins|münzen|vermögen|konto|bank|\\$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern KILLS_LABEL = Pattern.compile("(?<![a-z])(kills?|getötet|abschüsse)(?![a-z])", Pattern.CASE_INSENSITIVE);
	private static final Pattern DEATHS_LABEL = Pattern.compile("(?<![a-z])(tode|tod|deaths?|gestorben)(?![a-z])", Pattern.CASE_INSENSITIVE);
	private static final Pattern KD_LABEL = Pattern.compile("(?<![a-z])(k/d|kd|kdr)(?![a-z])", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLACE_LABEL = Pattern.compile("^\\W*(server|welt|world|modus|mode|bereich|ort|realm|zone)\\s*[:»>|\\-]+\\s*(.*)$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern CHAT_BALANCE = Pattern.compile("(kontostand|guthaben|balance|dein geld|du hast)[^\\d$]{0,24}(\\$?\\s*\\d[\\d.,]*\\s*(?:mrd|mio|tsd|[kmb])?\\s*\\$?)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern HUGOSMP = Pattern.compile("hugo\\s*smp(\\.net)?", Pattern.CASE_INSENSITIVE);
	private static final long STATS_INTERVAL = 120_000;
	private static final double TELEPORT_DISTANCE = 32;
	private static final long HINT_WINDOW = 15_000;

	/** One look at the game, with where each value came from (shown in the settings for checking). */
	public record Reading(String location, String locationSource, Double money, String moneySource, Integer kills,
		String killsSource, Integer deaths, String deathsSource, Double kd, int online, String sidebarTitle,
		List<String> sidebar, String tabHeader, String tabFooter, String world) {
		static final Reading EMPTY = new Reading(null, null, null, null, null, null, null, null, null, 0, "", List.of(), "", "", "");
	}

	private static Reading last = Reading.EMPTY;
	private static String lastLocation;
	private static long locationSince = System.currentTimeMillis();
	private static Double chatMoney;
	private static long lastStatsRequest;
	private static Vec3 lastPosition;
	private static Level lastLevel;
	private static String pendingHint;
	private static long pendingHintAt;
	private static String commandLocation;

	private StatusReader() {
	}

	public static Reading last() {
		return last;
	}

	/** When the shown location last changed, for "time since entering". */
	public static long locationSince() {
		return locationSince;
	}

	static void reset() {
		last = Reading.EMPTY;
		lastLocation = null;
		chatMoney = null;
		lastStatsRequest = 0;
		lastPosition = null;
		lastLevel = null;
		pendingHint = null;
		commandLocation = null;
	}

	// ------------------------------------------------------------------ events

	/** A command the player sent, e.g. "afk" or "spawn": a hint for the next teleport. */
	static void onCommand(String command) {
		String word = command.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
		String name = matchRule(word);
		if (name != null) {
			pendingHint = name;
			pendingHintAt = System.currentTimeMillis();
		}
	}

	static void onGameMessage(Component message) {
		Matcher matcher = CHAT_BALANCE.matcher(clean(message.getString()));
		if (matcher.find()) {
			Double amount = parseNumber(matcher.group(2));
			if (amount != null) {
				chatMoney = amount;
			}
		}
	}

	// ------------------------------------------------------------------ reading

	public static Reading read(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || client.getConnection() == null) {
			return last = Reading.EMPTY;
		}
		ModConfig config = ModConfigManager.CONFIG;
		trackTeleports(player, level);

		Scoreboard scoreboard = level.getScoreboard();
		Objective objective = sidebarObjective(scoreboard, player);
		String title = objective == null ? "" : clean(objective.getDisplayName().getString());
		List<String> sidebar = objective == null ? List.of() : sidebarLines(scoreboard, objective);
		PlayerTabOverlayAccessor tab = (PlayerTabOverlayAccessor) client.gui.getTabList();
		String header = tab.hugosmpenhanced$getHeader() == null ? "" : clean(tab.hugosmpenhanced$getHeader().getString());
		String footer = tab.hugosmpenhanced$getFooter() == null ? "" : clean(tab.hugosmpenhanced$getFooter().getString());
		List<String> tabLines = new ArrayList<>(List.of(header.split("\n")));
		tabLines.addAll(List.of(footer.split("\n")));
		String world = level.dimension().identifier().toString();

		// Location: a "Server: X" line wins, then the last warp command, then the world name and titles.
		String location = null;
		String locationSource = null;
		String labeled = labeledPlace(sidebar);
		String labeledSource = "Scoreboard";
		if (labeled == null) {
			labeled = labeledPlace(tabLines);
			labeledSource = "Tabliste";
		}
		if (labeled != null) {
			String rule = matchRule(labeled);
			location = rule != null ? rule : labeled;
			locationSource = labeledSource;
		}
		if (location == null && commandLocation != null) {
			location = commandLocation;
			locationSource = "Befehl";
		}
		if (location == null) {
			String path = level.dimension().identifier().getPath();
			boolean vanilla = path.equals("overworld") || path.equals("the_nether") || path.equals("the_end");
			String rule = vanilla ? null : matchRule(path);
			if (rule != null) {
				location = rule;
				locationSource = "Weltname";
			}
		}
		if (location == null) {
			String titles = HUGOSMP.matcher(title + "\n" + header + "\n" + footer).replaceAll(" ");
			String rule = matchRule(titles);
			if (rule != null) {
				location = rule;
				locationSource = "Titel";
			}
		}
		String spawnName = matchRule("spawn");
		boolean nearSpawn = config.discordSpawnRadius > 0 && nearSpawn(player, level, config.discordSpawnRadius);
		if (spawnName != null && spawnName.equals(location) && "Befehl".equals(locationSource) && !nearSpawn) {
			// Walked away from the spawn after /spawn.
			location = null;
			commandLocation = null;
		}
		if (spawnName != null && nearSpawn && (location == null || location.equals(matchRule("smp")))) {
			location = spawnName;
			locationSource = "Nähe zum Weltspawn";
		}
		if (location == null && !config.discordFallbackLocation.isBlank()) {
			location = config.discordFallbackLocation.strip();
			locationSource = "Standard";
		}
		if (location != null && !location.equals(lastLocation)) {
			lastLocation = location;
			locationSince = System.currentTimeMillis();
		}

		// Money, kills and deaths from labelled lines; then chat answers and the vanilla statistics.
		Double money = labeledNumber(sidebar, MONEY_LABEL);
		String moneySource = money != null ? "Scoreboard" : null;
		if (money == null) {
			money = labeledNumber(tabLines, MONEY_LABEL);
			moneySource = money != null ? "Tabliste" : null;
		}
		if (money == null && chatMoney != null) {
			money = chatMoney;
			moneySource = "Chat";
		}
		Double killsValue = labeledNumber(sidebar, KILLS_LABEL);
		String killsSource = killsValue != null ? "Scoreboard" : null;
		if (killsValue == null && (killsValue = labeledNumber(tabLines, KILLS_LABEL)) != null) {
			killsSource = "Tabliste";
		}
		Double deathsValue = labeledNumber(sidebar, DEATHS_LABEL);
		String deathsSource = deathsValue != null ? "Scoreboard" : null;
		if (deathsValue == null && (deathsValue = labeledNumber(tabLines, DEATHS_LABEL)) != null) {
			deathsSource = "Tabliste";
		}
		Double kd = labeledNumber(sidebar, KD_LABEL);
		if (kd == null) {
			kd = labeledNumber(tabLines, KD_LABEL);
		}
		if ((killsValue == null || deathsValue == null) && config.discordEnabled && DiscordPresence.onHugoSmp()) {
			requestStats(client);
			// Play time is never 0 once the server has answered the request.
			if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0) {
				if (killsValue == null) {
					killsValue = (double) player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
					killsSource = "Statistik";
				}
				if (deathsValue == null) {
					deathsValue = (double) player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
					deathsSource = "Statistik";
				}
			}
		}
		Integer kills = killsValue == null ? null : (int) Math.round(killsValue);
		Integer deaths = deathsValue == null ? null : (int) Math.round(deathsValue);
		if (kd == null && kills != null && deaths != null) {
			kd = deaths == 0 ? kills : (double) kills / deaths;
		}
		int online = client.getConnection().getOnlinePlayers().size();
		return last = new Reading(location, locationSource, money, moneySource, kills, killsSource, deaths, deathsSource, kd,
			online, title, sidebar, header, footer, world);
	}

	private static void trackTeleports(LocalPlayer player, ClientLevel level) {
		Vec3 position = player.position();
		boolean moved = lastLevel != level || lastPosition == null || lastPosition.distanceTo(position) > TELEPORT_DISTANCE;
		if (moved && lastLevel != null) {
			boolean hint = pendingHint != null && System.currentTimeMillis() - pendingHintAt < HINT_WINDOW;
			commandLocation = hint ? pendingHint : null;
			pendingHint = null;
		}
		lastLevel = level;
		lastPosition = position;
	}

	private static boolean nearSpawn(LocalPlayer player, ClientLevel level, int radius) {
		var respawn = level.getRespawnData();
		if (respawn == null || !respawn.dimension().equals(level.dimension())) {
			return false;
		}
		BlockPos spawn = respawn.pos();
		double dx = player.getX() - spawn.getX();
		double dz = player.getZ() - spawn.getZ();
		return dx * dx + dz * dz <= (double) radius * radius;
	}

	private static void requestStats(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastStatsRequest < STATS_INTERVAL || client.getConnection() == null) {
			return;
		}
		lastStatsRequest = now;
		client.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
	}

	// ------------------------------------------------------------------ scoreboard

	private static Objective sidebarObjective(Scoreboard scoreboard, LocalPlayer player) {
		PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null && scoreboard.getDisplayObjective(slot) != null) {
				return scoreboard.getDisplayObjective(slot);
			}
		}
		return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	/** The sidebar as it is drawn: top to bottom, with team prefixes and the number on the right. */
	private static List<String> sidebarLines(Scoreboard scoreboard, Objective objective) {
		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective).stream()
			.filter(entry -> !entry.isHidden()).toList());
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));
		List<String> lines = new ArrayList<>();
		for (PlayerScoreEntry entry : entries.subList(0, Math.min(15, entries.size()))) {
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			String name = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			String value = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)).getString();
			lines.add(clean(value.isEmpty() ? name : name + " " + value));
		}
		return lines;
	}

	// ------------------------------------------------------------------ parsing

	static String clean(String text) {
		return FORMATTING.matcher(text).replaceAll("").replace(' ', ' ').strip();
	}

	/** The value after "Server:", "Welt »" etc., or on the next line when the label stands alone. */
	static String labeledPlace(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			Matcher matcher = PLACE_LABEL.matcher(lines.get(i).strip());
			if (!matcher.find()) {
				continue;
			}
			String value = matcher.group(2).strip();
			if (value.isEmpty() && i + 1 < lines.size()) {
				value = lines.get(i + 1).strip();
			}
			value = value.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N})]+$", "");
			if (!value.isEmpty() && value.length() <= 32) {
				return value;
			}
		}
		return null;
	}

	/** The number on the line with the label, or on the next line when the label stands alone. */
	static Double labeledNumber(List<String> lines, Pattern label) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			Matcher matcher = label.matcher(line);
			if (!matcher.find()) {
				continue;
			}
			Double value = parseNumber(line.substring(matcher.end()));
			if (value == null && label == MONEY_LABEL && "$".equals(matcher.group(1))) {
				// "1.234$": the number stands before the sign.
				value = parseNumber(line.substring(0, matcher.start()));
			}
			if (value == null && line.substring(matcher.end()).replaceAll("[^\\p{L}\\p{N}]", "").isEmpty() && i + 1 < lines.size()) {
				value = parseNumber(lines.get(i + 1));
			}
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/** "1.234.567", "1,5k", "2,3 Mio", "$12.000" ... */
	static Double parseNumber(String text) {
		if (text == null) {
			return null;
		}
		Matcher matcher = NUMBER.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		String digits = matcher.group(1).replaceAll("[.,]+$", "");
		String suffix = matcher.group(2);
		Double value;
		if (suffix != null && digits.matches("\\d+[.,]\\d+")) {
			// With a suffix one separator is always the decimal point: "1.500M" is 1,5 million.
			value = Double.parseDouble(digits.replace(',', '.'));
		} else {
			value = AuctionLore.parseAmount(digits);
		}
		if (value == null) {
			return null;
		}
		if (suffix != null) {
			value *= switch (suffix.toLowerCase(Locale.ROOT)) {
				case "k", "tsd" -> 1_000;
				case "m", "mio" -> 1_000_000;
				case "b", "mrd" -> 1_000_000_000;
				default -> 1;
			};
		}
		return value;
	}

	/** The name of the first location rule whose word appears in the text ("afk=AFK-Welt"). */
	static String matchRule(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String rule : ModConfigManager.CONFIG.discordLocations) {
			int eq = rule.indexOf('=');
			String word = (eq < 0 ? rule : rule.substring(0, eq)).strip().toLowerCase(Locale.ROOT);
			if (word.isEmpty() || !lower.contains(word)) {
				continue;
			}
			String name = eq < 0 ? "" : rule.substring(eq + 1).strip();
			if (name.isEmpty()) {
				name = Character.toUpperCase(word.charAt(0)) + word.substring(1);
			}
			return name;
		}
		return null;
	}
}
