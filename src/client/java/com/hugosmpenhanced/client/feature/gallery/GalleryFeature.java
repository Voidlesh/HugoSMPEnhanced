package com.hugosmpenhanced.client.feature.gallery;

import java.util.List;

import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

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
import net.minecraft.resources.Identifier;

/**
 * Opens the {@link GalleryScreen} from a camera button in the pause menu or a key binding (not
 * bound by default).
 */
public final class GalleryFeature {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("hugosmpenhanced", "icon/screenshots");
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
				addPauseButton(screen, scaledWidth, scaledHeight);
			}
		});
	}

	/** A camera icon right of "Back to Game", or in the bottom-left corner if that is not found. */
	private static void addPauseButton(Screen screen, int scaledWidth, int scaledHeight) {
		List<AbstractWidget> widgets = Screens.getButtons(screen);
		int x = 6;
		int y = scaledHeight - 26;
		for (AbstractWidget widget : widgets) {
			if (widget.getMessage().getContents() instanceof TranslatableContents contents
				&& "menu.returnToGame".equals(contents.getKey())) {
				x = widget.getX() + widget.getWidth() + 4;
				y = widget.getY();
				break;
			}
		}
		Component label = Component.literal("Screenshots");
		SpriteIconButton button = SpriteIconButton.builder(label,
				b -> Minecraft.getInstance().setScreen(new GalleryScreen(screen)), true)
			.sprite(ICON, 16, 16)
			.size(20, 20)
			.build();
		button.setPosition(x, y);
		button.setTooltip(Tooltip.create(Component.literal("Screenshot-Galerie")));
		widgets.add(button);
	}
}
