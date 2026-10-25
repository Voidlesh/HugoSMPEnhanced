package com.hugosmpenhanced.client.feature.streamermode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

/**
 * Swaps player names for their stand-ins in any text about to be measured or drawn.
 *
 * <p>A name only counts as a whole word: a run of the characters Minecraft allows in names
 * ({@code A-Z a-z 0-9 _}), compared ignoring case. So "Steve" is found in "&lt;Steve&gt; hi",
 * "Steve's house" and "§aSteve", but not in "Steve123". Each stand-in takes the style of the
 * name's first letter, so a rank colour or a click event on the name carries over.
 *
 * <p>The table is swapped as a whole by {@link StreamerMode}; when it is empty every method
 * returns its argument unchanged without looking at it.
 */
public final class NameMask {
	private static volatile Table table = Table.EMPTY;
	private static final ThreadLocal<Buffer> BUFFER = ThreadLocal.withInitial(Buffer::new);

	private NameMask() {
	}

	/** Lower-case name to stand-in; lengths bound the tokens worth looking up. */
	record Table(Map<String, String> names, int minLength, int maxLength) {
		static final Table EMPTY = new Table(Map.of(), Integer.MAX_VALUE, 0);

		static Table of(Map<String, String> names) {
			int min = Integer.MAX_VALUE;
			int max = 0;
			for (String name : names.keySet()) {
				min = Math.min(min, name.length());
				max = Math.max(max, name.length());
			}
			return names.isEmpty() ? EMPTY : new Table(Map.copyOf(names), min, max);
		}

		String lookup(CharSequence text, int start, int end) {
			int length = end - start;
			if (length < this.minLength || length > this.maxLength) {
				return null;
			}
			return this.names.get(text.subSequence(start, end).toString().toLowerCase(Locale.ROOT));
		}
	}

	static void set(Map<String, String> names) {
		table = Table.of(names);
	}

	public static boolean active() {
		return table != Table.EMPTY;
	}

	/** The stand-in for one name, or the name itself when it isn't hidden. */
	public static String nameFor(String name) {
		String replacement = table.names().get(name.toLowerCase(Locale.ROOT));
		return replacement != null ? replacement : name;
	}

	public static boolean isNameChar(int c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
	}

	/** Raw string with legacy § codes: the code and its letter separate words, as they do on screen. */
	public static String mask(String text) {
		Table t = table;
		if (t == Table.EMPTY || text == null || text.length() < t.minLength()) {
			return text;
		}
		StringBuilder out = null;
		int copied = 0;
		int i = 0;
		int n = text.length();
		while (i < n) {
			char c = text.charAt(i);
			if (c == '§') {
				i += 2;
				continue;
			}
			if (!isNameChar(c)) {
				i++;
				continue;
			}
			int start = i;
			while (i < n && isNameChar(text.charAt(i))) {
				i++;
			}
			String replacement = t.lookup(text, start, i);
			if (replacement != null) {
				if (out == null) {
					out = new StringBuilder(n + 16);
				}
				out.append(text, copied, start).append(replacement);
				copied = i;
			}
		}
		if (out == null) {
			return text;
		}
		return out.append(text, copied, n).toString();
	}

	public static FormattedCharSequence mask(FormattedCharSequence sequence) {
		Table t = table;
		if (t == Table.EMPTY || sequence == null || sequence instanceof Masked) {
			return sequence;
		}
		Buffer buffer = BUFFER.get().reset();
		sequence.accept(buffer);
		Buffer masked = buffer.mask(t);
		return masked == null ? sequence : new Masked(masked.codePoints(), masked.styles());
	}

	public static FormattedText mask(FormattedText text) {
		Table t = table;
		if (t == Table.EMPTY || text == null) {
			return text;
		}
		Buffer buffer = BUFFER.get().reset();
		StringDecomposer.iterateFormatted(text, Style.EMPTY, buffer);
		Buffer masked = buffer.mask(t);
		if (masked == null) {
			return text;
		}
		List<FormattedText> parts = new ArrayList<>();
		masked.forEachRun((run, style) -> parts.add(FormattedText.of(run, style)));
		return FormattedText.composite(parts);
	}

