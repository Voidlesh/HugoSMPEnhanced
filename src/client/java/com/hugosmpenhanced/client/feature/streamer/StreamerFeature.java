package com.hugosmpenhanced.client.feature.streamer;

import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.glfw.GLFW;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Streamer tools: a donation auction (bids are /pay payments, shown big on screen) and giveaways
 * (join by private message in game or in the Twitch chat, drawn with a wheel). Panel on J and
 * {@code /streamer}.
 */
public final class StreamerFeature {
	public static KeyMapping panelKey;

	private static final TwitchChat TWITCH = new TwitchChat(message ->
		Minecraft.getInstance().execute(() -> Giveaway.onTwitchMessage(message.user(), message.text(), message.subOrVip())));
	private static String twitchCandidate = "";
	private static long twitchCandidateSince;
	private static String twitchApplied = "";
	private static final Deque<String> COMMANDS = new ArrayDeque<>();
	private static int commandCooldown;
	private static int refundTotal;
	private static long lastAnnounce;
	private static String pendingAnnounce;
	private static long lastJoinSound;
	private static final String[] DEMO_NAMES = {"xXSteveXx", "Alexandra", "KaiDerBauer", "noor_mc", "Efe2010", "Sunny_"};
	private static final long[][] DEMO_BIDS = {
		{2500, 0, 15_000}, {5000, 1, 25_000}, {8000, 2, 30_000}, {11000, 0, 20_000}, {14000, 3, 50_000},
		{17000, 1, 35_000}, {20000, 4, 12_000}, {23000, 2, 40_000}, {26500, 0, 30_000}, {28500, 5, 120_000}};
	private static int demoStep;

	private StreamerFeature() {
	}

