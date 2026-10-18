package com.hugosmpenhanced.client.feature.colorcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Text with {@code &} colour codes as a list of styled characters, so colours and formats can be
 * changed for a part of it and written back as codes. Reads {@code &0-&f}, {@code &k-&o},
 * {@code &r}, {@code &#RRGGBB} and {@code &x&R&R&G&G&B&B} (also with {@code §}); like on the
 * server, a colour code clears active formatting. Writing picks the shortest codes that give the
 * same look, so a gradient skips spaces and repeats nothing.
 */
public final class ColorText {
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int UNDERLINE = 4;
	public static final int STRIKE = 8;
	public static final int OBFUSCATED = 16;

	/** Format bits in code order, with their code letter. */
	private static final int[] FORMAT_BITS = {OBFUSCATED, BOLD, STRIKE, UNDERLINE, ITALIC};
	private static final char[] FORMAT_CODES = {'k', 'l', 'm', 'n', 'o'};

	/** A colour: one of the 16 chat colours (with its code) or any RGB value (code 0). */
	public record Color(int rgb, char code) {
		public static Color legacy(ChatFormatting formatting) {
			return new Color(formatting.getColor() & 0xFFFFFF, formatting.getChar());
		}

		public static Color hex(int rgb) {
			return new Color(rgb & 0xFFFFFF, (char) 0);
		}

		public boolean isLegacy() {
			return this.code != 0;
		}

		public String hexString() {
			return String.format("#%06X", this.rgb);
		}
	}

	/** One visible character, its look, and where it sits in the raw text. */
	public static final class Glyph {
		public final String text;
		public Color color;
		public int formats;
		public int rawStart;

		Glyph(String text, Color color, int formats, int rawStart) {
			this.text = text;
			this.color = color;
			this.formats = formats;
			this.rawStart = rawStart;
		}

		public boolean isSpace() {
			return this.text.equals(" ");
		}
	}

	public final List<Glyph> glyphs;
	/** Codes after the last character, kept as typed. */
	private final String tail;
	/** Colour and formats active at the end of the text. */
	public final Color endColor;
	public final int endFormats;

	private ColorText(List<Glyph> glyphs, String tail, Color endColor, int endFormats) {
		this.glyphs = glyphs;
		this.tail = tail;
		this.endColor = endColor;
		this.endFormats = endFormats;
	}

	// ------------------------------------------------------------------ reading

	public static ColorText parse(String raw) {
		List<Glyph> glyphs = new ArrayList<>();
		Color color = null;
		int formats = 0;
		int tailStart = 0;
		int i = 0;
		while (i < raw.length()) {
			int code = codeLength(raw, i);
			if (code > 0) {
				char c = Character.toLowerCase(raw.charAt(i + 1));
				if (code == 8) {
					color = Color.hex(Integer.parseInt(raw.substring(i + 2, i + 8), 16));
					formats = 0;
				} else if (code == 14) {
					StringBuilder hex = new StringBuilder();
					for (int k = 0; k < 6; k++) {
						hex.append(raw.charAt(i + 3 + 2 * k));
					}
					color = Color.hex(Integer.parseInt(hex.toString(), 16));
					formats = 0;
				} else if (c == 'r') {
					color = null;
					formats = 0;
				} else {
					ChatFormatting formatting = ChatFormatting.getByCode(c);
					if (formatting.isColor()) {
						color = Color.legacy(formatting);
						formats = 0;
					} else {
						formats |= formatBit(c);
					}
				}
				i += code;
				continue;
			}
			int cp = raw.codePointAt(i);
			int len = Character.charCount(cp);
			glyphs.add(new Glyph(raw.substring(i, i + len), color, formats, i));
			i += len;
			tailStart = i;
		}
		return new ColorText(glyphs, raw.substring(tailStart), color, formats);
	}

	/** Length of the code starting at {@code i} (2, 8 or 14), or 0 if there is none. */
	public static int codeLength(String raw, int i) {
		char prefix = raw.charAt(i);
		if ((prefix != '&' && prefix != '§') || i + 1 >= raw.length()) {
			return 0;
		}
		char c = Character.toLowerCase(raw.charAt(i + 1));
		if (c == '#' && i + 8 <= raw.length() && isHex(raw, i + 2, 6)) {
			return 8;
		}
		if (c == 'x' && i + 14 <= raw.length()) {
			boolean ok = true;
			for (int k = 0; k < 6 && ok; k++) {
				char p = raw.charAt(i + 2 + 2 * k);
				ok = (p == '&' || p == '§') && Character.digit(raw.charAt(i + 3 + 2 * k), 16) >= 0;
			}
			if (ok) {
				return 14;
			}
		}
		if (c == 'r' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o')) {
			return 2;
		}
		return 0;
	}

	private static boolean isHex(String raw, int from, int count) {
		for (int k = from; k < from + count; k++) {
			if (Character.digit(raw.charAt(k), 16) < 0) {
				return false;
			}
		}
		return true;
	}

	private static int formatBit(char code) {
		for (int k = 0; k < FORMAT_CODES.length; k++) {
			if (FORMAT_CODES[k] == code) {
				return FORMAT_BITS[k];
			}
		}
		return 0;
	}

	public static String formatCode(int bit) {
		for (int k = 0; k < FORMAT_BITS.length; k++) {
			if (FORMAT_BITS[k] == bit) {
				return "&" + FORMAT_CODES[k];
			}
		}
		return "";
	}

	/** Index of the first character at or after the raw position (the visible cursor). */
	public int glyphIndexAt(int rawPos) {
		int index = 0;
		for (Glyph glyph : this.glyphs) {
			if (glyph.rawStart >= rawPos) {
				break;
			}
			index++;
		}
		return index;
	}

	/** The text without any codes. */
	public String plain() {
		StringBuilder out = new StringBuilder();
		for (Glyph glyph : this.glyphs) {
			out.append(glyph.text);
		}
		return out.toString();
	}

	public boolean hasStyle() {
		for (Glyph glyph : this.glyphs) {
			if (glyph.color != null || glyph.formats != 0) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ writing

	/** Writes the text back with codes; {@link Glyph#rawStart} is updated to the new positions. */
	public String serialize(HexStyle hexStyle) {
		StringBuilder out = new StringBuilder();
		Color current = null;
		int currentFormats = 0;
		for (Glyph glyph : this.glyphs) {
			Color want = glyph.color;
			int wantFormats = glyph.formats;
			if (hexStyle == HexStyle.LEGACY_ONLY && want != null && !want.isLegacy()) {
				want = nearestLegacy(want.rgb);
			}
			// A plain space shows no colour, so it never needs a colour code of its own.
			boolean invisible = glyph.isSpace() && (wantFormats & (UNDERLINE | STRIKE | OBFUSCATED)) == 0;
			if (invisible && ((wantFormats ^ currentFormats) & ~ITALIC) == 0) {
				glyph.rawStart = out.length();
				out.append(glyph.text);
				continue;
			}
			if (!Objects.equals(want, current)) {
				out.append(want == null ? "&r" : colorCode(want, hexStyle));
				current = want;
				currentFormats = 0;
			}
			if ((currentFormats & ~wantFormats) != 0) {
				out.append("&r");
				if (current != null) {
					out.append(colorCode(current, hexStyle));
				}
				currentFormats = 0;
			}
			for (int bit : FORMAT_BITS) {
				if ((wantFormats & bit) != 0 && (currentFormats & bit) == 0) {
					out.append(formatCode(bit));
				}
			}
			currentFormats = wantFormats;
			glyph.rawStart = out.length();
			out.append(glyph.text);
		}
		out.append(this.tail);
		return out.toString();
	}

	public static String colorCode(Color color, HexStyle hexStyle) {
		if (color.isLegacy()) {
			return "&" + color.code;
		}
		return switch (hexStyle) {
			case AMPERSAND_HASH -> "&#" + String.format("%06x", color.rgb);
			case AMPERSAND_X -> {
				StringBuilder out = new StringBuilder("&x");
				for (char c : String.format("%06x", color.rgb).toCharArray()) {
					out.append('&').append(c);
				}
				yield out.toString();
			}
			case LEGACY_ONLY -> "&" + nearestLegacy(color.rgb).code;
		};
	}

	/** The chat colour closest to an RGB value, for servers without hex colours. */
	public static Color nearestLegacy(int rgb) {
		Color best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ChatFormatting formatting : ChatFormatting.values()) {
			if (!formatting.isColor()) {
				continue;
			}
			double distance = ColorMath.distance(rgb, formatting.getColor());
			if (distance < bestDistance) {
				bestDistance = distance;
				best = Color.legacy(formatting);
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ changing

	public void setColor(int from, int to, Color color) {
		for (int i = from; i < to; i++) {
			this.glyphs.get(i).color = color;
		}
	}

	/** Turns a format on for the range, or off if every character already has it. */
	public void toggleFormat(int from, int to, int bit) {
		boolean all = true;
		for (int i = from; i < to; i++) {
			all &= (this.glyphs.get(i).formats & bit) != 0;
		}
		for (int i = from; i < to; i++) {
			Glyph glyph = this.glyphs.get(i);
			glyph.formats = all ? glyph.formats & ~bit : glyph.formats | bit;
		}
	}

	public boolean allHave(int from, int to, int bit) {
		if (from >= to) {
			return false;
		}
		for (int i = from; i < to; i++) {
			if ((this.glyphs.get(i).formats & bit) == 0) {
				return false;
			}
		}
		return true;
	}

	public void clearStyle(int from, int to) {
		for (int i = from; i < to; i++) {
			this.glyphs.get(i).color = null;
			this.glyphs.get(i).formats = 0;
		}
	}

	/** Colours the range from one colour to the other, skipping spaces so the ends land on letters. */
	public void gradient(int from, int to, int... stops) {
		this.steppedGradient(from, to, 1, stops);
	}

	/**
	 * Like {@link #gradient(int, int, int...)}, but {@code step} letters in a row share a colour,
	 * which needs fewer codes for long texts.
	 */
	public void steppedGradient(int from, int to, int step, int... stops) {
		List<Glyph> letters = new ArrayList<>();
		for (int i = from; i < to; i++) {
			if (!this.glyphs.get(i).isSpace()) {
				letters.add(this.glyphs.get(i));
			}
		}
		int groups = (letters.size() + step - 1) / step;
		for (int k = 0; k < letters.size(); k++) {
			int group = k / step;
			float t = groups <= 1 ? 0 : group / (float) (groups - 1);
			letters.get(k).color = Color.hex(ColorMath.sample(stops, t));
		}
	}

	/**
	 * The chat colours a gradient from one chat colour to another passes through, in order. Grey,
	 * black and white are only used as ends, so a gradient never turns muddy in the middle.
	 */
	public static List<ChatFormatting> legacyPath(ChatFormatting from, ChatFormatting to) {
		List<ChatFormatting> path = new ArrayList<>();
		path.add(from);
		int a = from.getColor();
		int b = to.getColor();
		for (int k = 1; k < 64; k++) {
			int mixed = ColorMath.mix(a, b, k / 64f);
			ChatFormatting best = null;
			double bestDistance = Double.MAX_VALUE;
			for (ChatFormatting formatting : ChatFormatting.values()) {
				if (!formatting.isColor() || (isNeutral(formatting) && formatting != from && formatting != to)) {
					continue;
				}
				double distance = ColorMath.distance(mixed, formatting.getColor());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = formatting;
				}
			}
			if (best != path.get(path.size() - 1) && !path.contains(best)) {
				path.add(best);
			}
		}
		if (path.get(path.size() - 1) != to) {
			path.remove(to);
			path.add(to);
		}
		return path;
	}

	private static boolean isNeutral(ChatFormatting formatting) {
		return formatting == ChatFormatting.BLACK || formatting == ChatFormatting.DARK_GRAY
			|| formatting == ChatFormatting.GRAY || formatting == ChatFormatting.WHITE;
	}

	/**
	 * Colours the letters of the range in blocks along {@link #legacyPath}, using at most
	 * {@code maxColors} of its colours (always both ends). Returns how many colours were used.
	 */
	public int legacyGradient(int from, int to, ChatFormatting start, ChatFormatting end, int maxColors) {
		List<ChatFormatting> path = legacyPath(start, end);
		List<Glyph> letters = new ArrayList<>();
		for (int i = from; i < to; i++) {
			if (!this.glyphs.get(i).isSpace()) {
				letters.add(this.glyphs.get(i));
			}
		}
		int colors = Math.max(1, Math.min(Math.min(maxColors, path.size()), letters.size()));
		List<ChatFormatting> used = new ArrayList<>();
		for (int c = 0; c < colors; c++) {
			used.add(path.get(colors == 1 ? 0 : Math.round(c * (path.size() - 1) / (float) (colors - 1))));
		}
		int n = letters.size();
		for (int k = 0; k < n; k++) {
			letters.get(k).color = Color.legacy(used.get(k * colors / n));
		}
		return colors;
	}

	/**
	 * Gives the range the look of another text: colours and formats are taken from the same relative
	 * position, so a gradient template stretches over any length.
	 */
	public void applyStyleOf(ColorText template, int from, int to) {
		List<Glyph> source = new ArrayList<>();
		for (Glyph glyph : template.glyphs) {
			if (!glyph.isSpace()) {
				source.add(glyph);
			}
		}
		if (source.isEmpty()) {
			return;
		}
		List<Glyph> letters = new ArrayList<>();
		for (int i = from; i < to; i++) {
			if (!this.glyphs.get(i).isSpace()) {
				letters.add(this.glyphs.get(i));
			}
		}
		int n = source.size();
		int m = letters.size();
		for (int k = 0; k < m; k++) {
			double pos = m == 1 ? 0 : k * (n - 1) / (double) (m - 1);
			Glyph a = source.get((int) Math.floor(pos));
			Glyph b = source.get((int) Math.ceil(pos));
			double t = pos - Math.floor(pos);
			Glyph target = letters.get(k);
			target.formats = (t < 0.5 ? a : b).formats;
			if (a.color != null && b.color != null && !a.color.isLegacy() && !b.color.isLegacy() && a != b) {
				target.color = Color.hex(ColorMath.mix(a.color.rgb, b.color.rgb, (float) t));
			} else {
				target.color = (t < 0.5 ? a : b).color;
			}
		}
		// Spaces take the formats of the letter before them, so underline and strike stay unbroken.
		Glyph previous = null;
		for (int i = from; i < to; i++) {
			Glyph glyph = this.glyphs.get(i);
			if (glyph.isSpace()) {
				glyph.formats = previous == null ? 0 : previous.formats;
				glyph.color = previous == null ? null : previous.color;
			} else {
				previous = glyph;
			}
		}
	}

	// ------------------------------------------------------------------ showing

	public Component toComponent() {
		MutableComponent result = Component.empty();
		StringBuilder run = new StringBuilder();
		Style runStyle = null;
		for (Glyph glyph : this.glyphs) {
			Style style = style(glyph.color, glyph.formats);
			if (runStyle != null && !style.equals(runStyle)) {
				result.append(Component.literal(run.toString()).setStyle(runStyle));
				run.setLength(0);
			}
			runStyle = style;
			run.append(glyph.text);
		}
		if (runStyle != null) {
			result.append(Component.literal(run.toString()).setStyle(runStyle));
		}
		return result;
	}

	public static Style style(Color color, int formats) {
		Style style = Style.EMPTY;
		if (color != null) {
			style = style.withColor(TextColor.fromRgb(color.rgb));
		}
		return style.withBold((formats & BOLD) != 0 ? Boolean.TRUE : null)
			.withItalic((formats & ITALIC) != 0 ? Boolean.TRUE : null)
			.withUnderlined((formats & UNDERLINE) != 0 ? Boolean.TRUE : null)
			.withStrikethrough((formats & STRIKE) != 0 ? Boolean.TRUE : null)
			.withObfuscated((formats & OBFUSCATED) != 0 ? Boolean.TRUE : null);
	}

	/** Codes for an existing item name, so a renamed item can be edited further. */
	public static String fromComponent(Component component, HexStyle hexStyle) {
		List<Glyph> glyphs = new ArrayList<>();
		component.visit((style, text) -> {
			Color color = null;
			if (style.getColor() != null) {
				int rgb = style.getColor().getValue() & 0xFFFFFF;
				color = Color.hex(rgb);
				for (ChatFormatting formatting : ChatFormatting.values()) {
					if (formatting.isColor() && (formatting.getColor() & 0xFFFFFF) == rgb) {
						color = Color.legacy(formatting);
						break;
					}
				}
			}
			int formats = (style.isBold() ? BOLD : 0) | (style.isItalic() ? ITALIC : 0) | (style.isUnderlined() ? UNDERLINE : 0)
				| (style.isStrikethrough() ? STRIKE : 0) | (style.isObfuscated() ? OBFUSCATED : 0);
			int i = 0;
			while (i < text.length()) {
				int len = Character.charCount(text.codePointAt(i));
				glyphs.add(new Glyph(text.substring(i, i + len), color, formats, 0));
				i += len;
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return new ColorText(glyphs, "", null, 0).serialize(hexStyle);
	}
}
