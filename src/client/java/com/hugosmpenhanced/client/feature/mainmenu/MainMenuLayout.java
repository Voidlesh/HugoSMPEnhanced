package com.hugosmpenhanced.client.feature.mainmenu;

import com.hugosmpenhanced.client.settings.Labeled;
import net.minecraft.network.chat.Component;

public enum MainMenuLayout implements Labeled {
	CLASSIC("Klassisch"),
	SIDEBAR("Seitenleiste");

	private final String label;

	MainMenuLayout(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

	public MainMenuLayout next() {
		return this == CLASSIC ? SIDEBAR : CLASSIC;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
