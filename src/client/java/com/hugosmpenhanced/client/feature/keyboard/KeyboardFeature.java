package com.hugosmpenhanced.client.feature.keyboard;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Adds a button to the vanilla key binds screen that opens the {@link KeyboardScreen}. */
public final class KeyboardFeature {
	private KeyboardFeature() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof KeyBindsScreen) {
				Button button = Button.builder(Component.literal("Tastatur-Ansicht"),
						b -> Minecraft.getInstance().setScreen(new KeyboardScreen(screen)))
					.bounds(scaledWidth - 106, 6, 100, 20).build();
				button.setTooltip(Tooltip.create(Component.literal("Alle Belegungen auf einer Tastatur ansehen und ändern")));
				Screens.getButtons(screen).add(button);
			}
		});
	}
}
