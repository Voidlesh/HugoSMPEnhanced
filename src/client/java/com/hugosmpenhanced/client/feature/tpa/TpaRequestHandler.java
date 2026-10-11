package com.hugosmpenhanced.client.feature.tpa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TpaRequestHandler {
	private static final Pattern TPA_PATTERN =
			Pattern.compile("^\\[HugoSMP]\\s+([A-Za-z0-9_]{3,16})\\s+hat dir eine Teleportations.?Anfrage gesendet!");
	private static final Pattern TPAHERE_PATTERN =
			Pattern.compile("^\\[HugoSMP]\\s+([A-Za-z0-9_]{3,16})\\s+hat gefragt, ob du dich zu ihm teleportieren möchtest!");

	private static String lastMessage;
	private static long lastMessageAtMs;

	private TpaRequestHandler() {
	}

	public static void handleSystemMessage(String message) {
		if (!ModConfigManager.CONFIG.tpaToastEnabled || !ServerCheck.isHugoSmp() || isDuplicate(message)) {
			return;
		}

		Matcher matcher = TPA_PATTERN.matcher(message);
		if (matcher.find()) {
			trigger(matcher.group(1), Items.ENDER_PEARL, "hugosmpenhanced.tpa.title", "hugosmpenhanced.tpa.subtitle");
			return;
		}

		matcher = TPAHERE_PATTERN.matcher(message);
		if (matcher.find()) {
			trigger(matcher.group(1), Items.TARGET, "hugosmpenhanced.tpahere.title", "hugosmpenhanced.tpa.subtitle");
		}
	}

	private static void trigger(String player, Item icon, String titleKey, String subtitleKey) {
		TpaState.set(player, TpaState.TPA_TTL_MS);

		String keyName = TpaKeybinds.acceptKey.getTranslatedKeyMessage().getString();

		Component title = Component.translatable(titleKey, player).withStyle(ChatFormatting.BOLD);
		Component subtitle = Component.translatable(subtitleKey, keyName);

		TpaToast.show(title, subtitle, new ItemStack(icon), 5000);
	}

	private static boolean isDuplicate(String message) {
		long now = System.currentTimeMillis();
		if (message.equals(lastMessage) && (now - lastMessageAtMs) < 750) {
			return true;
		}
		lastMessage = message;
		lastMessageAtMs = now;
		return false;
	}
}
