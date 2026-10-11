package com.hugosmpenhanced.client.feature.mainmenu;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum SplashAnimation implements NameableEnum {
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
