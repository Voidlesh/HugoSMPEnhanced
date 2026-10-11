package com.hugosmpenhanced.client.feature.auction;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

/** Which item name the auction search sends when a suggestion is picked. */
public enum AuctionSearchLanguage implements NameableEnum {
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
