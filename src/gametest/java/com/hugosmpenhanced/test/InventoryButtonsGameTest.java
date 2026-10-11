package com.hugosmpenhanced.test;

import java.util.List;

import com.hugosmpenhanced.client.config.ConfigScreenBuilder;
import com.hugosmpenhanced.client.config.InventoryButtonsScreen;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.inventorybuttons.IconButton;
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

		context.setScreen(() -> new InventoryButtonsScreen(null));
		context.waitTicks(3);
		context.takeScreenshot("invbuttons-editor");
		context.clickScreenButton("+ Neu");
		context.waitTicks(2);
		context.takeScreenshot("invbuttons-editor-new");
		context.clickScreenButton("Löschen");
		context.waitTicks(2);
		context.setScreen(() -> null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(3);
			context.takeScreenshot("invbuttons-inventory");

			double[] pos = context.computeOnClient(client -> {
				IconButton first = client.screen.children().stream()
					.filter(IconButton.class::isInstance).map(IconButton.class::cast).findFirst().orElseThrow();
				double scale = client.getWindow().getGuiScale();
				return new double[] {(first.getX() + 10) * scale, (first.getY() + 10) * scale};
			});
			context.getInput().setCursorPos(pos[0], pos[1]);
			context.waitTicks(2);
			context.takeScreenshot("invbuttons-hover");
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			context.setScreen(() -> null);
			context.waitTicks(3);
			context.takeScreenshot("invbuttons-after-click");
		}
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
