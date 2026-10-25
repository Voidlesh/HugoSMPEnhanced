package com.hugosmpenhanced.test;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.streamermode.NameMask;
import com.hugosmpenhanced.client.feature.streamermode.StreamerMode;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if modern
import net.minecraft.client.multiplayer.chat.GuiMessage;
//? else
import net.minecraft.client.GuiMessage;
//? end
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Streamer mode in a flat test world: chat with names of others and your own, a mannequin named
 * like another player, the scoreboard sidebar and the tab list, once with the mode off and once on.
 * Also checks the text masking itself and that a chat line and its click events survive masking.
 */
@SuppressWarnings("UnstableApiUsage")
public class StreamerModeGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		String own = context.computeOnClient(client -> client.getUser().getName());
		checkText(context, own);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 6000");
			server.runCommand("tp @p 0.5 -60 0.5 0 0");
			server.runCommand("summon mannequin 0.5 -60 4.5 {Rotation:[180f,0f],profile:{name:\"Notch\"},"
				+ "CustomName:\"Notch\",CustomNameVisible:1b}");
			server.runCommand("summon mannequin 2.5 -60 5.5 {Rotation:[200f,0f],profile:{name:\"jeb_\"},"
				+ "CustomName:{text:\"[Admin] jeb_\",color:\"red\"},CustomNameVisible:1b}");
			server.runCommand("scoreboard objectives add geld dummy {\"text\":\"HugoSMP\",\"color\":\"gold\",\"bold\":true}");
			server.runCommand("scoreboard objectives setdisplay sidebar geld");
			server.runCommand("scoreboard objectives setdisplay list geld");
			server.runCommand("scoreboard players set " + own + " geld 1500");
			server.runCommand("scoreboard players set Notch geld 900");
			server.runCommand("scoreboard players set Dinnerbone geld 120");
			server.runCommand("tellraw @a [{\"text\":\"<\"},{\"text\":\"Notch\",\"color\":\"aqua\",\"click_event\":{\"action\":\"suggest_command\","
				+ "\"command\":\"/msg Notch \"}},{\"text\":\"> hey \"},{\"text\":\"" + own + "\",\"color\":\"yellow\"},{\"text\":\", kommst du zum Spawn?\"}]");
			server.runCommand("tellraw @a {\"text\":\"<" + own + "> klar, jeb_ und Dinnerbone sind auch schon da\"}");
			server.runCommand("tellraw @a [{\"text\":\"[TPA] \",\"color\":\"gold\"},{\"text\":\"Dinnerbone möchte sich zu dir teleportieren.\","
				+ "\"color\":\"gray\"}]");
			server.runCommand("tellraw @a {\"text\":\"§c[Admin] §fjeb_§7: Notch123 ist kein Name, notch schon\"}");
			context.waitTicks(10);
			singleplayer.getConnection().waitForChunksRender();

			setMode(context, false);
			context.waitTicks(3);
			context.runOnClient(client -> client.options.keyPlayerList.setDown(true));
			context.waitTicks(3);
			context.takeScreenshot("streamermodus-1-aus");

			setMode(context, true);
			context.waitTicks(3);
			context.takeScreenshot("streamermodus-2-an");
			checkChat(context, own);
			context.runOnClient(client -> client.options.keyPlayerList.setDown(false));

			context.setScreen(() -> new ChatScreen("/msg Notch hi", false));
			context.waitTicks(3);
			context.takeScreenshot("streamermodus-3-chat-offen");
			context.setScreen(() -> null);

			context.runOnClient(client -> {
				ModConfigManager.CONFIG.streamerModeNumbered = false;
				ModConfigManager.CONFIG.streamerModeOwnName = "Ich";
				StreamerMode.update(client);
			});
			context.waitTicks(3);
			context.takeScreenshot("streamermodus-4-ohne-nummern");

			int guiScale = context.computeOnClient(client -> client.options.guiScale().get());
			context.runOnClient(client -> client.options.guiScale().set(1));
			context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Streamer-Modus"));
			context.waitTicks(3);
			context.takeScreenshot("streamermodus-5-einstellungen");
			context.setScreen(() -> null);
			context.runOnClient(client -> client.options.guiScale().set(guiScale));
			reset(context);
		}
	}

	private static void setMode(ClientGameTestContext context, boolean on) {
		context.runOnClient(client -> {
			ModConfig config = ModConfigManager.CONFIG;
			config.streamerModeEnabled = on;
			config.streamerModeExtraNames = new ArrayList<>(List.of("Dinnerbone", "Notch", "jeb_"));
			StreamerMode.update(client);
		});
	}

	private static void reset(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ModConfig config = ModConfigManager.CONFIG;
			ModConfig defaults = new ModConfig();
			config.streamerModeEnabled = defaults.streamerModeEnabled;
			config.streamerModeExtraNames = defaults.streamerModeExtraNames;
			config.streamerModeNumbered = defaults.streamerModeNumbered;
			config.streamerModeOwnName = defaults.streamerModeOwnName;
			ModConfigManager.save();
			StreamerMode.update(client);
		});
	}

	private static void checkText(ClientGameTestContext context, String own) {
		context.runOnClient(client -> {
			ModConfig config = ModConfigManager.CONFIG;
			config.streamerModeEnabled = true;
			config.streamerModeExtraNames = new ArrayList<>(List.of("Notch", ".jeb_", "Spieler"));
			StreamerMode.update(client);
			String notch = NameMask.nameFor("Notch");
			String jeb = NameMask.nameFor("jeb_");
			expect(notch.startsWith("Spieler ") && !notch.equals(jeb), "numbered stand-ins: " + notch + " / " + jeb);
			expect(NameMask.nameFor("Spieler").equals("Spieler"), "a stand-in word is never replaced");
			expect(NameMask.nameFor(own).equals("Streamer"), "own name");

			String in = "§a<Notch>§r hi jeb_'s Notch123 NOTCH " + own + "!";
			String out = NameMask.mask(in);
			expect(out.equals("§a<" + notch + ">§r hi " + jeb + "'s Notch123 " + notch + " Streamer!"), "string: " + out);
			expect(NameMask.mask("§aNotch").equals("§a" + notch), "§ code before a name");
			String plain = "nothing to see";
			expect(NameMask.mask(plain) == plain, "untouched text is the same object");

			Style aqua = Style.EMPTY.withColor(0x55FFFF);
			Component component = Component.literal("<").append(Component.literal("Notch").setStyle(aqua)).append("> yo");
			FormattedCharSequence masked = NameMask.mask(component.getVisualOrderText());
			expect(NameMask.plain(masked).equals("<" + notch + "> yo"), "sequence: " + NameMask.plain(masked));
			int[] coloured = {0};
			masked.accept((index, style, codePoint) -> {
				if (aqua.equals(style)) {
					coloured[0]++;
				}
				return true;
			});
			expect(coloured[0] == notch.length(), "stand-in keeps the name's style");
			expect(NameMask.mask(component).getString().equals("<" + notch + "> yo"), "component");

			int widthMasked = client.font.width("<" + notch + "> yo");
			expect(client.font.width(component) == widthMasked, "width measures the masked text");

			config.streamerModeEnabled = false;
			StreamerMode.update(client);
			expect(NameMask.mask(in) == in, "off: nothing replaced");
		});
	}

	private static void checkChat(ClientGameTestContext context, String own) {
		context.runOnClient(client -> {
			List<String> lines = new ArrayList<>();
			for (GuiMessage.Line line : chatLines(client)) {
				lines.add(NameMask.plain(line.content()));
			}
			String all = String.join("\n", lines);
			for (String name : List.of("Notch>", "jeb_", "Dinnerbone", own)) {
				expect(!all.contains(name), "chat still shows " + name + ":\n" + all);
			}
			expect(all.contains("Notch123"), "longer words stay:\n" + all);
			// The click event on the name came along with the stand-in.
			boolean click = false;
			for (GuiMessage.Line line : chatLines(client)) {
				boolean[] found = {false};
				line.content().accept((index, style, codePoint) -> {
					found[0] |= style.getClickEvent() != null;
					return true;
				});
				click |= found[0];
			}
			expect(click, "click event kept");
		});
	}

	@SuppressWarnings("unchecked")
	private static List<GuiMessage.Line> chatLines(net.minecraft.client.Minecraft client) {
		try {
			var field = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("trimmedMessages");
			field.setAccessible(true);
			//? if modern
			return (List<GuiMessage.Line>) field.get(client.gui.hud.getChat());
			//? else
			return (List<GuiMessage.Line>) field.get(client.gui.getChat());
			//? end
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void expect(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Streamer mode: " + what);
		}
	}
}
