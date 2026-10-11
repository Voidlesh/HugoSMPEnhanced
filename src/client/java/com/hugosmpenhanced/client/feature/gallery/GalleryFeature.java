package com.hugosmpenhanced.client.feature.gallery;

import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/**
 * Opens the {@link GalleryScreen} from a button in the pause menu or a key binding (not bound by
 * default).
 */
public final class GalleryFeature {
	public static KeyMapping openKey;

	private GalleryFeature() {
	}

	public static void register() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hugosmpenhanced.open_gallery",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new GalleryScreen(null));
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				Button button = Button.builder(Component.literal("Screenshots"),
						b -> Minecraft.getInstance().setScreen(new GalleryScreen(screen)))
					.bounds(6, scaledHeight - 26, 90, 20).build();
				button.setTooltip(Tooltip.create(Component.literal("Screenshot-Galerie öffnen")));
				Screens.getButtons(screen).add(button);
			}
		});
	}
}
