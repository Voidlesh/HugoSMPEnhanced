package com.hugosmpenhanced.test;

import java.util.ArrayList;

import com.hugosmpenhanced.client.config.ConfigScreenBuilder;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonSpots;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Sorts an inventory, saves it as the layout with the book button, messes the inventory up and
 * turns the ghost view on with the eye button: empty slots must show faded ghosts, wrong ones red.
 */
@SuppressWarnings("UnstableApiUsage")
public class InventoryLayoutGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.inventoryLayoutEnabled = true;
			ModConfigManager.CONFIG.inventoryLayoutShown = false;
			ModConfigManager.CONFIG.inventoryLayout = new ArrayList<>();
			ModConfigManager.CONFIG.inventoryButtonsEnabled = true;
		});

		context.setScreen(() -> ConfigScreenBuilder.build(null));
		context.waitTicks(3);
		context.takeScreenshot("invlayout-config");
		context.setScreen(() -> null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runOnServer(server -> {
				Inventory inv = server.getPlayerList().getPlayers().get(0).getInventory();
				inv.clearContent();
				inv.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
				inv.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
				inv.setItem(2, new ItemStack(Items.DIAMOND_AXE));
				inv.setItem(3, new ItemStack(Items.BOW));
				inv.setItem(7, new ItemStack(Items.COOKED_BEEF, 32));
				inv.setItem(8, new ItemStack(Items.TORCH, 64));
				inv.setItem(9, new ItemStack(Items.ARROW, 64));
				inv.setItem(10, new ItemStack(Items.OAK_LOG, 64));
				inv.setItem(18, new ItemStack(Items.COBBLESTONE, 64));
				inv.setItem(27, new ItemStack(Items.ENDER_PEARL, 16));
				inv.setItem(36, new ItemStack(Items.DIAMOND_BOOTS));
				inv.setItem(39, new ItemStack(Items.DIAMOND_HELMET));
				inv.setItem(40, new ItemStack(Items.SHIELD));
			});
			context.waitTicks(5);
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(3);
			context.takeScreenshot("invlayout-sorted");

			click(context, 1); // book: save
			context.waitTicks(5);
			System.out.println("INVLAYOUT saved: " + context.computeOnClient(client -> ModConfigManager.CONFIG.inventoryLayout));

			// Mess it up: swap sword and bow, drop the logs, the pearls and the shield.
			singleplayer.getServer().runOnServer(server -> {
				Inventory inv = server.getPlayerList().getPlayers().get(0).getInventory();
				inv.setItem(0, new ItemStack(Items.BOW));
				inv.setItem(3, new ItemStack(Items.DIAMOND_SWORD));
				inv.setItem(10, ItemStack.EMPTY);
				inv.setItem(27, ItemStack.EMPTY);
				inv.setItem(40, ItemStack.EMPTY);
				inv.setItem(20, new ItemStack(Items.DIRT, 12));
			});
			context.waitTicks(5);
			click(context, 0); // eye: show
			context.waitTicks(3);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot("invlayout-ghosts");
			System.out.println("INVLAYOUT shown: " + context.computeOnClient(client -> ModConfigManager.CONFIG.inventoryLayoutShown));

			// Saving a different inventory asks for a second click.
			click(context, 1);
			context.waitTicks(3);
			context.takeScreenshot("invlayout-confirm");
			context.setScreen(() -> null);
		}
	}

	/** Clicks the layout button with the given index (0 eye, 1 book). */
	private static void click(ClientGameTestContext context, int index) {
		double[] pos = context.computeOnClient(client -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.screen;
			double scale = client.getWindow().getGuiScale();
			int x = screen.hugosmpenhanced$getLeftPos() + screen.hugosmpenhanced$getImageWidth() + 2
				+ InventoryButtonSpots.STEP * (index + 1) + 10;
			int y = screen.hugosmpenhanced$getTopPos() - InventoryButtonSpots.STEP + 10;
			return new double[] {x * scale, y * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}
}
