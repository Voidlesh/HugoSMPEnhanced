package com.hugosmpenhanced.test;

import java.util.List;

import com.hugosmpenhanced.client.config.ConfigScreenBuilder;
import com.hugosmpenhanced.client.config.InventoryButtonsScreen;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.config.SecondNametagScreen;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonSpots;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonPosition;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the config screen and the inventory button editor, then the real inventory with buttons
 * on the right and on top, clicks one of them and saves screenshots of each step.
 */
@SuppressWarnings("UnstableApiUsage")
public class InventoryButtonsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			List<InventoryButton> buttons = ModConfigManager.CONFIG.inventoryButtons;
			buttons.clear();
			buttons.add(button("Hallo", "hallo welt", "minecraft:diamond", InventoryButtonPosition.RIGHT));
			buttons.add(button("Nacht", "/time set night", "minecraft:clock", InventoryButtonPosition.RIGHT));
			buttons.add(button("Spawn", "/say oben", "minecraft:red_bed", InventoryButtonPosition.TOP));
			buttons.add(button("Kiste", "/say kiste", "minecraft:chest", InventoryButtonPosition.TOP));
			ModConfigManager.CONFIG.inventoryButtonsEnabled = true;
		});

		context.setScreen(() -> ConfigScreenBuilder.build(null));
		context.waitTicks(3);
		context.takeScreenshot("invbuttons-config");

		context.setScreen(() -> SecondNametagScreen.build(null));
		context.waitTicks(3);
		context.takeScreenshot("invbuttons-second-nametag-styled");

		context.setScreen(() -> new InventoryButtonsScreen(null));
		context.waitTicks(3);
		context.takeScreenshot("invbuttons-editor");
		context.clickScreenButton("Später ▶");
		context.waitTicks(2);
		context.takeScreenshot("invbuttons-editor-moved");
		context.clickScreenButton("Oben (2)");
		context.waitTicks(2);
		context.takeScreenshot("invbuttons-editor-top");
		context.setScreen(() -> null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(3);
			context.takeScreenshot("invbuttons-inventory");

			// Click the top button of the right column ("Nacht" after the reorder above).
			double[] first = buttonCenter(context, InventoryButtonPosition.RIGHT, 0);
			context.getInput().setCursorPos(first[0], first[1]);
			context.waitTicks(2);
			context.takeScreenshot("invbuttons-hover");
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);

			// Drag the second right button onto the third spot of the top row.
			double[] from = buttonCenter(context, InventoryButtonPosition.RIGHT, 1);
			double[] to = spotCenter(context, InventoryButtonPosition.TOP, 2);
			context.getInput().setCursorPos(from[0], from[1]);
			context.waitTicks(1);
			context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			context.getInput().setCursorPos((from[0] + to[0]) / 2, (from[1] + to[1]) / 2);
			context.waitTicks(2);
			context.getInput().setCursorPos(to[0], to[1]);
			context.waitTicks(2);
			context.takeScreenshot("invbuttons-dragging");
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(3);
			context.takeScreenshot("invbuttons-dropped");
			String placement = context.computeOnClient(client -> {
				StringBuilder out = new StringBuilder();
				for (InventoryButton b : ModConfigManager.CONFIG.inventoryButtons) {
					out.append(b.name).append('=').append(b.position).append(':').append(b.slot).append(' ');
				}
				return out.toString();
			});
			System.out.println("INVBUTTONS placement: " + placement);

			context.setScreen(() -> null);
			context.waitTicks(3);
			context.takeScreenshot("invbuttons-after-click");
		}
	}

	private static double[] buttonCenter(ClientGameTestContext context, InventoryButtonPosition side, int index) {
		return context.computeOnClient(client -> {
			InventoryButton entry = InventoryButtonSpots.onSide(ModConfigManager.CONFIG.inventoryButtons, side).get(index);
			return spot(client, entry.position, entry.slot);
		});
	}

	private static double[] spotCenter(ClientGameTestContext context, InventoryButtonPosition side, int slot) {
		return context.computeOnClient(client -> spot(client, side, slot));
	}

	private static double[] spot(Minecraft client, InventoryButtonPosition side, int slot) {
		AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.screen;
		double scale = client.getWindow().getGuiScale();
		int x = screen.hugosmpenhanced$getLeftPos() + InventoryButtonSpots.x(side, slot, screen.hugosmpenhanced$getImageWidth()) + 10;
		int y = screen.hugosmpenhanced$getTopPos() + InventoryButtonSpots.y(side, slot) + 10;
		return new double[] {x * scale, y * scale};
	}

	private static InventoryButton button(String name, String action, String icon, InventoryButtonPosition position) {
		InventoryButton button = new InventoryButton();
		button.name = name;
		button.action = action;
		button.icon = icon;
		button.position = position;
		return button;
	}
}
