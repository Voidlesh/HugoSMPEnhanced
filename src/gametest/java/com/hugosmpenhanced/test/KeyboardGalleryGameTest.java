package com.hugosmpenhanced.test;

import com.hugosmpenhanced.client.feature.gallery.GalleryScreen;
import com.hugosmpenhanced.client.feature.keyboard.KeyboardScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/**
 * Opens the key binds screen with its keyboard button, the keyboard view (selecting a key and
 * putting an action on it), then the screenshot gallery from the pause menu, and screenshots each.
 */
@SuppressWarnings("UnstableApiUsage")
public class KeyboardGalleryGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new KeyBindsScreen(null, Minecraft.getInstance().options));
		context.waitTicks(3);
		context.takeScreenshot("keyboard-keybinds-screen");
		context.clickScreenButton("Tastatur-Ansicht");
		context.waitTicks(3);
		context.takeScreenshot("keyboard-view");
		context.runOnClient(client -> {
			java.util.Map<String, java.util.List<String>> byKey = new java.util.TreeMap<>();
			for (var m : client.options.keyMappings) {
				byKey.computeIfAbsent(m.saveString(), k -> new java.util.ArrayList<>()).add(m.getName());
			}
			byKey.forEach((k, v) -> { if (v.size() > 1) System.out.println("KEYBOARD shared " + k + " " + v); });
		});

		// Select the G key and put the first action matching the search on it.
		context.runOnClient(client -> ((KeyboardScreen) client.gui.screen()).select(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G)));
		context.waitTicks(2);
		context.takeScreenshot("keyboard-selected");
		context.runOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.EditBox box) {
					client.gui.screen().setFocused(box);
				}
			}
		});
		context.getInput().typeChars("inventory");
		context.waitTicks(2);
		context.takeScreenshot("keyboard-search");
		String before = context.computeOnClient(client -> client.options.keyInventory.saveString());
		context.runOnClient(client -> {
			// Simulate a click on the first action row.
			for (var child : client.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.Button button && button.visible
					&& button.getMessage().getString().contains("[")) {
					button.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
					break;
				}
			}
		});
		context.waitTicks(2);
		String after = context.computeOnClient(client -> client.options.keyInventory.saveString());
		System.out.println("KEYBOARD assign: inventory " + before + " -> " + after);
		context.takeScreenshot("keyboard-assigned");
		context.runOnClient(client -> {
			client.options.keyInventory.setKey(client.options.keyInventory.getDefaultKey());
			net.minecraft.client.KeyMapping.resetMapping();
		});
		context.setScreen(() -> null);

		// No menu blur and a flat world, so the world loads in time under software rendering.
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(3);
			context.takeScreenshot("gallery-pause-menu");
			context.clickScreenButton("Screenshots");
			context.waitTicks(20);
			context.takeScreenshot("gallery-grid");
			context.runOnClient(client -> client.gui.screen().mouseClicked(
				new net.minecraft.client.input.MouseButtonEvent(30, 30, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false));
			context.waitTicks(20);
			context.takeScreenshot("gallery-viewer");
			context.clickScreenButton("Kopieren");
			context.waitTicks(20);
			context.takeScreenshot("gallery-copy");
			context.setScreen(() -> new GalleryScreen(null));
			context.waitTicks(10);
			context.setScreen(() -> null);
		}
	}
}
