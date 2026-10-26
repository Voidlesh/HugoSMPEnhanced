package com.hugosmpenhanced.test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerMob;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPrices;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPricesScreen;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPricesTooltip;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.sun.net.httpserver.HttpServer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if modern
import net.minecraft.world.level.block.entity.BlockEntityTypes;
//? end
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Spawner prices from a local copy of the Voidbot's public price list (VOID Market prices of
 * 12.10.2026): spawner items must show VOID's Ankauf/Verkauf, other items nothing;
 * the overview lists every kind; a failed refresh keeps
 * the last prices.
 */
@SuppressWarnings("UnstableApiUsage")
public class SpawnerPricesGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		checkText();
		HttpServer http = serve();
		try {
			run(context);
		} finally {
			http.stop(0);
			SpawnerPrices.forceActive = false;
			System.clearProperty("hugosmpenhanced.spawnerPricesUrl");
		}
	}

	private static void run(ClientGameTestContext context) {
		SpawnerPrices.forceActive = true;
		ModConfigManager.CONFIG.spawnerPricesTooltip = true;
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		check(Boolean.TRUE.equals(SpawnerPrices.refresh().join()), "prices must load: " + SpawnerPrices.lastError());
		SpawnerPrices.Snapshot snapshot = SpawnerPrices.snapshot();
		SpawnerPrices.Server voidMarket = snapshot.server();
		check(voidMarket != null && voidMarket.name().equals("VOID Market"), "VOID Market prices are read");
		check(voidMarket.buy(SpawnerMob.SKELETON) == 12_500_000L && voidMarket.sell(SpawnerMob.SKELETON) == null,
			"VOID skeleton Ankauf 12.5M, no Verkauf");
		check(voidMarket.buy(SpawnerMob.CREEPER) == 5_000_000L, "VOID creeper 5M");

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();

			ItemStack skeletons = spawnerWithEntity("minecraft:skeleton", 4);
			ItemStack creeper = named(new ItemStack(Items.SPAWNER), "Creeper Spawner", ChatFormatting.GREEN);
			ItemStack golem = named(new ItemStack(Items.SPAWNER), "Spawner", ChatFormatting.YELLOW);
			golem.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Typ: Eisengolem").withStyle(ChatFormatting.GRAY))));
			ItemStack blaze = named(new ItemStack(Items.SPAWNER), "Lohe-Spawner", ChatFormatting.GOLD);
			ItemStack zombie = spawnerWithEntity("minecraft:zombie", 1);
			ItemStack caveSpider = named(new ItemStack(Items.SPAWNER), "Höhlenspinnen-Spawner", ChatFormatting.WHITE);
			ItemStack diamonds = new ItemStack(Items.DIAMOND, 3);

			check(context.computeOnClient(c -> SpawnerPricesTooltip.mobOf(skeletons)) == SpawnerMob.SKELETON, "skeleton from spawner data");
			check(context.computeOnClient(c -> SpawnerPricesTooltip.mobOf(creeper)) == SpawnerMob.CREEPER, "creeper from name");
			check(context.computeOnClient(c -> SpawnerPricesTooltip.mobOf(golem)) == SpawnerMob.IRON_GOLEM, "golem from lore");
			check(context.computeOnClient(c -> SpawnerPricesTooltip.mobOf(zombie)) == null, "zombie spawners have no price");
			check(context.computeOnClient(c -> SpawnerPricesTooltip.mobOf(caveSpider)) == null, "cave spider is not spider");
			check(context.computeOnClient(c -> SpawnerPricesTooltip.mobOf(diamonds)) == null, "diamonds are no spawner");

			String skeletonTooltip = tooltip(context, skeletons);
			System.out.println("SPAWNER tooltip skeletons:\n" + skeletonTooltip);
			check(skeletonTooltip.contains("VOID Market") && skeletonTooltip.contains("Ankauf: $12,5 Mio")
				&& skeletonTooltip.contains("4× = $50 Mio") && skeletonTooltip.contains("Verkauf: kein Angebot"), "skeleton tooltip shows VOID prices");
			String golemTooltip = tooltip(context, golem);
			System.out.println("SPAWNER tooltip golem:\n" + golemTooltip);
			check(golemTooltip.contains("Ankauf: $9 Mio"), "golem Ankauf");
			check(tooltip(context, blaze).contains("Handelt gerade keine Lohe-Spawner"), "blaze: nobody trades it");
			check(!tooltip(context, diamonds).contains("Ankauf"), "no prices on diamonds");
			check(!tooltip(context, zombie).contains("Ankauf"), "no prices on zombie spawners");

			// An auction page with spawner listings.
			context.runOnClient(client -> {
				SimpleContainer page = new SimpleContainer(54);
				page.setItem(10, skeletons);
				page.setItem(11, creeper);
				page.setItem(12, golem);
				page.setItem(13, blaze);
				page.setItem(14, zombie);
				page.setItem(15, diamonds);
				var inventory = client.player.getInventory();
				client.setScreenAndShow(new ContainerScreen(new ChestMenu(MenuType.GENERIC_9x6, 0, inventory, page, 6), inventory,
					Component.literal("Auktionshaus")));
			});
			context.waitTicks(5);
			hoverSlotOf(context, skeletons);
			context.takeScreenshot("spawner-preise-tooltip-skelett");
			hoverSlotOf(context, creeper);
			context.takeScreenshot("spawner-preise-tooltip-creeper");
			hoverSlotOf(context, golem);
			context.takeScreenshot("spawner-preise-tooltip-golem");
			hoverSlotOf(context, blaze);
			context.takeScreenshot("spawner-preise-tooltip-ohne-angebot");
			context.getInput().setCursorPos(0, 0);

			context.setScreen(() -> new SpawnerPricesScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("spawner-preise-uebersicht");

			// A failed refresh keeps the last prices and says so.
			System.setProperty("hugosmpenhanced.spawnerPricesUrl", "http://127.0.0.1:1/nothing");
			check(!SpawnerPrices.refresh().join(), "refresh against a dead address must fail");
			check(SpawnerPrices.snapshot() != null && SpawnerPrices.lastError() != null, "old prices stay after a failed refresh");
			context.setScreen(() -> new SpawnerPricesScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("spawner-preise-uebersicht-offline");

			context.setScreen(() -> new SettingsScreen(null).select("Inventar & Handel", "Spawner"));
			context.waitTicks(5);
			context.takeScreenshot("spawner-preise-einstellungen");
			context.setScreen(() -> null);
		}
	}

	private static void checkText() {
		check(SpawnerMob.inText("Skelett-Spawner") == SpawnerMob.SKELETON, "Skelett-Spawner");
		check(SpawnerMob.inText("Skelettspawner") == SpawnerMob.SKELETON, "Skelettspawner");
		check(SpawnerMob.inText("Iron Golem Spawner") == SpawnerMob.IRON_GOLEM, "Iron Golem Spawner");
		check(SpawnerMob.inText("Zombie Pigman Spawner") == SpawnerMob.PIG, "Zombie Pigman");
		check(SpawnerMob.inText("Kuh-Spawner") == SpawnerMob.COW, "Kuh");
		check(SpawnerMob.inText("Spinnen-Spawner") == SpawnerMob.SPIDER && SpawnerMob.inText("Spinne") == SpawnerMob.SPIDER, "Spinne");
		check(SpawnerMob.inText("Höhlenspinne Spawner") == null, "Höhlenspinne");
		check(SpawnerMob.inText("Mooshroom Spawner") == null, "Mooshroom");
		check(SpawnerMob.inText("Kuhle Sache") == null, "Kuhle is not Kuh");
		check(SpawnerMob.inText("Spawner") == null, "plain Spawner");
		check(SpawnerPrices.format(12_500_000).equals("$12,5 Mio"), SpawnerPrices.format(12_500_000));
		check(SpawnerPrices.format(5_000_000).equals("$5 Mio"), SpawnerPrices.format(5_000_000));
		check(SpawnerPrices.format(8_950_000).equals("$8,95 Mio"), SpawnerPrices.format(8_950_000));
		check(SpawnerPrices.format(750_000).equals("$750.000"), SpawnerPrices.format(750_000));
	}

	private static HttpServer serve() {
		try (InputStream in = SpawnerPricesGameTest.class.getClassLoader().getResourceAsStream("spawner-prices-test.json")) {
			byte[] body = in.readAllBytes();
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/v1/spawner-prices", exchange -> {
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			System.setProperty("hugosmpenhanced.spawnerPricesUrl",
				"http://127.0.0.1:" + server.getAddress().getPort() + "/v1/spawner-prices");
			return server;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String tooltip(ClientGameTestContext context, ItemStack stack) {
		return context.computeOnClient(client -> stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)
			.stream().map(line -> line.getString()).collect(Collectors.joining("\n")));
	}

	private static ItemStack spawnerWithEntity(String entity, int count) {
		ItemStack stack = new ItemStack(Items.SPAWNER, count);
		CompoundTag entityTag = new CompoundTag();
		entityTag.putString("id", entity);
		CompoundTag spawnData = new CompoundTag();
		spawnData.put("entity", entityTag);
		CompoundTag tag = new CompoundTag();
		tag.put("SpawnData", spawnData);
		//? if modern
		stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityTypes.MOB_SPAWNER, tag));
		//? else
		stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityType.MOB_SPAWNER, tag));
		//? end
		return stack;
	}

	private static ItemStack named(ItemStack stack, String name, ChatFormatting color) {
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false)));
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
