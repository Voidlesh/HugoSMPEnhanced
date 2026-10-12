package com.hugosmpenhanced.client.feature.discord;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** What Discord shows while not on HugoSMP (menu, singleplayer, other servers). */
public enum DiscordOutside implements Labeled {
	SHOW("Minecraft anzeigen"),
	HIDE("Nichts anzeigen");

	private final String label;

	DiscordOutside(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