	public static void register() {
		panelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hugosmpenhanced.streamer_panel",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
			TpaKeybinds.CATEGORY));

		SpecialGuiElementRegistry.register(context -> new ItemSpinRenderer(context.vertexConsumers()));
		StreamerOverlay.register();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommandManager.literal("streamer").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				// Deferred: the chat screen closes itself right after running the command.
				client.execute(() -> client.setScreen(new StreamerScreen(null)));
				return 1;
			})));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onSystemLine(message.getString());
			}
		});
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
			if (!overlay) {
				onSystemLine(message.getString());
			}
		});
		// Vanilla /msg arrives as a signed player message of the "incoming private message" type.
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, receivedAt) -> {
			if (sender != null && signed != null && params.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
				onWhisper(new ChatPatterns.Whisper(sender.name(), signed.signedContent(), message.getString()));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(StreamerFeature::tick);
	}

	// ------------------------------------------------------------------ chat

	static void onSystemLine(String text) {
		String learned = ChatPatterns.tryLearn(text);
		if (learned != null) {
			info(Component.literal("Gelernt: ").withStyle(ChatFormatting.GREEN)
				.append(Component.literal(learned).withStyle(ChatFormatting.GRAY)));
			play(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.5f);
			return;
		}
		ChatPatterns.Payment payment = ChatPatterns.parsePayment(text);
		if (payment != null) {
			ChatPatterns.seenPayment(payment);
			DonationAuction.onPayment(payment.name(), payment.amount());
			return;
		}
		ChatPatterns.Whisper whisper = ChatPatterns.parseWhisper(text);
		if (whisper != null) {
			onWhisper(whisper);
		}
	}

	private static void onWhisper(ChatPatterns.Whisper whisper) {
		ChatPatterns.seenWhisper(whisper);
		Giveaway.onWhisper(whisper.name(), whisper.text());
	}

	// ------------------------------------------------------------------ tick

	private static void tick(Minecraft client) {
		while (panelKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new StreamerScreen(null));
			}
		}
		runDemo();
		DonationAuction.tick();
		if (commandCooldown > 0) {
			commandCooldown--;
		} else if (!COMMANDS.isEmpty() && client.player != null) {
			String command = COMMANDS.poll();
			client.player.connection.sendCommand(command);
			commandCooldown = 30;
		}
		if (pendingAnnounce != null && System.currentTimeMillis() - lastAnnounce >= 10_000) {
			sendAnnouncement(pendingAnnounce);
		}
		followTwitchSettings();
	}

	/** A channel typed in the settings takes effect once it stayed the same for a second, not per letter. */
	private static void followTwitchSettings() {
		var config = ModConfigManager.CONFIG;
		String wanted = Giveaway.open() && config.streamerTwitchJoin ? TwitchChat.normalize(config.streamerTwitchChannel) : "";
		long now = System.currentTimeMillis();
		if (!wanted.equals(twitchCandidate)) {
			twitchCandidate = wanted;
			twitchCandidateSince = now;
		} else if (!wanted.equals(twitchApplied) && now - twitchCandidateSince >= 1000) {
			twitchApplied = wanted;
			updateTwitchConnection();
		}
	}

	// ------------------------------------------------------------------ test run

	/** A 30 s auction with made-up bids, to see the overlay before going live. Nothing is paid. */
	public static void startDemo(net.minecraft.world.item.ItemStack stack, String name) {
		DonationAuction.start(stack, name, true);
		demoStep = 0;
	}

	private static void runDemo() {
		if (!DonationAuction.demo() || DonationAuction.state() != DonationAuction.State.RUNNING || demoStep >= DEMO_BIDS.length) {
			return;
		}
		long elapsed = System.currentTimeMillis() - DonationAuction.startedAt();
		if (elapsed >= DEMO_BIDS[demoStep][0]) {
			long[] bid = DEMO_BIDS[demoStep++];
			DonationAuction.onPayment(DEMO_NAMES[(int) bid[1]], bid[2]);
		}
	}

	// ------------------------------------------------------------------ auction events

	static void onBid(String name, long amount, boolean newLeader) {
		if (DonationAuction.demo()) {
			play(newLeader ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_PLING.value(), newLeader ? 1.2f : 1.5f, 0.8f);
			return;
		}
		play(SoundEvents.NOTE_BLOCK_PLING.value(), newLeader ? 2.0f : 1.5f, 0.7f);
		if (newLeader) {
			play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f);
			announce("Neues Höchstgebot: " + name + " mit " + Money.format(DonationAuction.total(name)) + "!");
		}
	}

	static void onAuctionEnded() {
		play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
		String winner = DonationAuction.winner();
		pendingAnnounce = null;
		if (DonationAuction.demo()) {
			return;
		}
		if (winner == null) {
			info(Component.literal("Auktion vorbei, es gab keine gültigen Gebote.").withStyle(ChatFormatting.GRAY));
			return;
		}
		long total = DonationAuction.total(winner);
		MutableComponent line = Component.literal(winner + " gewinnt mit " + Money.format(total) + "  ").withStyle(ChatFormatting.WHITE)
			.append(chatButton("[TPA]", "/tpa " + winner, "Zu " + winner + " teleportieren"))
			.append(Component.literal(" "))
			.append(chatButton("[Zu mir holen]", "/tpahere " + winner, winner + " zu dir holen"));
		info(line);
		if (ModConfigManager.CONFIG.streamerAnnounce) {
			sendAnnouncement("Die Auktion ist vorbei! " + winner + " gewinnt " + DonationAuction.title() + " mit " + Money.format(total) + ".");
		}
	}

	static void onAuctionStarted() {
		if (DonationAuction.demo() || !ModConfigManager.CONFIG.streamerAnnounce || Minecraft.getInstance().player == null) {
			return;
		}
		String self = Minecraft.getInstance().player.getGameProfile().name();
		sendAnnouncement("Spenden-Auktion: " + DonationAuction.title() + "! Bieten mit /pay " + self + " <Betrag>, ab "
			+ Money.format(DonationAuction.startBid()) + ", noch " + StreamerOverlay.clock(DonationAuction.endsAt() - System.currentTimeMillis()) + ".");
	}

	static void onJoin(Giveaway.Participant participant) {
		long now = System.currentTimeMillis();
		if (now - lastJoinSound > 250) {
			lastJoinSound = now;
			play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f + (float) Math.random() * 0.4f, 0.45f);
		}
	}

	// ------------------------------------------------------------------ commands and chat output

	/** Sends a command right away (TPA, /msg). */
	public static void command(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
		}
	}

	/** Pays every loser back, one /pay per 1,5 s so the server's spam protection doesn't drop any. */
	public static void refundLosers() {
		var refunds = DonationAuction.refunds();
		refunds.forEach((name, amount) -> COMMANDS.add("pay " + name + " " + amount));
		refundTotal = COMMANDS.size();
		DonationAuction.markRefunded();
		info(Component.literal("Zahle " + refunds.size() + (refunds.size() == 1 ? " Bieter" : " Bieter") + " zurück…").withStyle(ChatFormatting.GRAY));
	}

	public static int refundsLeft() {
		return COMMANDS.size();
	}

	public static int refundTotal() {
		return refundTotal;
	}

	private static void announce(String text) {
		if (!ModConfigManager.CONFIG.streamerAnnounce) {
			return;
		}
		if (System.currentTimeMillis() - lastAnnounce >= 10_000) {
			sendAnnouncement(text);
		} else {
			pendingAnnounce = text;
		}
	}

	private static void sendAnnouncement(String text) {
		Minecraft client = Minecraft.getInstance();
		pendingAnnounce = null;
		if (client.player != null) {
			client.player.connection.sendChat(text);
			lastAnnounce = System.currentTimeMillis();
		}
	}

	static void info(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal("[Streamer] ").withStyle(ChatFormatting.GOLD).append(message), false);
		}
	}

	private static MutableComponent chatButton(String label, String command, String hover) {
		return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	static void play(SoundEvent sound, float pitch, float volume) {
		if (ModConfigManager.CONFIG.streamerSounds) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
		}
	}

	// ------------------------------------------------------------------ Twitch

	/** Connected only while a giveaway is open, Twitch joining is on and a channel is set. */
	public static void updateTwitchConnection() {
		var config = ModConfigManager.CONFIG;
		String channel = TwitchChat.normalize(config.streamerTwitchChannel);
		twitchApplied = Giveaway.open() && config.streamerTwitchJoin ? channel : "";
		if (Giveaway.open() && config.streamerTwitchJoin && !channel.isEmpty()) {
			TWITCH.connect(channel);
		} else {
			TWITCH.disconnect();
		}
	}

	public static TwitchChat twitch() {
		return TWITCH;
	}
}
