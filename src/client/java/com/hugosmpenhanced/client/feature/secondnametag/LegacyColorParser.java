package com.hugosmpenhanced.client.feature.secondnametag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Turns user-typed text with {@code &} codes into a {@link Component}: {@code &0}-{@code &f} for
 * colours, {@code &k}-{@code &o} for formatting, {@code &r} to reset and {@code &#RRGGBB} for hex
 * colours. Like vanilla's legacy {@code §} codes, a colour code also clears active formatting.
 * Anything that isn't a valid code is kept as literal text.
 */
public final class LegacyColorParser {
	private LegacyColorParser() {
	}

	public static Component parse(String input) {
		MutableComponent result = Component.empty();
		StringBuilder run = new StringBuilder();
		Style style = Style.EMPTY;
		int i = 0;

		while (i < input.length()) {
			char c = input.charAt(i);
			if (c == '&' && i + 1 < input.length()) {
				char code = input.charAt(i + 1);

				if (code == '#' && i + 8 <= input.length()) {
					TextColor hex = parseHex(input.substring(i + 2, i + 8));
					if (hex != null) {
						flush(result, run, style);
						style = Style.EMPTY.withColor(hex);
						i += 8;
						continue;
					}
				}

				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting != null) {
					flush(result, run, style);
					if (formatting == ChatFormatting.RESET) {
						style = Style.EMPTY;
					} else if (formatting.isColor()) {
						style = Style.EMPTY.withColor(formatting);
					} else {
						style = style.applyFormat(formatting);
					}
					i += 2;
					continue;
				}
			}

			run.append(c);
			i++;
		}

		flush(result, run, style);
		return result;
	}

	private static TextColor parseHex(String hex) {
		for (int k = 0; k < hex.length(); k++) {
			if (Character.digit(hex.charAt(k), 16) < 0) {
				return null;
			}
		}
		return TextColor.fromRgb(Integer.parseInt(hex, 16));
	}

	private static void flush(MutableComponent result, StringBuilder run, Style style) {
		if (!run.isEmpty()) {
			result.append(Component.literal(run.toString()).setStyle(style));
			run.setLength(0);
		}
	}
}
