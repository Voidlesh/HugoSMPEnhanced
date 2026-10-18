package com.hugosmpenhanced.client.feature.discord;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** From when Discord counts the elapsed time. */
public enum DiscordTimer implements Labeled {
	JOIN("Seit dem Beitreten"),
	LOCATION("Seit dem Ortswechsel"),
	OFF("Aus");

	private final String label;

	DiscordTimer(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
