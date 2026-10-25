package com.hugosmpenhanced.client.feature.messages;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.ConfigCommand;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.streamer.ChatPatterns;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.hud.HudManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;

/**
 * Private messages and the second chat. Everything is read from the chat lines the server sends
 * and the commands the player types; nothing is ever sent on its own except what the player
 * triggers in the messages screen (a message, a TPA request).
 *
 * <p>Sent messages are logged from the typed command, so they land in the right chat whatever the
 * server's echo looks like. Received ones need the server's wording: Vanilla {@code /msg} is
 * recognised by its chat type, other wordings by the patterns {@code msg.incoming} and
 * {@code msg.outgoing} in the formats file and the whisper patterns of the streamer tools.
 */
public final class MessageFeature {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{3,17}");
	private static final long TPA_COOLDOWN_MS = 3_000;
	private static final String[] ERRORS = {"nicht online", "not online", "nicht gefunden", "not found", "existiert nicht",
		"does not exist", "kein spieler", "no player", "no such player", "unbekannter spieler", "nicht erreichbar",
		"blockiert", "blocked", "ignoriert", "ignored", "deaktiviert", "disabled", "nicht empfangen", "cannot message", "kannst keine"};

	public static KeyMapping openKey;
	private static long tpaReadyAt;
	private static long lastSentAt;

	private MessageFeature() {
	}

