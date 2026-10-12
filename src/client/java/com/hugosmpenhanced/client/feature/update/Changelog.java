package com.hugosmpenhanced.client.feature.update;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Release notes as GitHub Markdown, laid out for the game font: headings, bullet lists, bold,
 * inline code and links (shown as their text). Anything else stays plain text.
 */
final class Changelog {
	private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|!?\\[([^\\]]*)]\\(([^)]*)\\)|<[^>]+>");
	private static final int CODE = 0xFFFFDC8C;

	/** One wrapped line; {@code indent} in pixels, {@code gap} extra space above it. */
	record Line(FormattedCharSequence text, int indent, int gap, String bullet) {
	}

	private Changelog() {
	}

	static List<Line> layout(String markdown, Font font, int width) {
		List<Line> lines = new ArrayList<>();
		boolean blank = false;
		boolean code = false;
		for (String raw : markdown.replace("\r", "").split("\n")) {
			String line = raw.stripTrailing();
			if (line.strip().startsWith("```")) {
				code = !code;
				continue;
			}
			if (line.isBlank()) {
				blank = !lines.isEmpty();
				continue;
			}
			int gap = blank ? 5 : 0;
			blank = false;
			if (code) {
				add(lines, font, Component.literal(line.replace("\t", "  ")).withColor(CODE), width, 6, gap, null);
				continue;
			}
			String trimmed = line.strip();
			if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
				continue;
			}
			Matcher heading = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(trimmed);
			if (heading.matches()) {
				MutableComponent text = inline(heading.group(2).replaceAll("\\s#+$", ""), Style.EMPTY.withColor(Theme.WHITE & 0xFFFFFF).withBold(true));
				add(lines, font, text, width, 0, lines.isEmpty() ? 0 : 7, null);
				continue;
			}
			int depth = (line.length() - line.stripLeading().length()) / 2;
			Matcher bullet = Pattern.compile("^([-*+]|\\d+[.)])\\s+(.*)$").matcher(trimmed);
			if (bullet.matches()) {
				String marker = bullet.group(1).matches("\\d+[.)]") ? bullet.group(1) : "•";
				int indent = 4 + Math.min(depth, 3) * 8;
				add(lines, font, inline(bullet.group(2), Style.EMPTY.withColor(Theme.TEXT_DIM & 0xFFFFFF)), width,
					indent + font.width(marker) + 4, gap, marker);
				continue;
			}
			if (trimmed.startsWith(">")) {
				add(lines, font, inline(trimmed.substring(1).strip(), Style.EMPTY.withColor(Theme.MUTED & 0xFFFFFF).withItalic(true)),
					width, 6, gap, null);
				continue;
			}
			add(lines, font, inline(trimmed, Style.EMPTY.withColor(Theme.TEXT_DIM & 0xFFFFFF)), width, 0, gap, null);
		}
		return lines;
	}

	private static void add(List<Line> lines, Font font, Component text, int width, int indent, int gap, String bullet) {
		List<FormattedCharSequence> wrapped = font.split(text, Math.max(40, width - indent));
		for (int i = 0; i < wrapped.size(); i++) {
			lines.add(new Line(wrapped.get(i), indent, i == 0 ? gap : 0, i == 0 ? bullet : null));
		}
	}

	private static MutableComponent inline(String text, Style base) {
		MutableComponent out = Component.empty().withStyle(base);
		Matcher matcher = INLINE.matcher(text);
		int last = 0;
		while (matcher.find()) {
			if (matcher.start() > last) {
				out.append(Component.literal(text.substring(last, matcher.start())));
			}
			if (matcher.group(1) != null || matcher.group(2) != null) {
				String bold = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
				out.append(Component.literal(bold).withStyle(Style.EMPTY.withBold(true).withColor(Theme.TEXT & 0xFFFFFF)));
			} else if (matcher.group(3) != null) {
				out.append(Component.literal(matcher.group(3)).withColor(CODE));
			} else if (matcher.group(4) != null) {
				out.append(Component.literal(matcher.group(4)).withStyle(Style.EMPTY.withColor(Theme.GOLD & 0xFFFFFF)));
			}
			last = matcher.end();
		}
		if (last < text.length()) {
			out.append(Component.literal(text.substring(last)));
		}
		return out;
	}
}
