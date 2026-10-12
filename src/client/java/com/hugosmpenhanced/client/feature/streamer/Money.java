package com.hugosmpenhanced.client.feature.streamer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.feature.auction.AuctionLore;

/** Reads and writes money amounts the way HugoSMP shows them: "$20.000", "1,5k", "2 Mio $". */
public final class Money {
	/** An amount inside a chat line, for use in bigger patterns. Needs at least one digit. */
	public static final String AMOUNT_REGEX = "\\$?\\s?\\d[\\d.,]*(?:\\s?(?:mrd|mio|tsd|[kmb])(?![a-zäöü]))?\\s?\\$?";
	private static final Pattern NUMBER = Pattern.compile("(\\d[\\d.,]*)\\s?(mrd|mio|tsd|[kmb])?(?![a-zäöü])", Pattern.CASE_INSENSITIVE);

	private Money() {
	}

	/** The amount in a piece of text, or -1 when there is none. */
	public static long parse(String text) {
		if (text == null) {
			return -1;
		}
		Matcher matcher = NUMBER.matcher(text);
		if (!matcher.find()) {
			return -1;
		}
		String digits = matcher.group(1).replaceAll("[.,]+$", "");
		String suffix = matcher.group(2);
		Double value;
		if (suffix != null && digits.matches("\\d+[.,]\\d+")) {
			// With a suffix one separator is always the decimal point: "1.500M" is 1,5 million.
			value = Double.parseDouble(digits.replace(',', '.'));
		} else {
			value = AuctionLore.parseAmount(digits);
		}
		if (value == null) {
			return -1;
		}
		if (suffix != null) {
			value *= switch (suffix.toLowerCase(Locale.ROOT)) {
				case "k", "tsd" -> 1_000;
				case "m", "mio" -> 1_000_000;
				case "b", "mrd" -> 1_000_000_000;
				default -> 1;
			};
		}
		return Math.round(value);
	}

	public static String format(long amount) {
		return "$" + new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount);
	}

	/** Without the dollar sign, for text fields: "10.000". */
	public static String plain(long amount) {
		return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount);
	}
}
