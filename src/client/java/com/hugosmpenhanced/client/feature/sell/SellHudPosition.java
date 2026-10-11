package com.hugosmpenhanced.client.feature.sell;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** Where the AFK farm's status pill sits on the HUD. */
public enum SellHudPosition implements Labeled {
	TOP_CENTER("Oben Mitte"),
	TOP_LEFT("Oben links"),
	TOP_RIGHT("Oben rechts"),
	OFF("Aus");

	private final String label;

	SellHudPosition(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
