package com.hugosmpenhanced.client.feature.discord;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.sell.SellEngine;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Discord Rich Presence: what you do on HugoSMP shows on your Discord profile. Two lines of
 * text with placeholders, the elapsed time, the logo with your head and up to two buttons. Runs
 * only while the Discord desktop app is open; without it nothing happens.
 */
public final class DiscordPresence {
	public static final String LOGO_URL = "https://raw.githubusercontent.com/Voidlesh/HugoSMPEnhanced/main/docs/discord/app-icon.png";
	public static final String DISCORD_URL = "https://discord.gg/hugosmp";
	public static final String MOD_URL = "https://github.com/Voidlesh/HugoSMPEnhanced";
	public static final String PLACEHOLDERS = "{ort} {geld} {kills} {tode} {kd} {name} {spieler}";
	private static final Pattern SEPARATOR = Pattern.compile("\\s+[·|•\\-]\\s+");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
	private static final int UPDATE_TICKS = 20;

	/** The "HugoSMP" application in the Discord Developer Portal; its name is the "Spielt ..." line. */
	public static final String APP_ID = "1559249038363201627";

	/** Tests: talk to the fake Discord with another id. */
	public static String appId = APP_ID;

	/** Tests: treat every world as HugoSMP. */
	public static boolean anyServer;

	private static final DiscordConnection CONNECTION = new DiscordConnection();
	/** HugoSMP moves players between its worlds by reconnecting; that must not restart the clock. */
	private static final long WORLD_SWITCH_GAP = 30_000;

	private static long joinedAt = System.currentTimeMillis();
	private static String sessionAddress;
	private static long disconnectedAt;
	private static int ticks;
	private static View current;

	/** What Discord shows, also drawn by the preview in the settings. Null fields are left out. */
	public record View(String details, String state, Long start, String largeText, String smallText, String head,
		List<String[]> buttons) {
	}

