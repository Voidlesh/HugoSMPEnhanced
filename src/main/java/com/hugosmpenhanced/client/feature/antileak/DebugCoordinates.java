package com.hugosmpenhanced.client.feature.antileak;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

import com.hugosmpenhanced.client.feature.antileak.FakeCoordinates.Kind;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * Draws the F3 lines with X and Z (and Y, unless it is wanted) replaced. Masked numbers always
 * take the same width, so not even the number of digits gives a hint, and the real digits never
 * reach the screen: the blur is a smear of fixed placeholder digits.
 */
public final class DebugCoordinates {
	private static final Pattern INT = Pattern.compile("-?\\d+");
	private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");
	private static final int LINE_HEIGHT = 9;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int BACKGROUND = 0x90505050;
	/** Placeholders for the blur, picked by position in the line so neighbours differ. */
	private static final String[] FAKE = {"4817", "2093", "7360", "5129", "8642", "3075", "6904"};
	/** Blur kernel (x, y, alpha): a gaussian wide enough that no digit can be made out. */
	private static final int[][] KERNEL = kernel(5, 2, 2.6, 1.3, 0x34);

	private DebugCoordinates() {
	}

	private static int[][] kernel(int rx, int ry, double sx, double sy, int peak) {
		List<int[]> taps = new ArrayList<>();
		for (int dy = -ry; dy <= ry; dy++) {
			for (int dx = -rx; dx <= rx; dx++) {
				double weight = Math.exp(-(dx * dx / (2 * sx * sx) + dy * dy / (2 * sy * sy)));
				int alpha = (int) Math.round(peak * weight);
				if (alpha >= 4) {
					taps.add(new int[] {dx, dy, alpha});
				}
			}
		}
		return taps.toArray(new int[0][]);
	}

	/** A run of text, or a hidden number of fixed width; {@code kind} says what a hidden number stood for. */
	public record Part(String text, boolean masked, FakeCoordinates.Kind kind, String token) {
		Part(String text) {
			this(text, false, null, null);
		}
	}

	private static final Kind[] XYZ = {Kind.POS_X, Kind.POS_Y, Kind.POS_Z};
	private static final Kind[] BLOCK = {Kind.BLOCK_X, Kind.BLOCK_Y, Kind.BLOCK_Z};
	private static final Kind[] RELATIVE = {Kind.RELATIVE_X, Kind.RELATIVE_Y, Kind.RELATIVE_Z};
	private static final Kind[] TARGET = {Kind.TARGET_X, Kind.TARGET_Y, Kind.TARGET_Z};
	/** Chunk x, section y, chunk z, x and z inside the region, region file x and z. */
	private static final Kind[] CHUNK = {Kind.CHUNK_X, Kind.SECTION_Y, Kind.CHUNK_Z,
		Kind.REGION_LOCAL_X, Kind.REGION_LOCAL_Z, Kind.REGION_X, Kind.REGION_Z};

	/** Same as the vanilla debug overlay's renderLines, with coordinates masked. */
	public static void renderLines(GuiGraphicsExtractor graphics, Font font, List<String> lines, boolean left) {
		CoordinateMask style = AntiLeak.maskStyle();
		boolean showY = AntiLeak.showY();
		List<List<Part>> parsed = new ArrayList<>(lines.size());
		for (String line : lines) {
			List<Part> parts = Strings.isNullOrEmpty(line) ? List.of() : split(line, showY);
			parsed.add(style == CoordinateMask.RANDOM ? randomize(parts) : parts);
		}

		for (int i = 0; i < parsed.size(); i++) {
			List<Part> parts = parsed.get(i);
			if (!parts.isEmpty()) {
				int width = width(font, parts);
				int x = left ? 2 : graphics.guiWidth() - 2 - width;
				int y = 2 + LINE_HEIGHT * i;
				graphics.fill(x - 1, y - 1, x + width + 1, y + LINE_HEIGHT - 1, BACKGROUND);
			}
		}

		for (int i = 0; i < parsed.size(); i++) {
			List<Part> parts = parsed.get(i);
			if (parts.isEmpty()) {
				continue;
			}
			int x = left ? 2 : graphics.guiWidth() - 2 - width(font, parts);
			int y = 2 + LINE_HEIGHT * i;
			int masked = 0;
			// Formatting at the start of the line (the underline of "Targeted Block") goes on every run
			String carry = "";
			for (Part part : parts) {
				int w = partWidth(font, part);
				if (part.masked()) {
					drawMasked(graphics, font, style, x, y, w, FAKE[(i + masked++) % FAKE.length]);
				} else {
					//? if modern
					graphics.text(font, carry + part.text(), x, y, TEXT, false);
					//? else
					graphics.drawString(font, carry + part.text(), x, y, TEXT, false);
					//? end
					if (carry.isEmpty()) {
						//? if modern
						carry = leadingCodes(part.text());
						//? else
						carry = leadingCodes(part.text());
						//? end
					}
				}
				x += w;
			}
		}
	}

