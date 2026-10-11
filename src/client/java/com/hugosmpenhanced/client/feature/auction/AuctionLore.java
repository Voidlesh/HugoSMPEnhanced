package com.hugosmpenhanced.client.feature.auction;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuctionLore {
	/** "Preis: $7.000", "Preis pro Stück: $12,50" and the like; group 1 is the label after "Preis". */
	private static final Pattern PRICE_LINE = Pattern.compile("^Preis([^:$]*):\\s*\\$\\s*([\\d.,]+)\\s*$", Pattern.CASE_INSENSITIVE);

	private AuctionLore() {
	}

	/** A price read from a tooltip line, and whether the line already gives it per single item. */
	public record Price(double amount, boolean perItem) {
	}

	public static Optional<Price> parse(String plainLine) {
		Matcher matcher = PRICE_LINE.matcher(plainLine.strip());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		Double amount = parseAmount(matcher.group(2));
		if (amount == null) {
			return Optional.empty();
		}
		String label = matcher.group(1).toLowerCase();
		boolean perItem = label.contains("pro") || label.contains("stück") || label.contains("item");
		return Optional.of(new Price(amount, perItem));
	}

	public static Optional<Double> parsePrice(String plainLine) {
		return parse(plainLine).map(Price::amount);
	}

	/**
	 * Reads "7.000", "7,000", "7.000,50", "1,234.5" and "12,50". A separator followed by exactly
	 * three digits groups thousands; otherwise the last separator is the decimal point.
	 */
	static Double parseAmount(String text) {
		text = text.replaceAll("[.,]+$", "");
		if (text.isEmpty()) {
			return null;
		}
		int lastDot = text.lastIndexOf('.');
		int lastComma = text.lastIndexOf(',');
		int last = Math.max(lastDot, lastComma);
		String digits;
		if (last < 0) {
			digits = text;
		} else {
			boolean bothKinds = lastDot >= 0 && lastComma >= 0;
			int decimals = text.length() - last - 1;
			boolean lastIsDecimal = bothKinds || decimals != 3;
			if (lastIsDecimal) {
				digits = text.substring(0, last).replaceAll("[.,]", "") + "." + text.substring(last + 1);
			} else {
				digits = text.replaceAll("[.,]", "");
			}
		}
		try {
			return Double.parseDouble(digits);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
