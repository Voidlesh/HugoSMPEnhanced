package com.hugosmpenhanced.client.feature.antileak;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** How hidden coordinates look on the F3 screen; the real digits are never drawn. */
public enum CoordinateMask implements Labeled {
	BLUR("Verschwommen"),
	BAR("Schwarzer Balken"),
	RANDOM("Zufällige Zahlen");

	private final String label;

	CoordinateMask(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
