package com.hugosmpenhanced.client.feature.secondnametag;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.network.chat.Component;

/**
 * A second, user-defined line of text shown above or below the local player's own nametag. It is
 * purely client-side, so only the user sees it, in third-person view.
 */
public final class SecondNametag {
	private static String cachedSource;
	private static Component cachedComponent;

	private SecondNametag() {
	}

	public static boolean isActive() {
		ModConfig config = ModConfigManager.CONFIG;
		return config.secondNametagEnabled && config.secondNametagText != null && !config.secondNametagText.isBlank();
	}

	/** The configured text, parsed once per change rather than every frame. */
	public static Component text() {
		String source = ModConfigManager.CONFIG.secondNametagText;
		if (!source.equals(cachedSource)) {
			cachedSource = source;
			cachedComponent = LegacyColorParser.parse(source);
		}
		return cachedComponent;
	}
}
