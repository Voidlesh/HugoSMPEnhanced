package com.hugosmpenhanced.test;

import java.io.IOException;
import java.util.function.Predicate;

import com.google.gson.JsonObject;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.discord.DiscordOutside;
import com.hugosmpenhanced.client.feature.discord.DiscordPresence;
import com.hugosmpenhanced.client.feature.discord.DiscordTimer;
import com.hugosmpenhanced.client.feature.discord.StatusReader;
import com.hugosmpenhanced.client.settings.ModSettings;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import org.lwjgl.glfw.GLFW;

/**
 * Discord Rich Presence against a fake Discord app on a Unix socket: handshake, the activity in
 * the main menu, on "HugoSMP" with values from the sidebar (as plugins draw it: hidden numbers,
 * names with "Server: SMP", "Geld: $1.234.567" ...), location changes, a warp command, the
 * spawn radius, money from a chat answer, the vanilla statistics as fallback, hidden values, an
 * unknown application id and switching it off. Screenshots of the settings tab and the chat check.
 */
@SuppressWarnings("UnstableApiUsage")
public class DiscordPresenceGameTest implements FabricClientGameTest {
	private static final String ID = "123456789012345678";
	private static final int WAIT = 20 * 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		FakeDiscord discord;
		try {
			discord = new FakeDiscord();
		} catch (IOException e) {
			throw new AssertionError("Could not start the fake Discord", e);
		}
		System.setProperty("hugosmpenhanced.discordIpcDir", discord.dir.toString());
		context.runOnClient(client -> {
			client.options.menuBackgroundBlurriness().set(0);
			ModConfig config = ModConfigManager.CONFIG;
			ModConfig defaults = new ModConfig();
			config.discordEnabled = true;
			config.discordAppId = ID;
			config.discordDetails = defaults.discordDetails;
			config.discordState = defaults.discordState;
			config.discordShowMoney = true;
			config.discordShowKd = false;
			config.discordOutside = DiscordOutside.SHOW;
			config.discordTimer = DiscordTimer.JOIN;
			config.discordLocations = defaults.discordLocations;
			config.discordSpawnRadius = 150;
			DiscordPresence.anyServer = true;
		});