	public static Component mask(Component component) {
		Table t = table;
		if (t == Table.EMPTY || component == null) {
			return component;
		}
		Buffer buffer = BUFFER.get().reset();
		StringDecomposer.iterateFormatted(component, Style.EMPTY, buffer);
		Buffer masked = buffer.mask(t);
		if (masked == null) {
			return component;
		}
		MutableComponent out = Component.empty();
		masked.forEachRun((run, style) -> out.append(Component.literal(run).setStyle(style)));
		return out;
	}

	/** An already masked sequence; drawing it again doesn't scan it again. */
	private record Masked(int[] codePoints, Style[] styles) implements FormattedCharSequence {
		@Override
		public boolean accept(FormattedCharSink sink) {
			for (int i = 0; i < this.codePoints.length; i++) {
				if (!sink.accept(i, this.styles[i], this.codePoints[i])) {
					return false;
				}
			}
			return true;
		}
	}

	/** Code points with their styles, reused per thread so text without names costs no garbage. */
	private static final class Buffer implements FormattedCharSink {
		private int[] codePoints = new int[64];
		private Style[] styles = new Style[64];
		private int size;
		private final StringBuilder token = new StringBuilder(16);

		Buffer reset() {
			Arrays.fill(this.styles, 0, this.size, null);
			this.size = 0;
			return this;
		}

		@Override
		public boolean accept(int index, Style style, int codePoint) {
			this.add(codePoint, style);
			return true;
		}

		void add(int codePoint, Style style) {
			if (this.size == this.codePoints.length) {
				this.codePoints = Arrays.copyOf(this.codePoints, this.size * 2);
				this.styles = Arrays.copyOf(this.styles, this.size * 2);
			}
			this.codePoints[this.size] = codePoint;
			this.styles[this.size] = style;
			this.size++;
		}

		int[] codePoints() {
			return Arrays.copyOf(this.codePoints, this.size);
		}

		Style[] styles() {
			return Arrays.copyOf(this.styles, this.size);
		}

		/** A new buffer with every name replaced, or null when there was none. */
		Buffer mask(Table t) {
			if (this.size < t.minLength()) {
				return null;
			}
			Buffer out = null;
			int copied = 0;
			int i = 0;
			while (i < this.size) {
				if (!isNameChar(this.codePoints[i])) {
					i++;
					continue;
				}
				int start = i;
				this.token.setLength(0);
				while (i < this.size && isNameChar(this.codePoints[i])) {
					this.token.append((char) this.codePoints[i]);
					i++;
				}
				String replacement = t.lookup(this.token, 0, this.token.length());
				if (replacement == null) {
					continue;
				}
				if (out == null) {
					out = new Buffer();
				}
				for (int k = copied; k < start; k++) {
					out.add(this.codePoints[k], this.styles[k]);
				}
				Style style = this.styles[start];
				for (int k = 0; k < replacement.length(); ) {
					int cp = replacement.codePointAt(k);
					out.add(cp, style);
					k += Character.charCount(cp);
				}
				copied = i;
			}
			if (out == null) {
				return null;
			}
			for (int k = copied; k < this.size; k++) {
				out.add(this.codePoints[k], this.styles[k]);
			}
			return out;
		}

		void forEachRun(RunConsumer consumer) {
			int start = 0;
			StringBuilder run = new StringBuilder();
			for (int i = 0; i <= this.size; i++) {
				if (i == this.size || i > start && !this.styles[i].equals(this.styles[start])) {
					if (i > start) {
						consumer.accept(run.toString(), this.styles[start]);
					}
					run.setLength(0);
					start = i;
				}
				if (i < this.size) {
					run.appendCodePoint(this.codePoints[i]);
				}
			}
		}
	}

	@FunctionalInterface
	private interface RunConsumer {
		void accept(String run, Style style);
	}

	/** For tests: the visible text of a sequence. */
	public static String plain(FormattedCharSequence sequence) {
		StringBuilder out = new StringBuilder();
		sequence.accept((index, style, codePoint) -> {
			out.appendCodePoint(codePoint);
			return true;
		});
		return out.toString();
	}
}
