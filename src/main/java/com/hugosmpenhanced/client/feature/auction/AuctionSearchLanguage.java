package com.hugosmpenhanced.client.feature.auction;

import com.hugosmpenhanced.client.settings.Labeled;
import net.minecraft.network.chat.Component;

/** Which item name the auction search sends when a suggestion is picked. */
public enum AuctionSearchLanguage implements Labeled {
	GAME("Spielsprache"),
	ENGLISH("Englisch");

	private final String label;

	AuctionSearchLanguage(String label) {
		this.label = label;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
