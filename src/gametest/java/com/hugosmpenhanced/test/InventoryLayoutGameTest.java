package com.hugosmpenhanced.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

import com.hugosmpenhanced.client.config.ConfigScreenBuilder;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonPosition;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonSpots;
import com.hugosmpenhanced.client.feature.inventorylayout.InventoryLayout;
import com.hugosmpenhanced.client.feature.inventorylayout.InventoryLayoutFeature;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Saves two inventory layouts with the book button (the second through its fold-out list), messes
 * the inventory up, previews and picks a layout in the eye's fold-out list, asks to delete one, drags
 * the eye onto another spot and shows the ghosts in a chest, with screenshots of each step.
 */
@SuppressWarnings("UnstableApiUsage")
public class InventoryLayoutGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		context.runOnClient(client -> {
			ModConfig config = ModConfigManager.CONFIG;
			config.inventoryLayoutEnabled = true;
			config.inventoryButtonsEnabled = true;
			config.inventoryButtons.clear();
			InventoryButton home = InventoryButton.builtIn("minecraft:red_bed", InventoryButtonPosition.RIGHT, 0);
			home.name = "Home";
			home.action = "/home";
			config.inventoryButtons.add(home);
			config.inventoryLayoutShowButton = InventoryButton.builtIn("minecraft:ender_eye", InventoryButtonPosition.TOP, InventoryButtonSpots.CORNER_START);
			config.inventoryLayoutSaveButton = InventoryButton.builtIn("minecraft:writable_book", InventoryButtonPosition.TOP, InventoryButtonSpots.CORNER_START + 1);

			// A layout from the first version must become "Layout 1".
			config.inventoryLayouts = new ArrayList<>();
			config.inventoryLayout = new ArrayList<>(Collections.nCopies(41, ""));
			config.inventoryLayout.set(0, "minecraft:diamond_sword");
			config.inventoryLayoutShown = true;
			System.out.println("INVLAYOUT migrated: " + InventoryLayout.layouts().size() + " " + InventoryLayout.layouts().get(0).name
				+ " shown=" + InventoryLayout.shownIndex());
			InventoryLayout.clear();
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
			setInventory(context, singleplayer, inv -> {
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
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(3);
			shot(context, "invlayout-1-buttons");

			clickButton(context, InventoryButtonSpots.CORNER_START + 1); // book, no layouts yet: saves "Layout 1"
			context.waitTicks(3);

			// A second sorting for fighting, saved through the book's list.
			setInventory(context, singleplayer, inv -> {
				inv.setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
				inv.setItem(2, new ItemStack(Items.GOLDEN_APPLE, 8));
				inv.setItem(27, new ItemStack(Items.DIAMOND_PICKAXE));
				inv.setItem(28, new ItemStack(Items.DIAMOND_AXE));
			});
			clickButton(context, InventoryButtonSpots.CORNER_START + 1);
			context.waitTicks(2);
			hoverRow(context, 0, false);
			shot(context, "invlayout-2-book-list");
			clickRow(context, 0, false); // "+ Neues Layout"
			context.waitTicks(3);
			System.out.println("INVLAYOUT saved: " + context.computeOnClient(client -> InventoryLayout.layouts().size()));

			// Mess it up and look at both layouts through the eye's list.
			setInventory(context, singleplayer, inv -> {
				inv.setItem(0, new ItemStack(Items.BOW));
				inv.setItem(3, new ItemStack(Items.DIAMOND_SWORD));
				inv.setItem(10, ItemStack.EMPTY);
				inv.setItem(40, ItemStack.EMPTY);
				inv.setItem(20, new ItemStack(Items.DIRT, 12));
			});
			clickButton(context, InventoryButtonSpots.CORNER_START);
			context.waitTicks(2);
			hoverRow(context, 0, false);
			shot(context, "invlayout-3-eye-preview-layout1");
			hoverRow(context, 1, false);
			shot(context, "invlayout-4-eye-preview-layout2");
			clickRow(context, 0, false); // show "Layout 1"
			context.waitTicks(2);
			context.getInput().setCursorPos(0, 0);
			shot(context, "invlayout-5-ghosts");

			// Deleting asks first; Escape closes the list again.
			clickButton(context, InventoryButtonSpots.CORNER_START + 1);
			context.waitTicks(2);
			clickRow(context, 2, true);
			context.waitTicks(2);
			hoverRow(context, 2, true);
			shot(context, "invlayout-6-delete-confirm");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			System.out.println("INVLAYOUT after escape: screen=" + context.computeOnClient(client -> client.screen.getClass().getSimpleName())
				+ " layouts=" + context.computeOnClient(client -> InventoryLayout.layouts().size()));

			// Drag the eye onto the custom button's spot: the two swap.
			double[] from = spotCenter(context, InventoryButtonPosition.TOP, InventoryButtonSpots.CORNER_START);
			double[] to = spotCenter(context, InventoryButtonPosition.RIGHT, 0);
			context.getInput().setCursorPos(from[0], from[1]);
			context.waitTicks(1);
			context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			context.getInput().setCursorPos((from[0] + to[0]) / 2, (from[1] + to[1]) / 2);
			context.waitTicks(2);
			context.getInput().setCursorPos(to[0], to[1]);
			context.waitTicks(2);
			shot(context, "invlayout-7-dragging");
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(3);
			context.getInput().setCursorPos(0, 0);
			shot(context, "invlayout-8-moved");
			System.out.println("INVLAYOUT placement: " + context.computeOnClient(client -> {
				ModConfig config = ModConfigManager.CONFIG;
				InventoryButton home = config.inventoryButtons.get(0);
				return "eye=" + config.inventoryLayoutShowButton.position + ":" + config.inventoryLayoutShowButton.slot
					+ " home=" + home.position + ":" + home.slot + " shown=" + InventoryLayout.shownIndex();
			}));
			context.setScreen(() -> null);
			context.waitTicks(2);

			// The ghosts also show in the player part of a chest.
			singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).openMenu(
				new SimpleMenuProvider((id, inv, player) -> ChestMenu.threeRows(id, inv), Component.literal("Kiste"))));
			context.waitTicks(5);
			context.getInput().setCursorPos(0, 0);
			shot(context, "invlayout-9-chest");
			context.setScreen(() -> null);
		}
	}

	private static void setInventory(ClientGameTestContext context, TestSingleplayerContext singleplayer, Consumer<Inventory> change) {
		singleplayer.getServer().runOnServer(server -> change.accept(server.getPlayerList().getPlayers().get(0).getInventory()));
		context.waitTicks(5);
	}

	/** Clears the advancement and recipe toasts so they do not cover the buttons. */
	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.getToastManager().clear());
		context.waitTicks(3);
		context.takeScreenshot(name);
	}

	private static void clickButton(ClientGameTestContext context, int topSlot) {
		double[] pos = spotCenter(context, InventoryButtonPosition.TOP, topSlot);
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static void hoverRow(ClientGameTestContext context, int row, boolean delete) {
		double[] pos = rowCenter(context, row, delete);
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
	}

	private static void clickRow(ClientGameTestContext context, int row, boolean delete) {
		hoverRow(context, row, delete);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static double[] rowCenter(ClientGameTestContext context, int row, boolean delete) {
		return context.computeOnClient(client -> {
			int[] gui = InventoryLayoutFeature.openRowCenter(row, delete);
			if (gui == null) {
				throw new AssertionError("Layout list is not open");
			}
			double scale = client.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
	}

	private static double[] spotCenter(ClientGameTestContext context, InventoryButtonPosition side, int slot) {
		return context.computeOnClient(client -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.screen;
			double scale = client.getWindow().getGuiScale();
			int x = screen.hugosmpenhanced$getLeftPos() + InventoryButtonSpots.x(side, slot, screen.hugosmpenhanced$getImageWidth()) + 10;
			int y = screen.hugosmpenhanced$getTopPos() + InventoryButtonSpots.y(side, slot) + 10;
			return new double[] {x * scale, y * scale};
		});
	}
}
