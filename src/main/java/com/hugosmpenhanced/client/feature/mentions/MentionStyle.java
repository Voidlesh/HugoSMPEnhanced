package com.hugosmpenhanced.client.feature.mentions;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** How a chat message that mentions you is marked. */
public enum MentionStyle implements Labeled {
	LINE("Hinweiszeile"),
	COLOR("Nachricht einfärben"),
	BOTH("Beides");

	private final String label;

	MentionStyle(String label) {
		this.label = label;
	}

	public boolean line() {
		return this != COLOR;
	}

	public boolean color() {
		return this != LINE;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
