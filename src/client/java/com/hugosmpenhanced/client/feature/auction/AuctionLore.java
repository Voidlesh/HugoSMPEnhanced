package com.hugosmpenhanced.client.feature.auction;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuctionLore {
	private static final Pattern PRICE_LINE = Pattern.compile("^Preis:\\s*\\$([\\d,]+(?:\\.\\d+)?)$");

	private AuctionLore() {
	}

	public static Optional<Double> parsePrice(String plainLine) {
		Matcher matcher = PRICE_LINE.matcher(plainLine.strip());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(Double.parseDouble(matcher.group(1).replace(",", "")));
	}
}
