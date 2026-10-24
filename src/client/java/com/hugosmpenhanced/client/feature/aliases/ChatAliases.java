package com.hugosmpenhanced.client.feature.aliases;

import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;

/**
 * Chat aliases: a message that is exactly an alias name (ignoring case and spaces at the ends) is
 * replaced by the alias text when it is sent. Commands are never replaced, and nothing is replaced
 * while typing, only on Enter.
 */
public final class ChatAliases {
	/** Longest chat message in vanilla Minecraft; the text of an alias must fit into one message. */
	public static final int MAX_TEXT = 256;
	public static final int MAX_NAME = 24;
	private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_\\-]{1," + MAX_NAME + "}");

	private ChatAliases() {
	}

	/** The alias that the whole input names, or null. */
	public static ChatAlias find(String input) {
		if (!ModConfigManager.CONFIG.chatAliasesEnabled) {
			return null;
		}
		String typed = input.trim();
		if (typed.isEmpty() || typed.startsWith("/")) {
			return null;
		}
		for (ChatAlias alias : ModConfigManager.CONFIG.chatAliases) {
			if (alias != null && isUsable(alias) && alias.name.trim().equalsIgnoreCase(typed)) {
				return alias;
			}
		}
		return null;
	}

	/** The text to send for this input: the alias text if the input is an alias, else the input. */
	public static String expand(String input) {
		ChatAlias alias = find(input);
		if (alias == null) {
			return input;
		}
		String text = alias.text.trim();
		return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
	}

	/** An alias with a valid name and some text; broken entries from a hand-edited config are skipped. */
	public static boolean isUsable(ChatAlias alias) {
		return nameError(alias.name, alias) == null && !alias.text.isBlank();
	}

	/** Why this name cannot be saved, or null. {@code self} is the alias being edited, so it may keep its own name. */
	public static String nameError(String name, ChatAlias self) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			return "Ein Kürzel eingeben";
		}
		if (!NAME.matcher(trimmed).matches()) {
			return "Nur Buchstaben, Zahlen, _ und -, höchstens " + MAX_NAME + " Zeichen";
		}
		for (ChatAlias alias : ModConfigManager.CONFIG.chatAliases) {
			if (alias != self && alias != null && alias.name.trim().equalsIgnoreCase(trimmed)) {
				return "Dieses Kürzel gibt es schon";
			}
		}
		return null;
	}

	/** Why this text cannot be saved, or null. */
	public static String textError(String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return "Einen Text eingeben, der gesendet wird";
		}
		if (trimmed.length() > MAX_TEXT) {
			return "Zu lang: " + trimmed.length() + " von " + MAX_TEXT + " Zeichen";
		}
		return null;
	}
}
