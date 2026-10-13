package com.hugosmpenhanced.client.feature.mentions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.chatfilter.ChatFilterService;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class MentionHighlightFeature {
	private MentionHighlightFeature() {
	}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, boundChatType, receptionTimestamp) -> {
			Minecraft client = Minecraft.getInstance();
			// Own messages contain the own name, so they would always "mention" you.
			if (client.player == null || sender != null && sender.id().equals(client.player.getUUID())) {
				return;
			}
			check(message.getString(), sender != null ? sender.name() : null);
		});
		// Servers with chat plugins send player chat as system lines, without a sender.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			if (!overlay && !ChatFilterService.isSystemLine(text) && !ChatFilterService.isOwnMessage(text)) {
				check(text, null);
			}
		});
	}

	private static void check(String text, String senderName) {
		if (!ModConfigManager.CONFIG.mentionsEnabled || !ServerCheck.isHugoSmp()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		List<String> triggers = new ArrayList<>(ModConfigManager.CONFIG.mentionTriggers);
		triggers.add(client.player.getGameProfile().name());
		if (!matchesAnyTrigger(text, triggers)) {
			return;
		}

		if (ModConfigManager.CONFIG.mentionsSoundEnabled) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0f));
		}

		client.gui.getChat().addMessage(senderName != null
			? Component.literal("» ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(senderName).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" hat dich erwähnt").withStyle(ChatFormatting.GOLD))
			: Component.literal("» ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal("Du wurdest erwähnt").withStyle(ChatFormatting.GOLD)));
	}

	private static boolean matchesAnyTrigger(String text, List<String> triggers) {
		for (String trigger : triggers) {
			if (trigger == null || trigger.isBlank()) {
				continue;
			}
			Pattern pattern = Pattern.compile("\\b" + Pattern.quote(trigger) + "\\b", Pattern.CASE_INSENSITIVE);
			if (pattern.matcher(text).find()) {
				return true;
			}
		}
		return false;
	}
}
