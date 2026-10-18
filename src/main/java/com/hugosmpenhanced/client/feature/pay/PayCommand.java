package com.hugosmpenhanced.client.feature.pay;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.formats.HugoFormats;

/**
 * A typed {@code /pay Name Betrag}, also half typed. The command words come from the formats file
 * ({@code pay.commands}), so another alias of the server can be added without a new jar.
 */
public record PayCommand(String command, String name, int nameStart, int nameEnd, String amountText, long amount) {
	private static final Pattern SHAPE = Pattern.compile("^/(\\S+)\\s+([A-Za-z0-9_]{1,16})(?=\\s|$)(?:\\s+(\\S+))?");

	/** The command in a chat input ("/pay ..."), or null when it is not a payment. */
	public static PayCommand parse(String input) {
		if (input == null || !input.startsWith("/")) {
			return null;
		}
		Matcher matcher = SHAPE.matcher(input);
		if (!matcher.find()) {
			return null;
		}
		String command = matcher.group(1).toLowerCase(Locale.ROOT);
		int colon = command.indexOf(':');
		if (colon >= 0) {
			// "/essentials:pay"
			command = command.substring(colon + 1);
		}
		if (!isPayWord(command)) {
			return null;
		}
		String amountText = matcher.group(3);
		long amount = amountText == null ? -1 : Money.parse(amountText);
		return new PayCommand(command, matcher.group(2), matcher.start(2), matcher.end(2), amountText, amount);
	}

	/** Same as {@link #parse}, for a sent command without the slash. */
	public static PayCommand parseSent(String command) {
		return command == null ? null : parse("/" + command);
	}

	private static boolean isPayWord(String command) {
		for (String word : HugoFormats.words("pay.commands")) {
			if (word.equalsIgnoreCase(command)) {
				return true;
			}
		}
		return false;
	}
}
