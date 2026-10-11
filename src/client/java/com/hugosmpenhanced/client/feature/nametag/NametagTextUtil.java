package com.hugosmpenhanced.client.feature.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared helpers for finding the local player's name inside a rich-text {@link Component} (which
 * may carry a rank prefix/suffix with its own colors) so only that substring gets the nametag
 * style treatment, while the rest of the text keeps rendering exactly as the server styled it.
 */
public final class NametagTextUtil {
	private NametagTextUtil() {
	}

	/**
	 * Splits a component into its literal (text, resolved style) runs, exactly as authored/sent —
	 * e.g. a server-added rank prefix is normally its own run, separate from the player's name.
	 * Used so any run that doesn't overlap the name can be handed back to vanilla untouched, instead
	 * of being decomposed and re-emitted character by character.
	 */
	public static List<TextRun> runs(Component component) {
		List<TextRun> runs = new ArrayList<>();
		component.visit((style, literal) -> {
			if (!literal.isEmpty()) {
				runs.add(new TextRun(literal, style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return runs;
	}

	/** Flattens an already visually-ordered char sequence (e.g. one wrapped chat line) the same way. */
	public static List<CharSample> flatten(FormattedCharSequence sequence) {
		List<CharSample> samples = new ArrayList<>();
		sequence.accept((index, style, codePoint) -> {
			for (char c : Character.toChars(codePoint)) {
				samples.add(new CharSample(c, style));
			}
			return true;
		});
		return samples;
	}

	/** Length of a match of {@code name} starting at {@code start}, or 0 if it doesn't match there. */
	public static int matchLenAt(List<CharSample> samples, int start, String name) {
		if (name.isEmpty() || start + name.length() > samples.size()) {
			return 0;
		}
		for (int k = 0; k < name.length(); k++) {
			if (samples.get(start + k).ch() != name.charAt(k)) {
				return 0;
			}
		}
		return name.length();
	}

	public static int styleColor(Style style, int fallbackArgb) {
		TextColor color = style.getColor();
		return color != null ? (0xFF000000 | color.getValue()) : fallbackArgb;
	}

	public record CharSample(char ch, Style style) {
	}

	public record TextRun(String text, Style style) {
	}
}
