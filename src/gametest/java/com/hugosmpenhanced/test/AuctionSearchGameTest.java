package com.hugosmpenhanced.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.auction.AuctionLore;
import com.hugosmpenhanced.client.feature.auction.AuctionMarket;
import com.hugosmpenhanced.client.feature.auction.AuctionPriceMemory;
import com.hugosmpenhanced.client.feature.auction.AuctionSearch;
import com.hugosmpenhanced.client.feature.auction.AuctionSearchScreen;
import com.hugosmpenhanced.client.feature.notes.Note;
import com.hugosmpenhanced.client.feature.notes.NotesHud;
import com.hugosmpenhanced.client.hud.HudEditorScreen;
import com.hugosmpenhanced.client.hud.HudManager;
import com.hugosmpenhanced.client.hud.HudPos;


import com.hugosmpenhanced.client.settings.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The server opens an auction search sign like HugoSMP does; the search screen must open instead,
 * show suggestions with remembered prices, and Enter must write the picked name onto the sign.
 */
@SuppressWarnings("UnstableApiUsage")
public class AuctionSearchGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		AuctionSearch.forceActive = true;
		// The menu blur makes every loading-screen frame slow under software rendering.
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		// German like on HugoSMP, so the suggestions show both names.
		setLanguage(context, "de_de");
		ModConfigManager.CONFIG.auctionSearchEnabled = true;
		ModConfigManager.CONFIG.auctionSearchHistory.clear();
		ModConfigManager.CONFIG.auctionSearchHistory.add("Elytra");
		ModConfigManager.CONFIG.auctionSearchHistory.add("Netheritbarren");
		ModConfigManager.CONFIG.auctionSearchHistory.add("shulker rot");
		ModConfigManager.CONFIG.auctionSearchFavorites.clear();
		ModConfigManager.CONFIG.auctionSearchFavorites.add("Diamant");

		// Prices as HugoSMP writes them: dots group thousands.
		checkPrice("Preis: $7.000", 7000);
		checkPrice("Preis: $7,000", 7000);
		checkPrice("Preis: $1.234.567", 1234567);
		checkPrice("Preis: $12,50", 12.5);
		checkPrice("Preis: $7.000,50", 7000.5);
		checkPrice("Preis: $150", 150);
		checkPrice("Preis pro Stück: $2.500", 2500);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			// A flat world loads fast enough on slow machines; the sign is all this test needs.
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> {
				AuctionPriceMemory.record(new ItemStack(Items.DIAMOND), 152.5, AuctionMarket.AUCTION);
				AuctionPriceMemory.record(new ItemStack(Items.DIAMOND_SWORD), 900, AuctionMarket.AUCTION);
				AuctionPriceMemory.record(new ItemStack(Items.ELYTRA), 25_000, AuctionMarket.AUCTION);
				AuctionPriceMemory.record(new ItemStack(Items.NETHERITE_INGOT), 4_200, AuctionMarket.AUCTION);
				Holder<Enchantment> mending = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.MENDING);
				ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
				ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
				stored.set(mending, 1);
				book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
				AuctionPriceMemory.record(book, 3_000, AuctionMarket.AUCTION);
				AuctionPriceMemory.record(new ItemStack(Items.DIAMOND), 120, AuctionMarket.ORDERS);
				AuctionPriceMemory.record(new ItemStack(Items.DIAMOND), 140, AuctionMarket.ORDERS);
			});

			BlockPos pos = context.computeOnClient(client -> client.player.blockPosition().offset(0, 0, 2));
			singleplayer.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
				+ " oak_sign{front_text:{messages:[\"\",\"^^^^^^^^^^^^^^^\",\"Suchbegriff\",\"(min. 3 Zeichen)\"]}}");
			context.waitTicks(5);
			openSign(singleplayer, pos);
			context.waitTicks(10);
			boolean replaced = context.computeOnClient(client -> client.gui.screen() instanceof AuctionSearchScreen);
			System.out.println("AUCTION screen replaced sign: " + replaced);
			if (!replaced) {
				throw new AssertionError("Search sign was not replaced");
			}
			context.takeScreenshot("auction-search-empty");

			context.getInput().typeChars("dia");
			context.waitTicks(3);
			context.takeScreenshot("auction-search-dia");

			// Too short: Enter must not send anything.
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			context.takeScreenshot("auction-search-too-short");
			if (!context.computeOnClient(client -> client.gui.screen() instanceof AuctionSearchScreen)) {
				throw new AssertionError("Too short search was sent");
			}
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);

			context.getInput().typeChars("repa");
			context.waitTicks(3);
			context.takeScreenshot("auction-search-enchantment");
			clear(context);

			context.getInput().typeChars("schwert");
			context.waitTicks(3);
			context.takeScreenshot("auction-search-schwert");
			clear(context);

			// Pick the first suggestion for "diamantschw" with the arrow key and send it.
			context.getInput().typeChars("diamantschw");
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_DOWN);
			context.waitTicks(2);
			context.takeScreenshot("auction-search-selected");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(10);
			String line = singleplayer.getServer().computeOnServer(server ->
				((SignBlockEntity) server.overworld().getBlockEntity(pos)).getText(SignTextSlot.FRONT).getMessages(false).get(0).getString());
			String expected = context.computeOnClient(client -> new ItemStack(Items.DIAMOND_SWORD).getHoverName().getString());
			System.out.println("AUCTION sign line 1 after enter: '" + line + "' expected '" + expected + "' history="
				+ ModConfigManager.CONFIG.auctionSearchHistory);
			if (!expected.equals(line)) {
				throw new AssertionError("Sign got '" + line + "' instead of '" + expected + "'");
			}

			// "Schild benutzen" opens the vanilla editor.
			openSign(singleplayer, pos);
			context.waitTicks(10);
			context.runOnClient(client -> {
				var screen = client.gui.screen();
				for (var child : screen.children()) {
					if (child instanceof net.minecraft.client.gui.components.Button button
						&& button.getMessage().getString().equals("Schild benutzen")) {
						button.onPress(null);
					}
				}
			});
			context.waitTicks(5);
			String fallback = context.computeOnClient(client -> client.gui.screen() == null ? "null" : client.gui.screen().getClass().getSimpleName());
			System.out.println("AUCTION fallback screen: " + fallback);
			context.takeScreenshot("auction-search-fallback");
			context.setScreen(() -> null);

			// The same sign opened from the orders menu becomes the order search.
			context.runOnClient(client -> {
				var inventory = client.player.getInventory();
				client.setScreenAndShow(new ContainerScreen(ChestMenu.sixRows(0, inventory), inventory, Component.literal("Orders")));
			});
			context.waitTicks(3);
			openSign(singleplayer, pos);
			context.waitTicks(10);
			context.getInput().typeChars("diaman");
			context.waitTicks(3);
			context.takeScreenshot("auction-search-orders");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Pinned notes moved to the right side.
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.notes.clear();
				Note note = new Note();
				note.title = "Einkauf";
				note.text = "- 3 Stacks Eisen\n- Elytra reparieren";
				note.pinned = true;
				ModConfigManager.CONFIG.notes.add(note);
				HudManager.setPos(NotesHud.INSTANCE, new HudPos(1, 0.2));
			});
			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("notes-position");
			context.setScreen(() -> null);
			context.waitTicks(3);
			context.takeScreenshot("notes-position-hud");
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.notes.clear();
				HudManager.reset(NotesHud.INSTANCE);
			});

			context.setScreen(() -> new SettingsScreen(null).select("Inventar & Handel", "Auktionshaus"));
			context.waitTicks(5);
			context.takeScreenshot("auction-config");
			context.setScreen(() -> null);
		}
		AuctionSearch.forceActive = false;
		setLanguage(context, "en_us");
	}

	private static void setLanguage(ClientGameTestContext context, String code) {
		context.runOnClient(client -> {
			client.options.languageCode = code;
			client.getLanguageManager().setSelected(code);
			client.reloadResourcePacks();
		});
		context.waitFor(client -> client.gui.overlay() == null, 1200);
		context.waitTicks(5);
	}

	private static void checkPrice(String line, double expected) {
		double got = AuctionLore.parsePrice(line).orElse(-1.0);
		System.out.println("AUCTION price '" + line + "' -> " + got);
		if (Math.abs(got - expected) > 1e-9) {
			throw new AssertionError(line + " parsed as " + got + ", expected " + expected);
		}
	}

	private static void clear(ClientGameTestContext context) {
		context.runOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.EditBox box) {
					box.setValue("");
				}
			}
		});
		context.waitTicks(2);
	}

	private static void openSign(TestSingleplayerContext singleplayer, BlockPos pos) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			SignBlockEntity sign = (SignBlockEntity) server.overworld().getBlockEntity(pos);
			sign.setAllowedPlayerEditor(player.getUUID());
			player.openTextEdit(sign, SignTextSlot.FRONT);
		});
	}
}
