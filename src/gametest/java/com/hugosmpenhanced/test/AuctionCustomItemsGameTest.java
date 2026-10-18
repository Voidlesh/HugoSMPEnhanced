package com.hugosmpenhanced.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.auction.AuctionPriceMemory;
import com.hugosmpenhanced.client.feature.auction.AuctionSearch;
import com.hugosmpenhanced.client.feature.auction.AuctionSearchScreen;
import com.hugosmpenhanced.client.feature.auction.CustomItems;
import com.hugosmpenhanced.client.feature.auction.LimitedItems;
import com.hugosmpenhanced.client.feature.auction.LimitedOffers;
import com.hugosmpenhanced.client.feature.auction.MarkedItems;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * An auction house page like HugoSMP's, with custom items and limited items in several lore
 * formats: the custom items must be remembered and suggested by the search, and the cheapest
 * limited offer per hour of time left must be marked, not the cheapest one.
 */
@SuppressWarnings("UnstableApiUsage")
public class AuctionCustomItemsGameTest implements FabricClientGameTest {
	private static final long HOUR = 3_600_000L;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin"));

	@Override
	public void runTest(ClientGameTestContext context) {
		checkParser();
		AuctionSearch.forceActive = true;
		LimitedOffers.forceActive = true;
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		ModConfigManager.CONFIG.auctionSearchEnabled = true;
		ModConfigManager.CONFIG.customItemsEnabled = true;
		ModConfigManager.CONFIG.limitedItemsPerHour = true;
		ModConfigManager.CONFIG.limitedItemsHighlightBest = true;
		ModConfigManager.CONFIG.limitedItemsLifetimeHours = 72;
		ModConfigManager.CONFIG.auctionSearchHistory.clear();
		ModConfigManager.CONFIG.auctionSearchFavorites.clear();
		ModConfigManager.CONFIG.auctionMarkedItems.clear();
		ModConfigManager.CONFIG.auctionMarkHighlight = true;
		ModConfigManager.CONFIG.limitedItemsSellHint = true;
		CustomItems.clear();

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			long now = System.currentTimeMillis();

			// The cheapest (A) and the freshest (B) are not the best per hour; C is.
			ItemStack featherA = phoenixFeather(List.of("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 70 * HOUR))), 3_000);
			ItemStack featherB = phoenixFeather(List.of("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 2 * HOUR))), 9_000);
			ItemStack featherC = phoenixFeather(List.of("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 30 * HOUR))), 5_000);
			ItemStack featherD = phoenixFeather(List.of("Verfällt in: 2T 5Std"), 7_000);
			ItemStack swordA = eventSword("Restzeit: 47:12:05", 20_000);
			ItemStack swordB = eventSword("Verbleibend: 10 Stunden", 4_000);
			ItemStack crown = offered(named(new ItemStack(Items.GOLDEN_HELMET), "Hugos Krone", ChatFormatting.GOLD), List.of("Nur für Könige"), 50_000);
			ItemStack diamonds = offered(new ItemStack(Items.DIAMOND, 16), List.of(), 2_400);
			ItemStack nextPage = named(new ItemStack(Items.ARROW), "Nächste Seite", ChatFormatting.GREEN);
			// Buttons from the server's resource pack have their own model too, but no price.
			nextPage.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(7f), List.of(), List.of(), List.of()));
			ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
			filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

			context.runOnClient(client -> {
				SimpleContainer page = new SimpleContainer(54);
				ItemStack[] listings = {featherA, featherB, swordA, crown, featherC, diamonds, swordB, featherD};
				for (int i = 0; i < listings.length; i++) {
					page.setItem(10 + i + (i >= 5 ? 2 : 0), listings[i]);
				}
				for (int i = 45; i < 54; i++) {
					page.setItem(i, filler.copy());
				}
				page.setItem(53, nextPage);
				var inventory = client.player.getInventory();
				ItemStack own = phoenixFeather(List.of("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 12 * HOUR))), 0);
				own.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Limitiert").withStyle(ChatFormatting.LIGHT_PURPLE),
					Component.literal("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 12 * HOUR))).withStyle(ChatFormatting.GRAY))));
				inventory.setItem(0, own);
				client.setScreenAndShow(new ContainerScreen(new ChestMenu(MenuType.GENERIC_9x6, 0, inventory, page, 6), inventory,
					Component.literal("Auktionshaus")));
			});
			context.waitTicks(5);

			check(context.computeOnClient(client -> LimitedOffers.offer(featherC) != null && LimitedOffers.offer(featherC).best()),
				"feather C (5.000 for 42h) must be the best per hour");
			check(context.computeOnClient(client -> !LimitedOffers.offer(featherA).best() && !LimitedOffers.offer(featherB).best()),
				"cheapest and freshest feather must not be marked");
			check(context.computeOnClient(client -> LimitedOffers.offer(swordB).best() && !LimitedOffers.offer(swordA).best()),
				"sword with 10 hours for 4.000 must beat 47 hours for 20.000");
			check(context.computeOnClient(client -> LimitedOffers.offer(crown) == null && LimitedOffers.offer(diamonds) == null),
				"items that are not limited have no offer");
			List<String> names = context.computeOnClient(client -> CustomItems.names());
			System.out.println("CUSTOM known items: " + names);
			check(names.contains("Phönixfeder") && names.contains("Eventschwert") && names.contains("Hugos Krone"),
				"custom items must be remembered: " + names);
			check(!names.contains("Nächste Seite") && !names.contains(" ") && !names.contains("Diamant"),
				"menu buttons and vanilla items are not custom items: " + names);
			context.runOnClient(client -> {
				for (ItemStack stack : List.of(featherA, featherB, featherC, featherD, swordA, swordB)) {
					LimitedOffers.Offer offer = LimitedOffers.offer(stack);
					System.out.println("CUSTOM offer " + stack.getHoverName().getString() + " perItem=" + offer.perItem() + " perHour="
						+ offer.perHour() + " best=" + offer.best() + " rank=" + offer.rank() + "/" + offer.ofKind());
				}
			});
			context.takeScreenshot("ah-limitiert-markiert");
			// At GUI scale 2 the 854x480 test window has no room under a 6-row menu; scale 1 shows the summary line.
			context.runOnClient(client -> {
				client.options.guiScale().set(1);
				client.resizeGui();
			});
			context.waitTicks(3);
			context.takeScreenshot("ah-limitiert-uebersicht");
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				client.resizeGui();
			});
			context.waitTicks(3);

			hoverSlotOf(context, featherC);
			context.takeScreenshot("ah-limitiert-tooltip-bestes");
			hoverSlotOf(context, featherA);
			context.takeScreenshot("ah-limitiert-tooltip-teuer");
			hoverSlotOf(context, crown);
			context.takeScreenshot("ah-custom-item-tooltip");
			context.getInput().setCursorPos(0, 0);
			context.setScreen(() -> null);
			context.waitTicks(3);

			// The search suggests the remembered items with their colour, model and price per hour.
			BlockPos pos = context.computeOnClient(client -> client.player.blockPosition().offset(0, 0, 2));
			singleplayer.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
				+ " oak_sign{front_text:{messages:[\"\",\"^^^^^^^^^^^^^^^\",\"Suchbegriff\",\"(min. 3 Zeichen)\"]}}");
			context.waitTicks(5);
			openSign(singleplayer, pos);
			context.waitTicks(10);
			check(context.computeOnClient(client -> client.gui.screen() instanceof AuctionSearchScreen), "search screen must open");
			context.getInput().typeChars("phön");
			context.waitTicks(3);
			context.takeScreenshot("ah-suche-custom-item");
			List<AuctionSearch.Match> matches = context.computeOnClient(client -> AuctionSearch.search("phön", 5));
			check(!matches.isEmpty() && matches.get(0).entry().custom() && matches.get(0).entry().gameName().equals("Phönixfeder"),
				"custom item must be the first suggestion for 'phön'");
			check(context.computeOnClient(client -> AuctionPriceMemory.getPerHour(matches.get(0).entry().priceKey(),
				com.hugosmpenhanced.client.feature.auction.AuctionMarket.AUCTION) != null), "best price per hour must be remembered");
			check(context.computeOnClient(client -> AuctionPriceMemory.get("custom:" + CustomItems.idOf(crown, true),
				com.hugosmpenhanced.client.feature.auction.AuctionMarket.AUCTION) != null), "prices on the page are remembered without hovering");
			clear(context);
			context.getInput().typeChars("krone");
			context.waitTicks(3);
			context.takeScreenshot("ah-suche-custom-krone");
			clear(context);
			context.getInput().typeChars("event");
			context.waitTicks(3);
			context.takeScreenshot("ah-suche-custom-limitiert");
			checkIcons(context, "seen this session");
			// After a restart the icons come from the saved copies and must still show the custom textures.
			context.runOnClient(client -> CustomItems.reloadForTest());
			checkIcons(context, "after restart");
			clear(context);
			context.getInput().typeChars("phön");
			context.waitTicks(3);
			context.takeScreenshot("ah-suche-textur-nach-neustart");
			// Hovering the suggestion shows the item's own tooltip.
			double[] row = context.computeOnClient(client -> {
				double scale = client.getWindow().getGuiScale();
				int width = Math.min(client.gui.screen().width - 20, 300);
				int left = (client.gui.screen().width - width) / 2;
				// The first suggestion is the second row, below the "search for" row.
				int field = 20;
				int top = Math.max(18, (client.gui.screen().height - (field + 6 + 9 * 23 + 46)) / 2 + 10);
				return new double[] {(left + 60) * scale, (top + field + 6 + 23 + 10) * scale};
			});
			context.getInput().setCursorPos(row[0], row[1]);
			context.waitTicks(3);
			context.takeScreenshot("ah-suche-custom-tooltip");
			context.getInput().setCursorPos(0, 0);
			clear(context);
			context.getInput().typeChars("event");
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_DOWN);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(10);
			String line = singleplayer.getServer().computeOnServer(server ->
				((SignBlockEntity) server.overworld().getBlockEntity(pos)).getText(SignTextSlot.FRONT).getMessages(false).get(0).getString());
			System.out.println("CUSTOM sign line after enter: '" + line + "'");
			check("Eventschwert".equals(line), "Enter must send the custom item's name, got '" + line + "'");

			// The own inventory shows the time left too.
			context.runOnClient(client -> client.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
			context.waitTicks(5);
			double[] own = context.computeOnClient(client -> {
				AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.gui.screen();
				Slot slot = ((net.minecraft.client.gui.screens.inventory.InventoryScreen) client.gui.screen()).getMenu().slots.get(36);
				double scale = client.getWindow().getGuiScale();
				return new double[] {(screen.hugosmpenhanced$getLeftPos() + slot.x + 8) * scale,
					(screen.hugosmpenhanced$getTopPos() + slot.y + 8) * scale};
			});
			context.getInput().setCursorPos(own[0], own[1]);
			context.waitTicks(3);
			context.takeScreenshot("inventar-limitiert-tooltip");
			context.getInput().setCursorPos(0, 0);
			context.setScreen(() -> null);

			checkMarkButton(context, singleplayer, pos.offset(2, 0, 0));
			checkPages(context, now);
			// The search drew the stored copy first; the item the server sends later this session replaces it.
			context.runOnClient(client -> {
				CustomItems.reloadForTest();
				AuctionSearch.search("Phönixfeder", 1).get(0).entry();
				ItemStack changed = featherC.copy();
				changed.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.parse("minecraft:blaze_rod"));
				CustomItems.learn(changed, true);
				var model = AuctionSearch.search("Phönixfeder", 1).get(0).entry().icon().get(DataComponents.ITEM_MODEL);
				System.out.println("CUSTOM icon after live sighting: " + model);
				check(model != null && model.toString().equals("minecraft:blaze_rod"), "a live sighting must replace the stored icon, got " + model);
			});

			context.setScreen(() -> new SettingsScreen(null).select("Inventar & Handel", "Auktionshaus"));
			context.waitTicks(5);
			context.runOnClient(client -> client.gui.screen().mouseScrolled(client.gui.screen().width / 2.0, client.gui.screen().height / 2.0, 0, -20));
			context.waitTicks(3);
			context.takeScreenshot("ah-einstellungen-limitiert");
			context.runOnClient(client -> client.gui.screen().mouseScrolled(client.gui.screen().width / 2.0, client.gui.screen().height / 2.0, 0, 8));
			context.waitTicks(3);
			context.takeScreenshot("ah-einstellungen-markieren");
			context.setScreen(() -> null);
			context.runOnClient(client -> client.player.getInventory().setItem(0, ItemStack.EMPTY));
		}
		ModConfigManager.CONFIG.auctionMarkedItems.clear();
		CustomItems.clear();
		AuctionSearch.forceActive = false;
		LimitedOffers.forceActive = false;
	}

	private static void checkParser() {
		long now = 1_760_000_000_000L;
		long life = 72 * HOUR;
		expect("Läuft ab in: 2d 4h", now, life, now + 52 * HOUR);
		expect("Verbleibend: 1 Tag 3 Stunden", now, life, now + 27 * HOUR);
		expect("Restzeit: 47:12:05", now, life, now + (47 * 3600 + 12 * 60 + 5) * 1000L);
		expect("Noch 2T 5Std 30Min", now, life, now + 53 * HOUR + 30 * 60_000L);
		expect("Erstellt vor 5 Stunden", now, life, now - 5 * HOUR + life);
		expect("Expires in 3h", now, life, now + 3 * HOUR);
		long created = LimitedItems.parseDate("10.10.2026 14:30");
		expect("Erstellt: 10.10.2026 14:30", now, life, created + life);
		expect("Erstellt am 10.10.2026 um 14:30 Uhr", now, life, created + life);
		expect("Verfällt am 13.10.2026 um 14:30 Uhr", now, life, created + life);
		expect("Created: 2026-10-10 14:30", now, life, created + life);
		// A description of the lifetime and the auction's own end are not the item's time left.
		expect(List.of("Limitiert", "Verfällt 3 Tage nach der Erstellung"), now, life, -1);
		expect(List.of("Limitiert", "", "Verkäufer: Hugo", "Preis: $500", "Läuft ab in: 1d 23h"), now, life, -1);
		expect(List.of("Limitiert", "Verbleibend: 5h", "-----", "Preis: $500", "Angebot läuft ab in: 1d"), now, life, now + 5 * HOUR);
		// HugoSMP's real format: the time under "Läuft ab in:", repeated below the auction block next to the offer's own end.
		long hugo = (17 * 60 + 21) * 60_000L;
		expectStack(List.of("Baut 9 Blöcke im 3x3 Bereich ab", "Läuft ab in:", "0d 17h 21m", "", "Preis: $20.000.000",
			"Verkäufer: Ebruegge", "Läuft ab in: 23h 14m 17s", "Star-Tool läuft ab in: 0d 17h 21m", "Achtung: Läuft in 0d 17h 21m ab",
			"und wird dann entfernt!"), now, life, now + hugo);
		expectStack(List.of("Baut 9 Blöcke im 3x3 Bereich ab", "Läuft ab in:", "0d 17h 21m", "", "Preis: $20.000.000",
			"Verkäufer: Ebruegge", "Läuft ab in: 23h 14m 17s"), now, life, now + hugo);
		expectStack(List.of("Preis: $20.000.000", "Verkäufer: Ebruegge", "Läuft ab in: 23h 14m 17s",
			"Achtung: Läuft in 5h ab", "und wird dann entfernt!"), now, life, now + 5 * HOUR);
		// A normal offer: only the offer runs out, the item does not.
		expectStack(List.of("Effizienz V", "", "Preis: $500", "Verkäufer: Ebruegge", "Läuft ab in: 23h 14m 17s"), now, life, -1);
		check(LimitedItems.formatRemaining(53 * HOUR).equals("2T 5Std"), "format " + LimitedItems.formatRemaining(53 * HOUR));
		check(LimitedItems.formatRemaining(90 * 60_000L).equals("1Std 30Min"), "format " + LimitedItems.formatRemaining(90 * 60_000L));
	}

	private static void expect(String line, long now, long life, long expected) {
		expect(List.of("Limitiert", line), now, life, expected);
	}

	private static void expect(List<String> lines, long now, long life, long expected) {
		Optional<LimitedItems.Expiry> expiry = LimitedItems.fromLore(LimitedItems.withoutListingBlock(lines), now, life);
		long got = expiry.map(LimitedItems.Expiry::expiresAt).orElse(-1L);
		System.out.println("CUSTOM expiry " + lines + " -> " + (got < 0 ? "none" : (got - now) / 60_000 + " min") + " "
			+ expiry.map(LimitedItems.Expiry::source).orElse(""));
		check(got == expected, lines + " gave " + got + ", expected " + expected);
	}

	private static void expectStack(List<String> lines, long now, long life, long expected) {
		ItemStack stack = new ItemStack(Items.NETHERITE_PICKAXE);
		stack.set(DataComponents.LORE, new ItemLore(lines.stream().map(line -> (Component) Component.literal(line)).toList()));
		// A key that holds when the offer was made is not the item's creation time.
		CompoundTag data = new CompoundTag();
		data.putLong("listedTime", now - HOUR);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		Optional<LimitedItems.Expiry> expiry = LimitedItems.expiry(stack, now, life);
		long got = expiry.map(LimitedItems.Expiry::expiresAt).orElse(-1L);
		System.out.println("CUSTOM expiry stack " + lines + " -> " + (got < 0 ? "none" : (got - now) / 60_000 + " min") + " "
			+ expiry.map(LimitedItems.Expiry::source).orElse(""));
		check(got == expected, lines + " gave " + got + ", expected " + expected);
	}

	/** The box left of the star marks exactly that item; sand and suspicious sand are told apart. */
	private static void checkMarkButton(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos) {
		singleplayer.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
			+ " oak_sign{front_text:{messages:[\"\",\"^^^^^^^^^^^^^^^\",\"Suchbegriff\",\"(min. 3 Zeichen)\"]}}");
		context.waitTicks(5);
		openSign(singleplayer, pos);
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.gui.screen() instanceof AuctionSearchScreen), "search screen must open for marking");
		context.getInput().typeChars("sand");
		context.waitTicks(3);
		String first = context.computeOnClient(client -> AuctionSearch.search("sand", 3).get(0).entry().priceKey());
		System.out.println("CUSTOM first suggestion for sand: " + first);
		check("minecraft:sand".equals(first), "Sand must be the first suggestion, got " + first);
		double[] box = context.computeOnClient(client -> {
			double scale = client.getWindow().getGuiScale();
			int width = Math.min(client.gui.screen().width - 20, 300);
			int left = (client.gui.screen().width - width) / 2;
			int field = 20;
			int top = Math.max(18, (client.gui.screen().height - (field + 6 + 9 * 23 + 46)) / 2 + 10);
			int markX = left + width - 16 - 4 - 14;
			return new double[] {(markX + 6) * scale, (top + field + 6 + 23 + 10) * scale};
		});
		context.getInput().setCursorPos(box[0], box[1]);
		context.waitTicks(3);
		context.takeScreenshot("ah-suche-markieren-hover");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		check(MarkedItems.isMarked("minecraft:sand"), "clicking the box must mark sand");
		check(!MarkedItems.isMarked("minecraft:suspicious_sand"), "suspicious sand stays unmarked");
		check(context.computeOnClient(client -> client.gui.screen() instanceof AuctionSearchScreen), "marking must not send the search");
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.takeScreenshot("ah-suche-markiert");
		context.setScreen(() -> null);
		context.waitTicks(3);
	}

	/**
	 * Two pages: the best feather is on page 2. Back on page 1 the cheaper looking feather there is
	 * not framed, the arrow to page 2 lights up, and the own feather's tooltip suggests a price.
	 */
	private static void checkPages(ClientGameTestContext context, long now) {
		ItemStack pricey = phoenixFeather(List.of("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 2 * HOUR))), 9_000);
		ItemStack cheap = phoenixFeather(List.of("Erstellt: " + DATE.format(Instant.ofEpochMilli(now - 32 * HOUR))), 3_000);
		ItemStack sand = offered(new ItemStack(Items.SAND, 64), List.of(), 640);
		ItemStack suspicious = offered(new ItemStack(Items.SUSPICIOUS_SAND, 1), List.of(), 900);
		ItemStack next = named(new ItemStack(Items.ARROW), "Nächste Seite", ChatFormatting.GREEN);
		ItemStack previous = named(new ItemStack(Items.ARROW), "Vorherige Seite", ChatFormatting.GREEN);
		LimitedOffers.newBrowse();
		openPage(context, "Auktionshaus (1/2)", List.of(pricey, sand, suspicious), previous, next);
		check(context.computeOnClient(client -> LimitedOffers.offer(pricey).best()), "alone on page 1 the feather is the best so far");
		openPage(context, "Auktionshaus (2/2)", List.of(cheap), previous, next);
		check(context.computeOnClient(client -> LimitedOffers.currentPage() == 2), "page 2 must be read from the title");
		check(context.computeOnClient(client -> LimitedOffers.offer(cheap).best() && LimitedOffers.arrowHint(previous) == 0),
			"page 2 holds the best feather; the arrow back has nothing better");
		ItemStack pricey2 = pricey.copy();
		ItemStack sand2 = sand.copy();
		ItemStack suspicious2 = suspicious.copy();
		ItemStack next2 = next.copy();
		openPage(context, "Auktionshaus (1/2)", List.of(pricey2, sand2, suspicious2), previous.copy(), next2);
		check(context.computeOnClient(client -> !LimitedOffers.offer(pricey2).best() && LimitedOffers.offer(pricey2).bestPage() == 2),
			"back on page 1 the feather there is not the best, the best is on page 2");
		check(context.computeOnClient(client -> LimitedOffers.arrowHint(next2) == 1), "the arrow to page 2 must light up");
		check(context.computeOnClient(client -> MarkedItems.isMarked(sand2) && !MarkedItems.isMarked(suspicious2)),
			"only sand is marked in the auction house");
		context.takeScreenshot("ah-seiten-pfeil-markiert");
		hoverSlotOf(context, next2);
		context.takeScreenshot("ah-seiten-pfeil-tooltip");
		hoverSlotOf(context, pricey2);
		context.takeScreenshot("ah-seiten-platz-tooltip");

		// The own feather has 60 hours left; the best costs 3.000 for 40 hours = 75/h, so up to 4.500 beats it.
		ItemStack own = context.computeOnClient(client -> client.player.getInventory().getItem(0));
		var hint = context.computeOnClient(client -> LimitedOffers.sellHintFor(own, 60 * HOUR - 60_000L, System.currentTimeMillis()));
		System.out.println("CUSTOM sell hint: " + hint);
		check(hint.isPresent() && hint.get().perItem() < 4_500 && hint.get().perItem() > 4_300 && hint.get().bestPage() == 2,
			"sell hint must stay just under 4.500: " + hint);
		hoverSlotOf(context, own);
		context.takeScreenshot("ah-verkaufspreis-tipp");
		context.getInput().setCursorPos(0, 0);
		context.setScreen(() -> null);
		context.waitTicks(3);
	}

	private static void openPage(ClientGameTestContext context, String title, List<ItemStack> offers, ItemStack previous, ItemStack next) {
		ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
		filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		context.runOnClient(client -> {
			SimpleContainer page = new SimpleContainer(54);
			for (int i = 0; i < offers.size(); i++) {
				page.setItem(10 + i, offers.get(i));
			}
			for (int i = 45; i < 54; i++) {
				page.setItem(i, filler.copy());
			}
			page.setItem(45, previous);
			page.setItem(53, next);
			var inventory = client.player.getInventory();
			client.setScreenAndShow(new ContainerScreen(new ChestMenu(MenuType.GENERIC_9x6, 0, inventory, page, 6), inventory,
				Component.literal(title)));
		});
		context.waitTicks(5);
	}

	private static ItemStack phoenixFeather(List<String> timeLines, double price) {
		ItemStack stack = named(new ItemStack(Items.FEATHER), "Phönixfeder", ChatFormatting.GOLD);
		CompoundTag bukkit = new CompoundTag();
		bukkit.putString("hugosmp:item_id", "phoenix_feather");
		bukkit.putString("hugosmp:uuid", java.util.UUID.randomUUID().toString());
		CompoundTag root = new CompoundTag();
		root.put("PublicBukkitValues", bukkit);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		// Its own texture, like an item from HugoSMP's resource pack (here from the test mod's assets).
		stack.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.parse("hugosmpenhanced-test:phoenix_feather"));
		List<String> lore = new ArrayList<>();
		lore.add("✦ Limitiertes Event-Item");
		lore.add("Verbrennt nach 3 Tagen zu Asche.");
		lore.addAll(timeLines);
		return price > 0 ? offered(stack, lore, price) : stack;
	}

	private static ItemStack eventSword(String timeLine, double price) {
		ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Eventschwert").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(1001f), List.of(), List.of(), List.of()));
		return offered(stack, List.of("Limitiert", timeLine), price);
	}

	private static ItemStack named(ItemStack stack, String name, ChatFormatting color) {
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false)));
		return stack;
	}

	/** Lore like a HugoSMP auction listing: the item's lines, then seller, price and the offer's own end. */
	private static ItemStack offered(ItemStack stack, List<String> itemLore, double price) {
		List<Component> lore = new ArrayList<>();
		for (String line : itemLore) {
			lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		lore.add(Component.literal("-----------------").withStyle(ChatFormatting.DARK_GRAY));
		lore.add(Component.literal("Verkäufer: HugoFan123").withStyle(ChatFormatting.GRAY));
		lore.add(Component.literal("Preis: $" + String.format(java.util.Locale.GERMANY, "%,.0f", price)).withStyle(ChatFormatting.GOLD));
		lore.add(Component.literal("Läuft ab in: 1d 23h").withStyle(ChatFormatting.GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static void hoverSlotOf(ClientGameTestContext context, ItemStack stack) {
		double[] pos = context.computeOnClient(client -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.gui.screen();
			double scale = client.getWindow().getGuiScale();
			for (Slot slot : ((ContainerScreen) client.gui.screen()).getMenu().slots) {
				if (slot.getItem() == stack) {
					return new double[] {(screen.hugosmpenhanced$getLeftPos() + slot.x + 8) * scale,
						(screen.hugosmpenhanced$getTopPos() + slot.y + 8) * scale};
				}
			}
			throw new AssertionError("Stack not in the menu");
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
	}

	private static void checkIcons(ClientGameTestContext context, String when) {
		context.runOnClient(client -> {
			AuctionSearch.Entry feather = AuctionSearch.search("Phönixfeder", 1).get(0).entry();
			AuctionSearch.Entry sword = AuctionSearch.search("Eventschwert", 1).get(0).entry();
			var model = feather.icon().get(DataComponents.ITEM_MODEL);
			var modelData = sword.icon().get(DataComponents.CUSTOM_MODEL_DATA);
			System.out.println("CUSTOM icon " + when + ": feather model=" + model + " sword data=" + modelData);
			check(model != null && model.toString().equals("hugosmpenhanced-test:phoenix_feather"), "feather icon lost its model " + when);
			check(modelData != null && modelData.getFloat(0) != null && modelData.getFloat(0) == 1001f, "sword icon lost its model data " + when);
			List<String> lore = CustomItems.tooltipCopy(feather.icon()).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
				.map(Component::getString).toList();
			System.out.println("CUSTOM icon lore " + when + ": " + lore);
			check(feather.icon().getCount() == 1, "icon is a single item");
			check(lore.contains("✦ Limitiertes Event-Item") && lore.stream().noneMatch(line -> line.startsWith("Preis")
				|| line.startsWith("Verkäufer") || line.startsWith("-----")), "the tooltip keeps the item's lore without the auction lines: " + lore);
		});
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

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}
}
