package com.hugosmpenhanced.client.feature.notes;

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
 * Opens the {@link NotesScreen} from a notepad icon in the pause menu (next to the screenshot
 * camera) or a key binding (not bound by default), and shows pinned notes on the HUD.
 */
public final class NotesFeature {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("hugosmpenhanced", "icon/notes");
	public static KeyMapping openKey;

	private NotesFeature() {
	}

	public static void register() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hugosmpenhanced.open_notes",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new NotesScreen(null));
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				addPauseButton(screen, scaledHeight);
			}
		});
		NotesHud.register();
	}

	/** Second icon right of "Back to Game", after the screenshot camera. */
	private static void addPauseButton(Screen screen, int scaledHeight) {
		List<AbstractWidget> widgets = Screens.getButtons(screen);
		int x = 30;
		int y = scaledHeight - 26;
		for (AbstractWidget widget : widgets) {
			if (widget.getMessage().getContents() instanceof TranslatableContents contents
				&& "menu.returnToGame".equals(contents.getKey())) {
				x = widget.getX() + widget.getWidth() + 28;
				y = widget.getY();
				break;
			}
		}
		SpriteIconButton button = SpriteIconButton.builder(Component.literal("Notizblock"),
				b -> Minecraft.getInstance().setScreen(new NotesScreen(screen)), true)
			.sprite(ICON, 16, 16)
			.size(20, 20)
			.build();
		button.setPosition(x, y);
		button.setTooltip(Tooltip.create(Component.literal("Notizblock")));
		widgets.add(button);
	}
}
