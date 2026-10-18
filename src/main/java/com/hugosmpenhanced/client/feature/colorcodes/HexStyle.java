package com.hugosmpenhanced.client.feature.colorcodes;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** How the editor writes RGB colours, depending on what the server's /rename and /sign read. */
public enum HexStyle implements Labeled {
	LEGACY_ONLY("16 Chat-Farben"),
	AMPERSAND_HASH("Hex &#RRGGBB"),
	AMPERSAND_X("Hex &x&R&R...");

	private final String label;

	HexStyle(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
