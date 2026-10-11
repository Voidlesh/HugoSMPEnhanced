package com.hugosmpenhanced.client.settings;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Opens the settings menu with a key (O by default) and from a logo button in the pause menu. */
public final class SettingsFeature {
	public static KeyMapping openKey;

	private SettingsFeature() {
	}

	public static void register() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hugosmpenhanced.open_settings",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_O,
			TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new SettingsScreen(null));
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && ModConfigManager.CONFIG.pauseMenuSettingsButton) {
				addPauseButton(screen, scaledHeight);
			}
		});
	}

	/** Third icon right of "Back to Game", after the camera and the notepad. */
	private static void addPauseButton(Screen screen, int scaledHeight) {
		List<AbstractWidget> widgets = Screens.getButtons(screen);
		int x = 54;
		int y = scaledHeight - 26;
		for (AbstractWidget widget : widgets) {
			if (widget.getMessage().getContents() instanceof TranslatableContents contents
				&& "menu.returnToGame".equals(contents.getKey())) {
				x = widget.getX() + widget.getWidth() + 52;
				y = widget.getY();
				break;
			}
		}
		SpriteIconButton button = SpriteIconButton.builder(Component.literal("HugoSMP Enhanced"),
				b -> Minecraft.getInstance().setScreen(new SettingsScreen(screen)), true)
			.sprite(Theme.sprite("brand"), 16, 16)
			.size(20, 20)
			.build();
		button.setPosition(x, y);
		button.setTooltip(Tooltip.create(Component.literal("HugoSMP Enhanced")));
		widgets.add(button);
	}
}
