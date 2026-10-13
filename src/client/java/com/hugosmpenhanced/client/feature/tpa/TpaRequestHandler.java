package com.hugosmpenhanced.client.feature.tpa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuardChat;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

		HugoGuard.Entry listed = ModConfigManager.CONFIG.hugoGuardTpa ? HugoGuard.shown(player) : null;
		if (listed != null && listed.status() == HugoGuard.Status.SCAMMER) {
			warnScammer(listed);
			return;
		}

		Component title = Component.translatable(titleKey, player).withStyle(ChatFormatting.BOLD);
		// Without a key the toast would read "Press Not Bound to accept".
		Component subtitle = TpaKeybinds.acceptKey.isUnbound()
			? Component.literal("/tpaccept " + player)
			: Component.translatable(subtitleKey, TpaKeybinds.acceptKey.getTranslatedKeyMessage().getString());
		if (listed != null) {
			subtitle = Component.literal("✔ HugoGuard: vertrauenswürdig").withStyle(ChatFormatting.GREEN);
		}

		TpaToast.show(title, subtitle, new ItemStack(icon), 5000);
	}

	/** A red toast and a chat line instead of the normal toast; the accept key still works. */
	public static void warnScammer(HugoGuard.Entry scammer) {
		Component title = Component.literal("⚠ Scammer: " + scammer.username()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		Component subtitle = Component.literal("Laut HugoGuard: " + scammer.reports() + " Meldung(en)");
		TpaToast.show(title, subtitle, new ItemStack(Items.BARRIER), 8000, TpaToast.WARNING_COLOR);
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> minecraft.gui.getChat().addMessage(Component.empty().append(HugoGuardChat.tag(scammer))
			.append(Component.literal(" Vorsicht: Die TPA-Anfrage kommt von " + scammer.username()
				+ ", der auf HugoGuard als Scammer gelistet ist.").withStyle(ChatFormatting.RED))));
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
			net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F));
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