	/** Hidden numbers become made-up ones, drawn like normal text. */
	private static List<Part> randomize(List<Part> parts) {
		List<Part> out = new ArrayList<>(parts.size());
		for (Part part : parts) {
			out.add(part.masked() ? new Part(FakeCoordinates.value(part.kind(), part.token())) : part);
		}
		return out;
	}

	/** Splits a line into text and masked numbers; lines without coordinates come back whole. */
	public static List<Part> split(String line, boolean showY) {
		String plain = ChatFormatting.stripFormatting(line);
		if (plain == null) {
			return List.of(new Part(line));
		}
		int keep = showY ? 1 : -1;
		if (plain.startsWith("XYZ: ")) {
			return mask(line, DECIMAL, keep, XYZ);
		}
		if (plain.startsWith("Block: ")) {
			return mask(line, INT, keep, BLOCK);
		}
		if (plain.startsWith("Section-relative: ")) {
			return mask(line, INT, keep, RELATIVE);
		}
		if (plain.startsWith("Targeted Block: ") || plain.startsWith("Targeted Fluid: ")) {
			return mask(line, INT, keep, TARGET);
		}
		if (plain.startsWith("Chunk: ")) {
			return mask(line, INT, keep, CHUNK);
		}
		return List.of(new Part(line));
	}

	/** Masks every number of the line except the one at {@code keep}. */
	private static List<Part> mask(String line, Pattern number, int keep, Kind[] kinds) {
		List<Part> parts = new ArrayList<>();
		Matcher matcher = number.matcher(line);
		int start = 0;
		int index = 0;
		while (matcher.find()) {
			// Skip digits that belong to a formatting code like "§n"
			if (matcher.start() > 0 && line.charAt(matcher.start() - 1) == ChatFormatting.PREFIX_CODE) {
				continue;
			}
			if (index != keep) {
				if (matcher.start() > start) {
					parts.add(new Part(line.substring(start, matcher.start())));
				}
				parts.add(new Part(matcher.group().contains(".") ? "0000.000" : "0000", true,
					kinds[Math.min(index, kinds.length - 1)], matcher.group()));
				start = matcher.end();
			}
			index++;
		}
		if (start < line.length()) {
			parts.add(new Part(line.substring(start)));
		}
		return parts;
	}

	private static String leadingCodes(String text) {
		int end = 0;
		while (end + 1 < text.length() && text.charAt(end) == ChatFormatting.PREFIX_CODE) {
			end += 2;
		}
		return text.substring(0, end);
	}

	private static int width(Font font, List<Part> parts) {
		int width = 0;
		for (Part part : parts) {
			width += partWidth(font, part);
		}
		return width;
	}

	private static int partWidth(Font font, Part part) {
		//? if modern
		return font.width(part.text());
		//? else
		return font.width(part.text());
		//? end
	}

	private static void drawMasked(GuiGraphicsExtractor graphics, Font font, CoordinateMask style, int x, int y, int width, String fake) {
		if (style == CoordinateMask.BAR) {
			graphics.fill(x, y - 1, x + width - 1, y + LINE_HEIGHT - 2, 0xFF000000);
			return;
		}
		// Soft glow behind the smear, then the placeholder digits drawn many times slightly apart
		graphics.fill(x, y, x + width - 1, y + LINE_HEIGHT - 3, 0x18FFFFFF);
		String text = fake.length() >= 4 && width > font.width("00000") ? fake + "." + fake.substring(1) : fake;
		int textX = x + (width - font.width(text)) / 2;
		for (int[] tap : KERNEL) {
			graphics.text(font, text, textX + tap[0], y + tap[1], ARGB.color(tap[2], TEXT), false);
		}
	}
}
