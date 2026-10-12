package com.hugosmpenhanced.client;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.hugosmpenhanced.client.feature.auction.AuctionSearch;
import com.hugosmpenhanced.client.feature.auction.LimitedOffers;
import com.hugosmpenhanced.client.feature.auction.PricePerItemFeature;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodes;
import com.hugosmpenhanced.client.feature.discord.DiscordPresence;
import com.hugosmpenhanced.client.feature.entityheatmap.EntityHeatmap;
import com.hugosmpenhanced.client.feature.gallery.GalleryFeature;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuardGlowLayer;
import com.hugosmpenhanced.client.feature.hugoguard.HgCommand;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonsFeature;
import com.hugosmpenhanced.client.feature.keyboard.KeyboardFeature;
import com.hugosmpenhanced.client.feature.mentions.MentionHighlightFeature;
import com.hugosmpenhanced.client.feature.notes.NotesFeature;
import com.hugosmpenhanced.client.feature.sell.SellFeature;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPricesFeature;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.settings.SettingsFeature;
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
		AuctionSearch.register();
		LimitedOffers.register();
		MentionHighlightFeature.register();
		TpaKeybinds.register();
		ConfigCommand.register();
		SettingsFeature.register();
		InventoryButtonsFeature.register();
		KeyboardFeature.register();
		GalleryFeature.register();
		NotesFeature.register();
		HugoGuard.register();
		HugoGuardGlowLayer.register();
		HgCommand.register();
		EntityHeatmap.register();
		SellFeature.register();
		SpawnerPricesFeature.register();
		DiscordPresence.register();
		ColorCodes.register();
		AntiLeak.register();
		LOGGER.info("HugoSMP Enhanced loaded");
	}
}