	private DiscordPresence() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks >= UPDATE_TICKS) {
				ticks = 0;
				update(client);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(address(client)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
		ClientSendMessageEvents.COMMAND.register(StatusReader::onCommand);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				StatusReader.onGameMessage(message);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CONNECTION.shutdown());
	}

	/**
	 * A join right after leaving the same server is a world switch: the time keeps running and
	 * the warp command hint (/afk ...) stays for the new world.
	 */
	public static void onJoin(String address) {
		boolean worldSwitch = address.equals(sessionAddress) && System.currentTimeMillis() - disconnectedAt < WORLD_SWITCH_GAP;
		if (!worldSwitch) {
			joinedAt = System.currentTimeMillis();
			StatusReader.reset();
		}
		sessionAddress = address;
		ticks = UPDATE_TICKS - 5;
	}

	public static void onDisconnect() {
		disconnectedAt = System.currentTimeMillis();
		ticks = UPDATE_TICKS - 5;
	}

	public static String address(Minecraft client) {
		if (client.hasSingleplayerServer()) {
			return "singleplayer";
		}
		return client.getCurrentServer() == null ? "" : client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
	}

	/** Recomputes the presence and hands it to the connection; cheap, runs once a second. */
	public static void update(Minecraft client) {
		ModConfig config = ModConfigManager.CONFIG;
		if (!config.discordEnabled) {
			// The money tracker and the debug dump read the sidebar from here, presence or not.
			StatusReader.read(client);
		}
		View view = config.discordEnabled ? build(client) : null;
		current = view;
		CONNECTION.show(config.discordEnabled, appId, view == null ? null : toJson(view));
	}

	public static boolean onHugoSmp() {
		return anyServer || ServerCheck.isHugoSmp();
	}

	/** The last computed presence, null when nothing is shown. */
	public static View current() {
		return current;
	}

	// ------------------------------------------------------------------ status for the settings

	public static boolean connected() {
		return CONNECTION.status() == DiscordConnection.Status.CONNECTED;
	}

	public static Component statusText() {
		ModConfig config = ModConfigManager.CONFIG;
		if (!config.discordEnabled) {
			return Component.literal("Aus").withStyle(ChatFormatting.GRAY);
		}
		return switch (CONNECTION.status()) {
			case CONNECTED -> {
				String error = CONNECTION.lastError();
				yield error.isEmpty()
					? Component.literal("Verbunden" + (CONNECTION.userName().isEmpty() ? "" : " als " + CONNECTION.userName()))
						.withStyle(ChatFormatting.GREEN)
					: Component.literal("Discord: " + error).withStyle(ChatFormatting.RED);
			}
			case BAD_ID -> Component.literal(CONNECTION.lastError()).withStyle(ChatFormatting.RED);
			case NOT_FOUND -> Component.literal("Discord-App nicht gefunden").withStyle(ChatFormatting.GOLD);
			default -> Component.literal("Suche Discord ...").withStyle(ChatFormatting.GRAY);
		};
	}

	// ------------------------------------------------------------------ building

	private static View build(Minecraft client) {
		ModConfig config = ModConfigManager.CONFIG;
		StatusReader.Reading reading = StatusReader.read(client);
		boolean inWorld = client.level != null && client.player != null;
		if (!inWorld && current != null && System.currentTimeMillis() - disconnectedAt < 5_000) {
			// Mid world switch: keep showing the last place instead of flashing "Im Hauptmenü".
			return current;
		}
		boolean hugo = inWorld && onHugoSmp();
		if (!hugo && config.discordOutside == DiscordOutside.HIDE) {
			return null;
		}
		String name = client.getUser().getName();
		String head = config.discordShowHead ? "https://mc-heads.net/avatar/" + client.getUser().getProfileId() + "/64" : null;
		List<String[]> buttons = buttons(config);
		if (!hugo) {
			String details = !inWorld ? "Im Hauptmenü" : client.hasSingleplayerServer() ? "Einzelspieler" : "Auf einem anderen Server";
			Long start = inWorld && config.discordTimer != DiscordTimer.OFF ? joinedAt : null;
			return new View(details, null, start, "HugoSMP Enhanced", head == null ? null : name, head, buttons);
		}
		return view(reading, name, head, buttons, config);
	}

	/** The presence for a reading; the preview uses it with example values outside HugoSMP. */
	public static View view(StatusReader.Reading reading, String name, String head, List<String[]> buttons, ModConfig config) {
		Map<String, String> values = values(reading, name, config);
		String details = render(config.discordDetails, values);
		String state = render(config.discordState, values);
		Long start = switch (config.discordTimer) {
			case JOIN -> joinedAt;
			case LOCATION -> StatusReader.locationSince();
			case OFF -> null;
		};
		String largeText = reading.location() != null && config.discordShowLocation ? reading.location() + " auf HugoSMP.net" : "HugoSMP.net";
		return new View(details, state, start, largeText, head == null ? null : name, head, buttons);
	}

	public static List<String[]> buttons(ModConfig config) {
		List<String[]> buttons = new ArrayList<>();
		if (config.discordButtonDiscord) {
			buttons.add(new String[] {"HugoSMP-Discord", DISCORD_URL});
		}
		if (config.discordButtonMod) {
			buttons.add(new String[] {"Mod herunterladen", MOD_URL});
		}
		return buttons;
	}

	static Map<String, String> values(StatusReader.Reading reading, String name, ModConfig config) {
		Map<String, String> values = new HashMap<>();
		values.put("name", name);
		values.put("spieler", reading.online() > 0 ? String.valueOf(reading.online()) : null);
		values.put("ort", config.discordShowLocation ? reading.location() : null);
		values.put("geld", config.discordShowMoney && reading.money() != null ? money(reading.money(), config.discordMoneyShort) : null);
		values.put("kills", config.discordShowKills && reading.kills() != null ? SellEngine.formatCount(reading.kills()) : null);
		values.put("tode", config.discordShowDeaths && reading.deaths() != null ? SellEngine.formatCount(reading.deaths()) : null);
		values.put("kd", config.discordShowKd && reading.kd() != null
			? new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(reading.kd()) : null);
		return values;
	}

	static String money(double amount, boolean shortForm) {
		if (!shortForm || Math.abs(amount) < 10_000) {
			return SellEngine.formatMoney(amount);
		}
		DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.GERMANY));
		if (Math.abs(amount) >= 1_000_000_000) {
			return "$" + format.format(amount / 1_000_000_000) + " Mrd";
		}
		if (Math.abs(amount) >= 1_000_000) {
			return "$" + format.format(amount / 1_000_000) + " Mio";
		}
		return "$" + format.format(amount / 1_000) + "k";
	}

	/**
	 * Fills the placeholders. Parts separated by " · ", " | ", " • " or " - " whose placeholder
	 * has no value (not found, or hidden in the settings) are left out with their separator.
	 */
	static String render(String template, Map<String, String> values) {
		if (template == null || template.isBlank()) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		List<String> separators = new ArrayList<>();
		Matcher separator = SEPARATOR.matcher(template);
		int from = 0;
		while (separator.find()) {
			parts.add(template.substring(from, separator.start()));
			separators.add(separator.group());
			from = separator.end();
		}
		parts.add(template.substring(from));

		StringBuilder out = new StringBuilder();
		String pendingSeparator = null;
		for (int i = 0; i < parts.size(); i++) {
			String filled = fill(parts.get(i), values);
			if (filled != null && !filled.isBlank()) {
				if (out.length() > 0 && pendingSeparator != null) {
					out.append(pendingSeparator);
				}
				out.append(filled.strip());
			}
			if (i < separators.size()) {
				pendingSeparator = separators.get(i);
			}
		}
		String text = out.toString().strip();
		if (text.length() < 2) {
			return null;
		}
		return text.length() > 128 ? text.substring(0, 127) + "…" : text;
	}

	private static String fill(String part, Map<String, String> values) {
		Matcher matcher = PLACEHOLDER.matcher(part);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String key = matcher.group(1).toLowerCase(Locale.ROOT);
			if (!values.containsKey(key)) {
				matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
				continue;
			}
			String value = values.get(key);
			if (value == null) {
				return null;
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	static JsonObject toJson(View view) {
		JsonObject activity = new JsonObject();
		activity.addProperty("type", 0);
		if (view.details() != null) {
			activity.addProperty("details", view.details());
		}
		if (view.state() != null) {
			activity.addProperty("state", view.state());
		}
		if (view.start() != null) {
			JsonObject timestamps = new JsonObject();
			timestamps.addProperty("start", view.start());
			activity.add("timestamps", timestamps);
		}
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", LOGO_URL);
		assets.addProperty("large_text", view.largeText());
		if (view.head() != null) {
			assets.addProperty("small_image", view.head());
			assets.addProperty("small_text", view.smallText());
		}
		activity.add("assets", assets);
		if (!view.buttons().isEmpty()) {
			JsonArray buttons = new JsonArray();
			for (String[] button : view.buttons()) {
				JsonObject entry = new JsonObject();
				entry.addProperty("label", button[0]);
				entry.addProperty("url", button[1]);
				buttons.add(entry);
			}
			activity.add("buttons", buttons);
		}
		return activity;
	}

	/** Prints what the detection sees, so a screenshot shows where each value comes from. */
	public static void printDiagnosis(Minecraft client) {
		if (client.player == null) {
			return;
		}
		StatusReader.Reading r = StatusReader.read(client);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("[Discord] ").withStyle(ChatFormatting.GOLD).append(statusText()));
		lines.add(entry("Ort", r.location(), r.locationSource()));
		lines.add(entry("Geld", r.money() == null ? null : SellEngine.formatMoney(r.money()), r.moneySource()));
		lines.add(entry("Kills", r.kills() == null ? null : String.valueOf(r.kills()), r.killsSource()));
		lines.add(entry("Tode", r.deaths() == null ? null : String.valueOf(r.deaths()), r.deathsSource()));
		lines.add(Component.literal(" Welt: " + r.world()).withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal(" Scoreboard: " + (r.sidebarTitle().isEmpty() && r.sidebar().isEmpty() ? "keins"
			: r.sidebarTitle() + " | " + String.join(" | ", r.sidebar().stream().filter(line -> !line.isBlank()).toList())))
			.withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal(" Tab oben: " + (r.tabHeader().isEmpty() ? "-" : r.tabHeader().replace("\n", " | "))).withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal(" Tab unten: " + (r.tabFooter().isEmpty() ? "-" : r.tabFooter().replace("\n", " | "))).withStyle(ChatFormatting.GRAY));
		for (Component line : lines) {
			client.player.displayClientMessage(line, false);
		}
	}

	private static Component entry(String label, String value, String source) {
		return Component.literal(" " + label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(value == null ? "nicht erkannt" : value).withStyle(value == null ? ChatFormatting.RED : ChatFormatting.WHITE))
			.append(Component.literal(source == null ? "" : "  (" + source + ")").withStyle(ChatFormatting.DARK_GRAY));
	}
}