	public static void register() {
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hugosmpenhanced.open_messages",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_N, TpaKeybinds.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.gui.screen() == null && client.player != null) {
					client.setScreenAndShow(new MessagesScreen(null));
				}
			}
		});
		HudManager.add(SideChatHud.INSTANCE);

		ClientSendMessageEvents.COMMAND.register(MessageFeature::onCommand);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onSystemLine(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, receivedAt) -> {
			boolean incoming = params.chatType().is(ChatType.MSG_COMMAND_INCOMING);
			boolean outgoing = params.chatType().is(ChatType.MSG_COMMAND_OUTGOING);
			if (!incoming && !outgoing || !active()) {
				return;
			}
			String text = signed != null ? signed.signedContent() : message.getString();
			if (incoming) {
				String name = sender != null ? sender.name() : params.name().getString();
				receive(name, text, message.getString());
			} else if (params.targetName().isPresent()) {
				sent(params.targetName().get().getString(), text, message.getString());
			}
		});

		ConfigCommand.addSubcommand(root -> root.then(ClientCommands.literal("nachrichten")
			.executes(context -> ConfigCommand.open(MessagesScreen::new))
			.then(ClientCommands.argument("spieler", StringArgumentType.word())
				.executes(context -> {
					String name = StringArgumentType.getString(context, "spieler");
					return ConfigCommand.open(parent -> new MessagesScreen(parent, name));
				}))));
	}

	public static boolean active() {
		return ModConfigManager.CONFIG.messagesEnabled && ServerCheck.isHugoSmp();
	}

	// ------------------------------------------------------------------ typed commands

	/** A command the player sent; public for game tests. */
	public static void onCommand(String command) {
		if (!active() || command == null) {
			return;
		}
		String line = command.strip();
		if (line.startsWith("/")) {
			line = line.substring(1);
		}
		String[] parts = line.split("\\s+", 3);
		if (parts.length < 2) {
			return;
		}
		String verb = parts[0].toLowerCase(Locale.ROOT);
		if (verb.indexOf(':') > 0) {
			verb = verb.substring(verb.indexOf(':') + 1);
		}
		String name;
		String text;
		if (HugoFormats.words("msg.reply").contains(verb)) {
			name = MessageLog.lastPartner();
			text = line.substring(parts[0].length()).strip();
		} else if (HugoFormats.words("msg.commands").contains(verb) && parts.length == 3) {
			name = parts[1];
			text = parts[2];
		} else {
			return;
		}
		if (name != null && NAME.matcher(name).matches() && !text.isBlank()) {
			lastSentAt = System.currentTimeMillis();
			MessageLog.outgoing(name, text);
		}
	}

	// ------------------------------------------------------------------ chat lines

	/** One system chat line; public for game tests and the chat filter (which hides some lines before the event fires). */
	public static void onSystemLine(String raw) {
		if (!active() || raw == null) {
			return;
		}
		String line = HugoFormats.clean(raw);
		if (line.isEmpty() || line.length() > 400) {
			return;
		}
		for (Pattern pattern : HugoFormats.patterns("msg.outgoing")) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches() && HugoFormats.group(matcher, "name") != null && HugoFormats.group(matcher, "text") != null) {
				sent(matcher.group("name"), matcher.group("text").strip(), line);
				return;
			}
		}
		ChatPatterns.Whisper whisper = ChatPatterns.parseWhisper(line);
		if (whisper != null) {
			receive(whisper.name(), whisper.text(), line);
			return;
		}
		for (Pattern pattern : HugoFormats.patterns("msg.incoming")) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches() && HugoFormats.group(matcher, "name") != null && HugoFormats.group(matcher, "text") != null) {
				receive(matcher.group("name"), matcher.group("text").strip(), line);
				return;
			}
		}
		if (System.currentTimeMillis() - lastSentAt < 3_000 && line.startsWith("[") && line.length() < 140 && looksLikeError(line)) {
			MessageLog.markLastFailed();
		}
		String lower = line.toLowerCase(Locale.ROOT);
		if (lower.startsWith("[nachricht]")) {
			SideChat.add(SideChat.Channel.PRIVATE, line.substring(11).strip(), line);
		} else if (lower.startsWith("[hugosmp]")) {
			SideChat.add(SideChat.Channel.SERVER, line.substring(9).strip(), line);
		} else if (lower.startsWith("[freunde]")) {
			SideChat.add(SideChat.Channel.FRIENDS, line.substring(9).strip(), line);
		} else if (lower.startsWith("[clan]")) {
			SideChat.add(SideChat.Channel.CLAN, line.substring(6).strip(), line);
		}
	}

	private static boolean looksLikeError(String line) {
		String lower = line.toLowerCase(Locale.ROOT);
		for (String error : ERRORS) {
			if (lower.contains(error)) {
				return true;
			}
		}
		return false;
	}

	private static void receive(String name, String text, String shown) {
		MessageLog.incoming(name, text);
		SideChat.add(SideChat.Channel.PRIVATE, name + ": " + text, HugoFormats.clean(shown));
	}

	private static void sent(String name, String text, String shown) {
		MessageLog.echo(name, text);
		SideChat.add(SideChat.Channel.PRIVATE, "Du → " + name + ": " + text, HugoFormats.clean(shown));
	}

	// ------------------------------------------------------------------ actions of the screen

	/** Sends a private message the way the player would type it. */
	public static boolean send(String name, String text) {
		Minecraft client = Minecraft.getInstance();
		String body = text.strip();
		if (client.player == null || body.isEmpty() || !NAME.matcher(name).matches()) {
			return false;
		}
		client.player.connection.sendCommand("msg " + name + " " + body);
		return true;
	}

	/** Longest text that still fits a command (256 characters in total). */
	public static int maxTextLength(String name) {
		return Math.max(20, 250 - "msg ".length() - name.length() - 1);
	}

	/** Seconds left until the next TPA request may be sent, 0 when ready. */
	public static int tpaWait() {
		return (int) Math.max(0, (tpaReadyAt - System.currentTimeMillis() + 999) / 1000);
	}

	/** {@code /tpa name} (you go to them) or {@code /tpahere name} (they come to you). False while the cooldown runs. */
	public static boolean tpa(String name, boolean here) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !NAME.matcher(name).matches() || tpaWait() > 0) {
			return false;
		}
		tpaReadyAt = System.currentTimeMillis() + TPA_COOLDOWN_MS;
		client.player.connection.sendCommand((here ? "tpahere " : "tpa ") + name);
		MessageLog.note(name, here ? "TPA-Anfrage gesendet: " + name + " soll zu dir kommen" : "TPA-Anfrage gesendet: du willst zu " + name, true);
		return true;
	}

	/** Accepts the request of the player who sent one (the same as the accept key). */
	public static boolean accept(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !NAME.matcher(name).matches()) {
			return false;
		}
		client.player.connection.sendCommand("tpaccept " + name);
		com.hugosmpenhanced.client.feature.tpa.TpaState.clear();
		MessageLog.note(name, "TPA-Anfrage angenommen", false);
		return true;
	}

}
