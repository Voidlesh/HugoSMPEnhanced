package com.hugosmpenhanced.test;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.mentions.MentionHighlightFeature;
import com.hugosmpenhanced.client.feature.mentions.MentionStyle;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.GuiMessage;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Mentions in all three styles: a chat line naming you gets the hint line, the gold colour or both;
 * your own line with your name and a line without it stay as they are.
 */
@SuppressWarnings("UnstableApiUsage")
public class MentionGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		MentionHighlightFeature.forceActive = true;
		ModConfigManager.CONFIG.mentionsEnabled = true;
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			String name = context.computeOnClient(client -> client.player.getGameProfile().name());

			for (MentionStyle style : MentionStyle.values()) {
				ModConfigManager.CONFIG.mentionStyle = style;
				context.runOnClient(client -> client.gui.getChat().clearMessages(false));
				say(singleplayer, "[Clan] Ehrlich: hey " + name + ", kommst du?");
				say(singleplayer, name + ": ich bin " + name);
				say(singleplayer, "[HugoSMP] Willkommen zurück, " + name);
				say(singleplayer, "Ehrlich: niemand gemeint");
				context.waitTicks(5);
				int expected = style.line() ? 5 : 4;
				var all = context.computeOnClient(client -> allMessages(client));
				System.out.println("MENTION " + style + " lines=" + all);
				if (all.size() != expected) {
					throw new AssertionError("Mention " + style + ": expected " + expected + " chat lines, got " + all);
				}
				boolean tagged = context.computeOnClient(client -> firstTagged(client));
				if (tagged != style.color()) {
					throw new AssertionError("Mention " + style + ": gold stripe " + (tagged ? "shown" : "missing"));
				}
				context.takeScreenshot("mention-" + style.name().toLowerCase());
			}
			context.setScreen(() -> new SettingsScreen(null).select("Chat", "Erwähnungen"));
			context.waitTicks(5);
			context.takeScreenshot("mention-settings");
			context.setScreen(() -> null);
		}
		MentionHighlightFeature.forceActive = false;
	}

	private static void say(TestSingleplayerContext singleplayer, String text) {
		singleplayer.getServer().runCommand("tellraw @a \"" + text + "\"");
	}

	private static java.util.List<String> allMessages(net.minecraft.client.Minecraft client) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (GuiMessage message : messages(client)) {
			out.add(message.content().getString());
		}
		return out;
	}

	private static boolean firstTagged(net.minecraft.client.Minecraft client) {
		for (GuiMessage message : messages(client)) {
			if (message.content().getString().contains("kommst du")) {
				return message.tag() != null && message.tag().text() != null
					&& "Erwähnung".equals(message.tag().text().getString());
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static java.util.List<GuiMessage> messages(net.minecraft.client.Minecraft client) {
		try {
			java.lang.reflect.Field field = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			return (java.util.List<GuiMessage>) field.get(client.gui.getChat());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
