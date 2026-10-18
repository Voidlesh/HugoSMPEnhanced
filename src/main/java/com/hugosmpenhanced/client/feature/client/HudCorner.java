package com.hugosmpenhanced.client.feature.client;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** Where the FPS and ping line sits on the HUD. */
public enum HudCorner implements Labeled {
	OFF("Aus"),
	TOP_LEFT("Oben links"),
	TOP_RIGHT("Oben rechts"),
	BOTTOM_LEFT("Unten links"),
	BOTTOM_RIGHT("Unten rechts");

	private final String label;

	HudCorner(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
