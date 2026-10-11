package com.hugosmpenhanced.client.feature.mainmenu;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum SplashColor implements NameableEnum {
	YELLOW("Gelb", 0xFFFFFF00),
	WHITE("Weiß", 0xFFFFFFFF),
	GOLD("Gold", 0xFFFFAA00),
	RED("Rot", 0xFFFF5555),
	PURPLE("Lila", 0xFFC77DFF),
	AQUA("Türkis", 0xFF55FFFF);

	private final String label;
	private final int argb;

	SplashColor(String label, int argb) {
		this.label = label;
		this.argb = argb;
	}

	public int argb() {
		return this.argb;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
