package com.hugosmpenhanced.test;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? end
import java.util.function.Predicate;

//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.sell.SellAutostart;
import com.hugosmpenhanced.client.feature.sell.SellEngine;
import com.hugosmpenhanced.client.feature.sell.SellOverlay;
import com.hugosmpenhanced.client.feature.sell.SellRule;
import com.hugosmpenhanced.client.feature.sell.SellSpeed;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
//? if modern
import net.minecraft.world.item.ItemStackTemplate;
//? end
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Runs the sell assistant against a test /sell (see {@link TestSellServer}) in a real client:
 * Alt preview and slot lock in the inventory, the first sale with the confirmation panel, reserve,
 * minimum amount and protection, price learning from the chat message, the AFK farm with its HUD,
 * pausing when the player moves and the autostart after joining.
 */
@SuppressWarnings("UnstableApiUsage")
public class SellGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.menuBackgroundBlurriness().set(0);
			SellEngine.anyServer = true;
			ModConfig config = ModConfigManager.CONFIG;
			config.sellEnabled = true;
			config.sellRules.clear();
			SellRule cane = new SellRule("minecraft:sugar_cane");
			cane.keep = 128;
			config.sellRules.add(cane);
			config.sellRules.add(new SellRule("minecraft:melon_slice"));
			SellRule bamboo = new SellRule("minecraft:bamboo");
			bamboo.minAmount = 128;
			config.sellRules.add(bamboo);
			config.sellRules.add(new SellRule("minecraft:diamond_sword"));
			config.sellRules.add(new SellRule("minecraft:bundle"));
			SellRule kelp = new SellRule("minecraft:kelp");
			kelp.enabled = false;
			config.sellRules.add(kelp);
			config.sellLockedSlots.clear();
			config.sellConfirm = true;
			config.sellConfirmedRules = "";
			config.sellSpeed = SellSpeed.NORMAL;
			config.sellThreshold = 40;
			config.sellPrices.clear();
			config.sellAfkWasOn = false;
			config.sellAutostart = SellAutostart.IF_WAS_ON;
			System.out.println("SELLTEST money: " + SellEngine.parseMoney("Du hast 2.304 Items für $23.040 verkauft!")
				+ " " + SellEngine.parseMoney("+1,5k$") + " " + SellEngine.parseMoney("Verkauft für $ 12,50") + " " + SellEngine.parseMoney("keine Zahl"));
		});

		context.setScreen(() -> new SettingsScreen(null).select("Inventar & Handel", "Verkaufen"));
		context.waitTicks(3);
		context.takeScreenshot("sell-0-einstellungen");
		context.getInput().setCursorPos(500, 400);
		context.getInput().scroll(-10);
		context.waitTicks(3);
		context.takeScreenshot("sell-0-einstellungen-unten");
		context.setScreen(() -> null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamerule sendCommandFeedback false");
			setInventory(context, singleplayer, (player, inv) -> {
				inv.clearContent();
				inv.setItem(0, new ItemStack(Items.NETHERITE_PICKAXE));
				inv.setItem(3, new ItemStack(Items.CARROT, 32));
				inv.setItem(7, new ItemStack(Items.SUGAR_CANE, 12));
				for (int i = 9; i < 27; i++) {
					inv.setItem(i, new ItemStack(Items.SUGAR_CANE, 64));
				}
				for (int i = 27; i < 31; i++) {
					inv.setItem(i, new ItemStack(Items.MELON_SLICE, 64));
				}
				inv.setItem(31, new ItemStack(Items.BAMBOO, 32));
				ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
				sword.enchant(player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
				inv.setItem(32, sword);
				ItemStack bundle = new ItemStack(Items.BUNDLE);
				//? if modern
				bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(java.util.List.of(new ItemStackTemplate(Items.DIAMOND, 16))));
				//? else
				bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(java.util.List.of(new ItemStack(Items.DIAMOND, 16))));
				//? end
				inv.setItem(33, bundle);
				ItemStack named = new ItemStack(Items.SUGAR_CANE, 64);
				named.set(DataComponents.CUSTOM_NAME, Component.literal("Glücks-Zuckerrohr"));
				inv.setItem(34, named);
				inv.setItem(35, new ItemStack(Items.KELP, 64));
			});

			// Alt + right click locks a slot; holding Alt previews what would be sold.
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(3);
			hoverInventorySlot(context, 9);
			context.getInput().holdAlt();
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			context.getInput().setCursorPos(10, 10);
			context.waitTicks(2);
			shot(context, "sell-1-alt-vorschau");
			context.getInput().releaseAlt();
			System.out.println("SELLTEST locked: " + context.computeOnClient(client -> ModConfigManager.CONFIG.sellLockedSlots));
			context.setScreen(() -> null);
			context.waitTicks(2);

			// First sale: G opens /sell, the panel asks once.
			context.getInput().pressKey(InputConstants.KEY_G);
			waitUntil(context, "confirm", client -> SellEngine.state() == SellEngine.State.CONFIRM, 100);
			context.getInput().setCursorPos(10, 10);
			shot(context, "sell-2-vorschau");
			context.getInput().resizeWindow(1920, 1080);
			context.waitTicks(3);
			shot(context, "sell-2b-vorschau-1080p");
			context.getInput().resizeWindow(1600, 900);
			context.waitTicks(3);
			shot(context, "sell-2c-vorschau-900p");
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(3);
			clickPanelButton(context, true);
			context.waitTicks(4);
			shot(context, "sell-3-einlegen");
			waitUntil(context, "first run done", client -> !SellEngine.running(), 600);
			context.waitTicks(30);
			shot(context, "sell-4-verkauft-chat");
			System.out.println("SELLTEST after first run: " + inventorySummary(context, singleplayer) + " sales=" + TestSellServer.sales);

			// Second run with only melons: no question any more, and the price is learned.
			setInventory(context, singleplayer, (player, inv) -> {
				for (int i = 27; i < 31; i++) {
					inv.setItem(i, new ItemStack(Items.MELON_SLICE, 64));
				}
			});
			context.getInput().pressKey(InputConstants.KEY_G);
			waitUntil(context, "second run done", client -> TestSellServer.sales >= 2 && !SellEngine.running(), 400);
			context.waitTicks(30);
			System.out.println("SELLTEST prices: " + context.computeOnClient(client -> ModConfigManager.CONFIG.sellPrices)
				+ " sales=" + TestSellServer.sales + " " + inventorySummary(context, singleplayer));

			// AFK farm with Shift + G: waits outside, collects in the menu until 50 %, sells.
			context.getInput().holdShift();
			context.getInput().pressKey(InputConstants.KEY_G);
			context.getInput().releaseShift();
			context.waitTicks(5);
			System.out.println("SELLTEST afk on: " + context.computeOnClient(client -> SellEngine.afkRunning() + " " + SellEngine.state()
				+ " wasOn=" + ModConfigManager.CONFIG.sellAfkWasOn));
			setInventory(context, singleplayer, (player, inv) -> {
				for (int i = 10; i < 18; i++) {
					inv.setItem(i, new ItemStack(Items.MELON_SLICE, 64));
				}
			});
			waitUntil(context, "afk collecting", client -> SellEngine.state() == SellEngine.State.WAITING && SellEngine.fillPercent() > 0, 200);
			context.getInput().setCursorPos(10, 10);
			shot(context, "sell-5-afk-sammelt");
			int before = TestSellServer.sales;
			setInventory(context, singleplayer, (player, inv) -> {
				for (int i = 18; i < 27; i++) {
					inv.setItem(i, new ItemStack(Items.MELON_SLICE, 64));
				}
			});
			waitUntil(context, "afk sold", client -> TestSellServer.sales > before && SellEngine.boundScreen() == null
				&& SellEngine.state() == SellEngine.State.WAITING, 400);
			context.waitTicks(40);
			shot(context, "sell-6-afk-hud");
			System.out.println("SELLTEST afk sale: sales=" + TestSellServer.sales + " hud sales=" + context.computeOnClient(client -> SellEngine.sales()
				+ " income=" + SellEngine.income()) + " " + inventorySummary(context, singleplayer));

			// Moving pauses the farm, standing still resumes it.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.teleportTo(player.getX() + 3, player.getY(), player.getZ());
			});
			waitUntil(context, "paused", client -> SellEngine.state() == SellEngine.State.PAUSED, 100);
			shot(context, "sell-7-pausiert");
			waitUntil(context, "resumed", client -> SellEngine.state() == SellEngine.State.WAITING, 200);
			System.out.println("SELLTEST resumed: " + context.computeOnClient(client -> SellEngine.state()));

			// Off again with Shift + G; then the autostart after a join.
			context.getInput().holdShift();
			context.getInput().pressKey(InputConstants.KEY_G);
			context.getInput().releaseShift();
			context.waitTicks(3);
			System.out.println("SELLTEST afk off: " + context.computeOnClient(client -> SellEngine.afkRunning() + " wasOn=" + ModConfigManager.CONFIG.sellAfkWasOn));
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.sellAfkWasOn = true;
				SellEngine.onJoin();
			});
			waitUntil(context, "autostart", client -> SellEngine.afkRunning(), 260);
			System.out.println("SELLTEST autostart: " + context.computeOnClient(client -> SellEngine.afkRunning()));

			// Inside the sell menu the key still stops the farm and closes the menu.
			setInventory(context, singleplayer, (player, inv) -> {
				for (int i = 10; i < 14; i++) {
					inv.setItem(i, new ItemStack(Items.MELON_SLICE, 64));
				}
			});
			waitUntil(context, "afk in menu", client -> SellEngine.boundScreen() != null && SellEngine.state() == SellEngine.State.WAITING, 200);
			context.getInput().holdShift();
			context.getInput().pressKey(InputConstants.KEY_G);
			context.getInput().releaseShift();
			context.waitTicks(5);
			System.out.println("SELLTEST stop in menu: running=" + context.computeOnClient(client -> SellEngine.afkRunning()
				+ " screen=" + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName())));
			context.runOnClient(client -> SellEngine.stop("", true));
			context.waitTicks(3);
		}
		context.runOnClient(client -> {
			SellEngine.anyServer = false;
			ModConfigManager.CONFIG.sellEnabled = false;
		});
	}

	private interface InventoryChange {
		void accept(ServerPlayer player, Inventory inventory);
	}

	private static void setInventory(ClientGameTestContext context, TestSingleplayerContext singleplayer, InventoryChange change) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			change.accept(player, player.getInventory());
			player.containerMenu.broadcastChanges();
			player.inventoryMenu.broadcastChanges();
		});
		context.waitTicks(5);
	}

	private static String inventorySummary(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(server -> {
			Inventory inv = server.getPlayerList().getPlayers().get(0).getInventory();
			int cane = 0;
			int melon = 0;
			int bamboo = 0;
			StringBuilder special = new StringBuilder();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (stack.is(Items.SUGAR_CANE)) {
					cane += stack.getCount();
				} else if (stack.is(Items.MELON_SLICE)) {
					melon += stack.getCount();
				} else if (stack.is(Items.BAMBOO)) {
					bamboo += stack.getCount();
				}
				if (stack.has(DataComponents.CUSTOM_NAME) || stack.is(Items.DIAMOND_SWORD) || stack.is(Items.BUNDLE) || stack.is(Items.KELP)) {
					special.append(stack.getHoverName().getString()).append('@').append(i).append(' ');
				}
			}
			return "cane=" + cane + " melon=" + melon + " bamboo=" + bamboo + " special=[" + special.toString().trim() + "]";
		});
	}

	private static void waitUntil(ClientGameTestContext context, String what, Predicate<Minecraft> condition, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			if (context.computeOnClient(condition::test)) {
				System.out.println("SELLTEST reached " + what + " after " + i + " ticks");
				return;
			}
			context.waitTick();
		}
		System.out.println("SELLTEST TIMEOUT waiting for " + what + " state=" + context.computeOnClient(client -> SellEngine.state()));
	}

	private static void shot(ClientGameTestContext context, String name) {
		//? if modern
		context.runOnClient(client -> client.gui.toastManager().clear());
		//? else
		context.runOnClient(client -> client.getToastManager().clear());
		//? end
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	private static void clickPanelButton(ClientGameTestContext context, boolean confirm) {
		double[] pos = context.computeOnClient(client -> {
			int[] gui = SellOverlay.buttonCenter(confirm);
			if (gui == null) {
				throw new AssertionError("Sell panel button not shown");
			}
			double scale = client.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void hoverInventorySlot(ClientGameTestContext context, int inventoryIndex) {
		double[] pos = context.computeOnClient(client -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.gui.screen();
			for (Slot slot : ((InventoryScreen) client.gui.screen()).getMenu().slots) {
				if (slot.container instanceof Inventory && slot.getContainerSlot() == inventoryIndex) {
					double scale = client.getWindow().getGuiScale();
					return new double[] {(screen.hugosmpenhanced$getLeftPos() + slot.x + 8) * scale, (screen.hugosmpenhanced$getTopPos() + slot.y + 8) * scale};
				}
			}
			throw new AssertionError("No slot " + inventoryIndex);
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
	}
}
