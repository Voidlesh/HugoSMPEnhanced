package com.hugosmpenhanced.client;

import com.hugosmpenhanced.client.feature.auction.PricePerItemFeature;
import com.hugosmpenhanced.client.feature.mentions.MentionHighlightFeature;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HugoSmpEnhancedClient implements ClientModInitializer {
	public static final String MOD_ID = "hugosmpenhanced";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ServerCheck.register();
		PricePerItemFeature.register();
		MentionHighlightFeature.register();
		TpaKeybinds.register();
		ConfigCommand.register();
		LOGGER.info("HugoSMP Enhanced loaded");
	}
}
