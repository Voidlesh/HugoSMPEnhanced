package com.hugosmpenhanced.client.feature.sell;

import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** Whether the AFK farm starts by itself after joining HugoSMP. */
public enum SellAutostart implements Labeled {
	OFF("Aus"),
	IF_WAS_ON("Wenn vorher an"),
	ALWAYS("Bei jedem Join");

	private final String label;

	SellAutostart(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
