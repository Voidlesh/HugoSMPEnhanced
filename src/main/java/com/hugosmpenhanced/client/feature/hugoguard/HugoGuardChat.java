package com.hugosmpenhanced.client.feature.hugoguard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Puts a red "⚠ SCAMMER" or green "✔ TRUSTED" tag in front of chat lines whose sender is on a
 * HugoGuard list. The sender
 * is the first name after the leading bracket tags, as in "[Clan] [Rang] Name: …" or
 * "[Nachricht] Name -> Du: …"; a scammer merely mentioned later in a line is not marked.
 */
public final class HugoGuardChat {
	private static final Pattern SENDER = Pattern.compile("^\\s*(?:\\[[^\\]]{1,32}]\\s*){0,4}([A-Za-z0-9_]{3,16})\\b");

	private HugoGuardChat() {
	}

	public static Component mark(Component message) {
		if (!ModConfigManager.CONFIG.hugoGuardChat || !HugoGuard.active()) {
			return message;
		}
		Matcher matcher = SENDER.matcher(message.getString());
		if (!matcher.find()) {
			return message;
		}
		HugoGuard.Entry entry = HugoGuard.shown(matcher.group(1));
		if (entry == null) {
			return message;
		}
		return Component.empty().append(tag(entry)).append(" ").append(message);
	}

	/**
	 * The clickable tag, red for scammers and green for trusted players; hovering shows the details,
	 * clicking opens the HugoGuard profile.
	 */
	public static MutableComponent tag(HugoGuard.Entry entry) {
		boolean scammer = entry.status() == HugoGuard.Status.SCAMMER;
		ChatFormatting color = scammer ? ChatFormatting.RED : ChatFormatting.GREEN;
		MutableComponent hover = Component.literal(entry.username() + " ist auf HugoGuard als " + (scammer ? "Scammer" : "vertrauenswürdig") + " gelistet")
			.withStyle(color)
			.append(Component.literal(scammer
				? "\n" + entry.reports() + " bestätigte Meldung(en)"
				: "\nReputation: " + entry.repScore()).withStyle(ChatFormatting.GRAY));
		if (entry.label() != null && !entry.label().isBlank()) {
			hover.append(Component.literal("\n" + entry.label()).withStyle(ChatFormatting.GOLD));
		}
		hover.append(Component.literal("\nKlick: Profil öffnen").withStyle(ChatFormatting.DARK_GRAY));
		return Component.literal(scammer ? "⚠ SCAMMER" : "✔ TRUSTED").setStyle(Style.EMPTY.withColor(color).withBold(true)
			.withHoverEvent(new HoverEvent.ShowText(hover))
			.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(entry.profileUrl()))));
	}
}
