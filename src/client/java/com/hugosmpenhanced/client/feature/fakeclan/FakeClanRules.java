package com.hugosmpenhanced.client.feature.fakeclan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fake-clan rules without any Minecraft types, so they can be checked on their own.
 *
 * <p>A clan tag is the bracket in a player's name suffix, as in {@code Name [Clan]}. Clans can carry a
 * rank as a second bracket, so when a suffix has several brackets the last one is left out and the
 * clan is the one before it.
 *
 * <p>A tag is fake when it is on the manual fake list (exact name, ignoring case), or when it looks
 * like a real clan without being one: identical after folding the usual look-alike characters
 * (0 and O, 1, l, I and |, 3 and E, 5 and S), or within a small edit distance of a real clan. Edit
 * distance is allowed for longer names only, since short names differ by one letter too easily.
 * A manual fake is always flagged, even when the same name is also on the real list; otherwise an
 * exact match with a real clan is never fake.
 */
public final class FakeClanRules {
	private static final Pattern BRACKET = Pattern.compile("\\[([^\\[\\]]{1,24})]");

	/** A fake tag: the tag as written, and the real clan it imitates, or null when it was added by hand. */
	public record Match(String clan, String imitates) {
		public boolean manual() {
			return this.imitates == null;
		}
	}

	private FakeClanRules() {
	}

	/** The clan tag in a name suffix, or null if there is none. */
	public static String clanOf(String suffix) {
		if (suffix == null) {
			return null;
		}
		List<String> brackets = new ArrayList<>();
		Matcher matcher = BRACKET.matcher(suffix);
		while (matcher.find()) {
			brackets.add(matcher.group(1).trim());
		}
		if (brackets.size() > 1) {
			brackets.removeLast();
		}
		if (brackets.isEmpty() || brackets.getLast().isEmpty()) {
			return null;
		}
		return brackets.getLast();
	}

	/** Whether the clan tag is fake, given the real and the manual fake clan names; null if it is not. */
	public static Match check(String clan, Collection<String> real, Collection<String> fake) {
		if (clan == null || clan.isBlank()) {
			return null;
		}
		String tag = clan.trim();
		for (String name : fake) {
			if (name != null && name.trim().equalsIgnoreCase(tag)) {
				return new Match(tag, null);
			}
		}
		for (String name : real) {
			if (name != null && name.trim().equalsIgnoreCase(tag)) {
				return null;
			}
		}
		String folded = fold(tag);
		for (String name : real) {
			if (name == null || name.isBlank()) {
				continue;
			}
			String realFolded = fold(name.trim());
			if (distance(folded, realFolded) <= allowedEdits(realFolded)) {
				return new Match(tag, name.trim());
			}
		}
		return null;
	}

	/** Lower case, with the look-alike characters mapped to one letter each. */
	static String fold(String text) {
		StringBuilder folded = new StringBuilder(text.length());
		for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
			folded.append(switch (c) {
				case '0' -> 'o';
				case '1', '|', '!', 'i' -> 'l';
				case '3' -> 'e';
				case '5' -> 's';
				default -> c;
			});
		}
		return folded.toString();
	}

	/** How many single-character edits still count as a look-alike, by the length of the real name. */
	static int allowedEdits(String folded) {
		if (folded.length() <= 4) {
			return 0;
		}
		return folded.length() <= 8 ? 1 : 2;
	}

	/** Optimal string alignment distance: insert, delete, replace and swap of two neighbours each cost 1. */
	static int distance(String a, String b) {
		int[][] d = new int[a.length() + 1][b.length() + 1];
		for (int i = 0; i <= a.length(); i++) {
			d[i][0] = i;
		}
		for (int j = 0; j <= b.length(); j++) {
			d[0][j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		return d[a.length()][b.length()];
	}
}
