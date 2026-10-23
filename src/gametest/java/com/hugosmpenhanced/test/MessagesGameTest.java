package com.hugosmpenhanced.test;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.messages.MessageFeature;
import com.hugosmpenhanced.client.feature.messages.MessageLog;
import com.hugosmpenhanced.client.feature.messages.MessagesScreen;
import com.hugosmpenhanced.client.feature.messages.SideChat;
import com.hugosmpenhanced.client.feature.tpa.TpaRequestHandler;
import com.hugosmpenhanced.client.feature.tpa.TpaState;
import com.hugosmpenhanced.client.hud.HudEditorScreen;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.client.util.ServerCheck;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Private messages and the second chat: reads made-up server lines the way HugoSMP might send
 * them, checks the log (incoming, outgoing, echo not doubled, /r, refused message, TPA notes),
 * then screenshots the messages tab, the second chat on the HUD and the settings tab.
 */
@SuppressWarnings("UnstableApiUsage")
public class MessagesGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("gamerule doDaylightCycle false");
			singleplayer.getServer().runCommand("time set 6000");
			context.runOnClient(client -> {
				ServerCheck.forceHugoSmp = true;
				ModConfigManager.CONFIG.messagesEnabled = true;
				ModConfigManager.CONFIG.sideChatEnabled = true;
				ModConfigManager.CONFIG.sideChatHideFromMain = false;
				ModConfigManager.CONFIG.sideChatFade = 0;
				MessageLog.resetForTest();
				SideChat.clear();
				TpaState.clear();
			});
			context.waitTicks(5);

			logic(context);
			screens(context);
		} finally {
			context.runOnClient(client -> {
				ServerCheck.forceHugoSmp = false;
				ModConfigManager.CONFIG.sideChatHideFromMain = false;
				ModConfigManager.save();
				MessageLog.resetForTest();
				SideChat.clear();
			});
		}
	}

	private static void logic(ClientGameTestContext context) {
		context.runOnClient(client -> {
			MessageFeature.onSystemLine("[Hugo -> mir] Hast du kurz Zeit für einen Handel?");
			MessageFeature.onSystemLine("[Nachricht] Mika_77 » Du: Verkaufst du das Elytra noch?");
			MessageFeature.onCommand("msg Hugo Klar, ich komme gleich");
			// The server's echo of the message above must not add a second one.
			MessageFeature.onSystemLine("[Nachricht] Du -> Hugo: Klar, ich komme gleich");
			MessageFeature.onCommand("r Danke dir, bis gleich!");
			MessageFeature.onCommand("w Mika_77 Ja, ist noch da. 5 Mio?");
			MessageFeature.onSystemLine("[HugoSMP] Der Spieler Mika_77 ist nicht online.");
			MessageFeature.onSystemLine("[HugoSMP] Der Server startet in 5 Minuten neu.");
			MessageFeature.onSystemLine("[Freunde] Kai ist jetzt online.");
			// Not private: public chat that merely contains an arrow.
			MessageFeature.onSystemLine("Steve: wie komme ich -> zum Spawn?");
			MessageFeature.onCommand("pay Hugo 100");
			TpaRequestHandler.handleSystemMessage("[HugoSMP] Hugo hat dir eine Teleportations-Anfrage gesendet!");
		});
		List<MessageLog.Conversation> chats = context.computeOnClient(client -> MessageLog.conversations());
		StringBuilder dump = new StringBuilder();
		for (MessageLog.Conversation chat : chats) {
			dump.append(chat.name).append(" unread=").append(chat.unread).append('\n');
			for (MessageLog.Message message : chat.messages) {
				dump.append("   ").append(message.out ? "OUT " : "IN  ").append(message.note ? "(note) " : "").append(message.failed ? "(failed) " : "")
					.append(message.text).append('\n');
			}
		}
		System.out.println("MESSAGES\n" + dump);
		check(chats.size() == 2, "two conversations (Hugo, Mika_77), no third from the arrow or /pay: " + dump);
		MessageLog.Conversation hugo = context.computeOnClient(client -> MessageLog.find("Hugo"));
		MessageLog.Conversation mika = context.computeOnClient(client -> MessageLog.find("Mika_77"));
		check(hugo != null && mika != null, "both chats exist");
		long hugoMessages = hugo.messages.stream().filter(m -> !m.note).count();
		check(hugoMessages == 3, "Hugo: 1 in, 2 out (echo not doubled, /r goes to Hugo): " + hugoMessages);
		check(hugo.messages.stream().anyMatch(m -> m.note && m.text.contains("TPA")), "TPA request noted in Hugo's chat");
		check(hugo.unread == 1, "Hugo unread 1: " + hugo.unread);
		check(mika.messages.size() == 2 && mika.messages.get(1).out && mika.messages.get(1).failed, "message to offline Mika_77 marked as not delivered");
		check(mika.unread == 1, "Mika unread 1");
		check(context.computeOnClient(client -> SideChat.recent(50, true).size()) >= 5, "second chat has the lines");
		check(context.computeOnClient(client -> SideChat.recent(50, true).stream()
			.noneMatch(e -> e.text().contains("wie komme ich"))), "public chat stays out of the second chat");
		check(context.computeOnClient(client -> MessageFeature.tpa("Hugo", false)), "tpa sends");
		check(context.computeOnClient(client -> MessageFeature.tpaWait()) > 0, "tpa cooldown runs");
		check(!context.computeOnClient(client -> MessageFeature.tpa("Hugo", true)), "second tpa blocked during the cooldown");
		check(context.computeOnClient(client -> MessageLog.find("Hugo").messages.stream()
			.anyMatch(m -> m.note && m.text.contains("du willst zu Hugo"))), "sent TPA noted");
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.sideChatHideFromMain = true;
			SideChat.add(SideChat.Channel.SERVER, "Testzeile", "[HugoSMP] Testzeile");
		});
		check(context.computeOnClient(client -> SideChat.hideFromMain("[HugoSMP] Testzeile")), "hidden from main chat when asked");
		check(!context.computeOnClient(client -> SideChat.hideFromMain("[HugoSMP] Andere Zeile")), "other lines stay");
		context.runOnClient(client -> ModConfigManager.CONFIG.sideChatHideFromMain = false);
	}

	private static void screens(ClientGameTestContext context) {
		// The TPA cooldown of the logic test is over after a moment.
		context.waitTicks(130);
		context.runOnClient(client -> TpaState.set("Hugo", 30_000));
		context.setScreen(() -> new MessagesScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("nachrichten-chat");

		context.runOnClient(client -> {
			if (client.screen instanceof MessagesScreen screen) {
				screen.setSearchForTest("Mika");
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("nachrichten-suche");
		context.runOnClient(client -> {
			if (client.screen instanceof MessagesScreen screen) {
				screen.setSearchForTest("");
				screen.selectForTest("Mika_77");
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("nachrichten-nicht-zugestellt");

		// Type an answer and send it with Enter.
		context.getInput().typeChars("Alles klar");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(3);
		check(context.computeOnClient(client -> MessageLog.find("Mika_77").messages.stream().anyMatch(m -> m.out && m.text.equals("Alles klar"))),
			"answer typed in the screen is sent and logged");
		check(context.computeOnClient(client -> MessageLog.unreadTotal()) == 0, "viewing the chats cleared the unread counts");
		context.takeScreenshot("nachrichten-gesendet");
		context.setScreen(() -> null);

		context.runOnClient(client -> MessageLog.clear());
		context.setScreen(() -> new MessagesScreen(null));
		context.waitTicks(3);
		context.takeScreenshot("nachrichten-leer");
		context.setScreen(() -> null);

		// Second chat on the HUD.
		context.runOnClient(client -> {
			SideChat.clear();
			MessageFeature.onSystemLine("[Hugo -> mir] Hast du kurz Zeit für einen Handel?");
			MessageFeature.onSystemLine("[HugoSMP] Der Server startet in 5 Minuten neu.");
			MessageFeature.onSystemLine("[HugoSMP] Mika_77 hat dir eine Teleportations-Anfrage gesendet!");
			MessageFeature.onSystemLine("[Nachricht] Mika_77 » Du: Verkaufst du das Elytra noch? Ich würde auch mehr zahlen, wenn es verzaubert ist.");
		});
		context.waitTicks(5);
		context.takeScreenshot("zweiter-chat-hud");
		context.setScreen(() -> new ChatScreen("", false));
		context.waitTicks(3);
		context.takeScreenshot("zweiter-chat-chat-offen");
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(4);
		context.takeScreenshot("zweiter-chat-editor");
		context.setScreen(() -> null);

		context.setScreen(() -> new SettingsScreen(null).select("Chat", "Nachrichten"));
		context.waitTicks(3);
		context.takeScreenshot("nachrichten-einstellungen");
		context.setScreen(() -> null);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
