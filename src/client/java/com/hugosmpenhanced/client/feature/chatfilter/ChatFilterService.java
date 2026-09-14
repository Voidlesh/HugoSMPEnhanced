package com.hugosmpenhanced.client.feature.chatfilter;

import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;
import net.minecraft.client.Minecraft;

/**
 * Categorizes HugoSMP chat lines by their known prefixes and decides whether a
 * message should be hidden based on the current chat-filter config toggles.
 */
public final class ChatFilterService {
	// Not togglable categories on their own — they only exist so timestamped log
	// lines and continuation lines aren't misclassified as "Public Chat".
	private static final Pattern TIMESTAMP_LINE =
			Pattern.compile("^\\s*\\[\\d{2}\\.\\d{2}\\.\\d{2}]\\s*\\(\\d{2}:\\d{2}\\).*");
	private static final Pattern CONTINUATION_LINE = Pattern.compile("^- .*");

	private static final ChatFilterRule[] CATEGORY_RULES = {
			new ChatFilterRule(Pattern.compile("^\\[HugoSMP].*", Pattern.CASE_INSENSITIVE),
					() -> ModConfigManager.CONFIG.systemChatEnabled),
			new ChatFilterRule(Pattern.compile("^\\[Nachricht].*", Pattern.CASE_INSENSITIVE),
					() -> ModConfigManager.CONFIG.msgChatEnabled),
			new ChatFilterRule(Pattern.compile("^\\[Freunde].*", Pattern.CASE_INSENSITIVE),
					() -> ModConfigManager.CONFIG.friendChatEnabled),
			new ChatFilterRule(Pattern.compile("^\\[Clan].*", Pattern.CASE_INSENSITIVE),
					() -> ModConfigManager.CONFIG.clanChatEnabled),
	};

	private ChatFilterService() {
	}

	public static boolean shouldCancel(String text) {
		if (isOwnMessage(text)) {
			return false;
		}

		if (isPublicChat(text)) {
			return !ModConfigManager.CONFIG.publicChatEnabled;
		}

		for (ChatFilterRule rule : CATEGORY_RULES) {
			if (!rule.isVisible() && rule.pattern().matcher(text).find()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPublicChat(String text) {
		for (ChatFilterRule rule : CATEGORY_RULES) {
			if (rule.pattern().matcher(text).find()) {
				return false;
			}
		}
		return !TIMESTAMP_LINE.matcher(text).find() && !CONTINUATION_LINE.matcher(text).find();
	}

	private static boolean isOwnMessage(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return false;
		}
		String name = client.player.getGameProfile().name();
		return Pattern.compile("^(\\[[^]]+])?\\s*" + Pattern.quote(name) + ".*").matcher(text).find();
	}
}
