package com.hugosmpenhanced.client.feature.mainmenu;

import com.hugosmpenhanced.client.settings.Labeled;
import net.minecraft.network.chat.Component;

public enum SplashAnimation implements Labeled {
	PULSE("Pulsieren"),
	WOBBLE("Wackeln"),
	RAINBOW("Regenbogen"),
	NONE("Aus");

	private final String label;

	SplashAnimation(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
