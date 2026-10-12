package com.hugosmpenhanced.client.feature.colorcodes;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** How the editor writes RGB colours, depending on what the server's /rename and /sign read. */
public enum HexStyle implements Labeled {
	AMPERSAND_HASH("&#RRGGBB"),
	AMPERSAND_X("&x&R&R&G&G&B&B"),
	LEGACY_ONLY("Nur 16 Farben");

	private final String label;

	HexStyle(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
