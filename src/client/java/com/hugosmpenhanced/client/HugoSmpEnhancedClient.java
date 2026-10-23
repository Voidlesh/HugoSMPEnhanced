package com.hugosmpenhanced.client;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.hugosmpenhanced.client.feature.auction.AuctionSearch;
import com.hugosmpenhanced.client.feature.auction.LimitedOffers;
import com.hugosmpenhanced.client.feature.auction.PricePerItemFeature;
import com.hugosmpenhanced.client.feature.client.ClientTweaks;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodes;
import com.hugosmpenhanced.client.feature.debug.DebugDump;
import com.hugosmpenhanced.client.feature.discord.DiscordPresence;
import com.hugosmpenhanced.client.feature.entityheatmap.EntityHeatmap;
import com.hugosmpenhanced.client.feature.farm.FarmFeature;
import com.hugosmpenhanced.client.feature.gallery.GalleryFeature;
import com.hugosmpenhanced.client.feature.hugoguard.HgCommand;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuardGlowLayer;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonsFeature;
import com.hugosmpenhanced.client.feature.keyboard.KeyboardFeature;
import com.hugosmpenhanced.client.feature.keywords.KeywordAlarm;
import com.hugosmpenhanced.client.feature.mentions.MentionHighlightFeature;
import com.hugosmpenhanced.client.feature.messages.MessageFeature;
import com.hugosmpenhanced.client.feature.money.MoneyFeature;
import com.hugosmpenhanced.client.feature.notes.NotesFeature;
import com.hugosmpenhanced.client.feature.orders.OrdersHelper;
import com.hugosmpenhanced.client.feature.pay.PayFeature;
import com.hugosmpenhanced.client.feature.sell.SellFeature;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPricesFeature;
import com.hugosmpenhanced.client.feature.spawnerstorage.SpawnerStorageFeature;
import com.hugosmpenhanced.client.feature.streamer.StreamerFeature;
import com.hugosmpenhanced.client.feature.streamermode.StreamerMode;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.feature.update.ModUpdates;
import com.hugosmpenhanced.client.feature.update.UpdateBadge;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.hud.HudKeys;
import com.hugosmpenhanced.client.settings.SettingsFeature;
import com.hugosmpenhanced.client.util.MenuContext;
import com.hugosmpenhanced.client.util.ServerCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

public class HugoSmpEnhancedClient implements ClientModInitializer {
	public static final String MOD_ID = "hugosmpenhanced";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ServerCheck.register();
		HugoFormats.register();
		HudKeys.register();
		DebugDump.register();
		MenuContext.register();
		PayFeature.register();
		MoneyFeature.register();
		OrdersHelper.register();
		SpawnerStorageFeature.register();
		KeywordAlarm.register();
		FarmFeature.register();
		MessageFeature.register();
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
		StreamerMode.register();
		StreamerFeature.register();
		ModUpdates.register();
		UpdateBadge.register();
		ClientTweaks.register();
		LOGGER.info("HugoSMP Enhanced loaded");
	}
}