		// Main menu: connects, says hello with the id and shows "Im Hauptmenü".
		waitFor(context, discord, a -> "Im Hauptmenü".equals(str(a, "details")), "main menu activity");
		check(discord.handshakes.contains(ID), "handshake with the application id");
		check(context.computeOnClient(client -> DiscordPresence.connected()), "status connected");
		context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Discord"));
		context.waitTicks(3);
		context.takeScreenshot("discord-einstellungen-menue");

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());
			// Away from the world spawn; the spawn radius is checked further down.
			server.runCommand("tp @p 1000 -60 1000");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("time set 6000");
			// A plugin-style sidebar: blank numbers, the text in the entry names.
			server.runCommand("scoreboard objectives add sb dummy {\"text\":\"HugoSMP\",\"color\":\"gold\",\"bold\":true}");
			server.runCommand("scoreboard objectives setdisplay sidebar sb");
			server.runCommand("scoreboard objectives modify sb numberformat blank");
			// HugoSMP's own sidebar (screenshot from Levi): icon, label, value, no colon.
			line(server, "l1", 9, "{\"text\":\"\"}");
			line(server, "l2", 8, "[{\"text\":\"» Server: \",\"color\":\"gray\"},{\"text\":\"SMP\",\"color\":\"green\"}]");
			line(server, "l3", 7, "[{\"text\":\"$ \",\"color\":\"green\"},{\"text\":\"Geld \"},{\"text\":\"1.50M\",\"color\":\"green\"}]");
			line(server, "g0", 7, "[{\"text\":\"Gems \"},{\"text\":\"0\",\"color\":\"red\"}]");
			line(server, "l4", 6, "[{\"text\":\"⚔ Kills \"},{\"text\":\"42\",\"color\":\"gold\"}]");
			line(server, "l5", 5, "[{\"text\":\"☠ Tode \"},{\"text\":\"7\",\"color\":\"red\"}]");
			line(server, "c0", 5, "[{\"text\":\"Clan \"},{\"text\":\"GHG\",\"color\":\"dark_purple\"}]");
			line(server, "p0", 4, "[{\"text\":\"Ping \"},{\"text\":\"37ms\",\"color\":\"light_purple\"}]");
			line(server, "k0", 4, "[{\"text\":\"Keyall \"},{\"text\":\"???\",\"color\":\"aqua\"}]");
			line(server, "t0", 4, "[{\"text\":\"Spielzeit \"},{\"text\":\"4d 15h\",\"color\":\"green\"}]");
			line(server, "l6", 3, "{\"text\":\"hugosmp.net\",\"color\":\"red\"}");

			waitFor(context, discord, a -> "SMP".equals(str(a, "details"))
				&& "$1.500.000 · 42 Kills · 7 Tode".equals(str(a, "state")), "SMP with money, kills and deaths");
			JsonObject activity = discord.last();
			check(activity.getAsJsonObject("timestamps").get("start").getAsLong() > 0, "elapsed time");
			check(DiscordPresence.LOGO_URL.equals(activity.getAsJsonObject("assets").get("large_image").getAsString()), "logo");
			check(activity.getAsJsonObject("assets").get("small_image").getAsString().startsWith("https://mc-heads.net/avatar/"), "head");
			check(activity.getAsJsonArray("buttons").size() == 2, "two buttons");
			context.takeScreenshot("discord-sidebar-smp");

			context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Discord"));
			context.waitTicks(3);
			context.takeScreenshot("discord-einstellungen-live");
			context.getInput().setCursorPos(500, 400);
			context.getInput().scroll(-6);
			context.waitTicks(3);
			context.takeScreenshot("discord-einstellungen-unten");
			context.getInput().scroll(-12);
			context.waitTicks(3);
			context.takeScreenshot("discord-einstellungen-mitte");
			// Typing into the upper line's field: click, type, Enter.
			int[] field = option("Obere Zeile").bounds();
			context.runOnClient(client -> {
				MouseButtonEvent click = new MouseButtonEvent(field[0] + 20, field[1] + field[3] - 10,
					new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
				client.screen.mouseClicked(click, false);
				client.screen.mouseReleased(click);
				for (char c : " auf HugoSMP".toCharArray()) {
					client.screen.charTyped(new CharacterEvent(c, 0));
				}
			});
			context.waitTicks(2);
			context.takeScreenshot("discord-text-bearbeiten");
			context.runOnClient(client -> client.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
			check("{ort} auf HugoSMP".equals(ModConfigManager.CONFIG.discordDetails), "typed upper line: " + ModConfigManager.CONFIG.discordDetails);
			waitFor(context, discord, a -> "SMP auf HugoSMP".equals(str(a, "details")), "own upper line");
			context.runOnClient(client -> ModConfigManager.CONFIG.discordDetails = "{ort}");
			context.getInput().scroll(-60);
			context.waitTicks(3);
			context.takeScreenshot("discord-einstellungen-ende");
			context.setScreen(() -> null);

			// K/D on, money short.
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.discordShowKd = true;
				ModConfigManager.CONFIG.discordMoneyShort = true;
			});
			waitFor(context, discord, a -> "$1,5 Mio · 42 Kills · 7 Tode · K/D 6,00".equals(str(a, "state")), "K/D and short money");

			// Another server: the "Server:" line wins.
			line(server, "l2", 8, "[{\"text\":\"» Server: \",\"color\":\"gray\"},{\"text\":\"Duels-2\",\"color\":\"green\"}]");
			waitFor(context, discord, a -> "Duels".equals(str(a, "details")), "Duels from the sidebar");

			// No server line: near the world spawn it is the spawn, far away the fallback.
			server.runCommand("scoreboard players reset l2 sb");
			server.runCommand("tp @p " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ());
			waitFor(context, discord, a -> "Spawn".equals(str(a, "details")), "spawn by distance");
			server.runCommand("tp @p 2000 -60 2000");
			waitFor(context, discord, a -> "SMP".equals(str(a, "details")), "fallback far from spawn");

			// A warp command followed by a teleport names the place.
			context.runOnClient(client -> client.player.connection.sendCommand("afk"));
			server.runCommand("tp @p 6000 -60 6000");
			waitFor(context, discord, a -> "AFK-Welt".equals(str(a, "details")), "AFK world after /afk");
			check("Befehl".equals(context.computeOnClient(client -> StatusReader.last().locationSource())), "source of /afk");

			// HugoSMP switches worlds by reconnecting: the clock keeps running and a warp hint survives it.
			long start = discord.last().getAsJsonObject("timestamps").get("start").getAsLong();
			context.runOnClient(client -> {
				client.player.connection.sendCommand("pvp");
				String address = DiscordPresence.address(client);
				DiscordPresence.onDisconnect();
				DiscordPresence.onJoin(address);
			});
			server.runCommand("tp @p 9000 -60 9000");
			waitFor(context, discord, a -> "PvP".equals(str(a, "details"))
				&& a.getAsJsonObject("timestamps").get("start").getAsLong() == start, "PvP after a world switch, same start time");
			context.runOnClient(client -> client.player.connection.sendCommand("afk"));
			server.runCommand("tp @p 6000 -60 6000");
			waitFor(context, discord, a -> "AFK-Welt".equals(str(a, "details")), "back in the AFK world");

			// Money from a chat answer once the sidebar has none, kills and deaths from the statistics.
			server.runCommand("scoreboard players reset l3 sb");
			server.runCommand("scoreboard players reset l4 sb");
			server.runCommand("scoreboard players reset l5 sb");
			server.runCommand("tellraw @a [{\"text\":\"Dein Kontostand: \",\"color\":\"gray\"},{\"text\":\"5.000$\",\"color\":\"green\"}]");
			context.runOnClient(client -> ModConfigManager.CONFIG.discordMoneyShort = false);
			waitFor(context, discord, a -> "$5.000 · 0 Kills · 0 Tode · K/D 0,00".equals(str(a, "state")), "chat money and statistics");
			check("Statistik".equals(context.computeOnClient(client -> StatusReader.last().killsSource())), "kills from statistics");

			// Hidden values drop out with their separator.
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.discordShowMoney = false;
				ModConfigManager.CONFIG.discordShowKd = false;
			});
			waitFor(context, discord, a -> "0 Kills · 0 Tode".equals(str(a, "state")), "money hidden");

			// The chat check shows what was read and from where.
			context.runOnClient(client -> client.execute(() -> DiscordPresence.printDiagnosis(client)));
			context.waitTicks(2);
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(3);
			context.takeScreenshot("discord-erkennung-chat");
			context.setScreen(() -> null);

			// Unknown application id: Discord closes with 4000, the menu says so; a good id reconnects.
			context.runOnClient(client -> ModConfigManager.CONFIG.discordAppId = FakeDiscord.BAD_ID);
			context.waitFor(client -> DiscordPresence.statusText().getString().contains("Unbekannte Application-ID"), WAIT);
			context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Discord"));
			context.waitTicks(3);
			context.takeScreenshot("discord-falsche-id");
			context.setScreen(() -> null);
			int before = discord.activities.size();
			context.runOnClient(client -> ModConfigManager.CONFIG.discordAppId = ID);
			context.waitFor(client -> DiscordPresence.connected() && discord.activities.size() > before, WAIT);

			// Switched off: the connection closes, which clears the presence in Discord.
			int closed = discord.closedConnections;
			context.runOnClient(client -> ModConfigManager.CONFIG.discordEnabled = false);
			context.waitFor(client -> discord.closedConnections > closed, WAIT);
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.discordEnabled = true;
				ModConfigManager.CONFIG.discordShowMoney = true;
				ModConfigManager.CONFIG.discordOutside = DiscordOutside.HIDE;
			});
			waitFor(context, discord, a -> str(a, "details") != null, "back on");
		}

		// Outside HugoSMP with "Nichts anzeigen": the activity is cleared.
		context.runOnClient(client -> DiscordPresence.anyServer = false);
		context.waitFor(client -> discord.activities.size() > 0 && discord.last() == null, WAIT);
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.discordOutside = DiscordOutside.SHOW;
			ModConfigManager.CONFIG.discordAppId = "";
			ModConfigManager.save();
		});
		System.out.println("DISCORD TEST passed, activities sent: " + discord.activities.size());
	}

	private static SettingsOption option(String label) {
		for (SettingsOption option : ModSettings.allOptions()) {
			if (option.label().equals(label)) {
				return option;
			}
		}
		throw new AssertionError("No option " + label);
	}

	private static void line(TestServerContext server, String holder, int score, String json) {
		server.runCommand("scoreboard players set " + holder + " sb " + score);
		server.runCommand("scoreboard players display name " + holder + " sb " + json);
	}

	private static void waitFor(ClientGameTestContext context, FakeDiscord discord, Predicate<JsonObject> condition, String what) {
		try {
			context.waitFor(client -> condition.test(discord.last()), WAIT);
		} catch (RuntimeException | AssertionError e) {
			throw new AssertionError("Discord never showed: " + what + ", last activity: " + discord.last()
				+ ", reading: " + context.computeOnClient(client -> StatusReader.last()), e);
		}
	}

	private static String str(JsonObject activity, String key) {
		return activity != null && activity.has(key) ? activity.get(key).getAsString() : null;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Discord presence: " + what);
		}
	}
}
