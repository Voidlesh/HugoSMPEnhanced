package com.hugosmpenhanced.client.settings;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Opens the settings menu with a key (O by default) and from a logo button in the pause menu.
 *
 * <p>Since 1.21.9 several key bindings can share a key and all of them fire. Iris, for one, opens
 * its shader pack screen on O as well, and whichever mod sets its screen last wins. So the press is
 * taken straight from the keyboard ({@link #onKeyPress}) before any binding sees it, and the first
 * time another binding is passed over that way, a toast names it.
 */
public final class SettingsFeature {
	public static KeyMapping openKey;
	private static boolean conflictShown;

	private SettingsFeature() {
	}

	public static void register() {
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hugosmpenhanced.open_settings",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_O,
			TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.setScreenAndShow(new SettingsScreen(null));
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && ModConfigManager.CONFIG.pauseMenuSettingsButton) {
				addPauseButton(screen, scaledHeight);
			}
		});
	}

	/**
	 * Called from the keyboard before key bindings are updated. Returns true when the press opened
	 * the menu, so no other binding on the same key reacts to it.
	 */
	public static boolean onKeyPress(KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (openKey == null || openKey.isUnbound() || client.gui.screen() != null || client.player == null
			//? if modern
			|| client.gui.overlay() != null || !openKey.matches(event) || client.options.keyDebugModifier.isDown()) {
			//? else
			|| client.getOverlay() != null || !openKey.matches(event) || client.options.keyDebugModifier.isDown()) {
			//? end
			return false;
		}
		client.setScreenAndShow(new SettingsScreen(null));
		KeyMapping other = conflictingMapping();
		if (other != null && !conflictShown) {
			conflictShown = true;
			//? if modern
			SystemToast.add(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			//? else
			client.getToastManager().addToast(SystemToast.multiline(client, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			//? end
				Component.literal("Taste " + openKey.getTranslatedKeyMessage().getString() + " ist doppelt belegt"),
				Component.literal("Auch \"").append(Component.translatable(other.getName()))
					//? if modern
					.append("\". HugoSMP Enhanced hat Vorrang, ändern unter Allgemein > Menü."));
					//? else
					.append("\". HugoSMP Enhanced hat Vorrang, ändern unter Allgemein > Menü.")));
					//? end
		}
		return true;
	}

	/** Another binding on the menu key, F3 combinations left out (they only fire together with F3). */
	public static KeyMapping conflictingMapping() {
		if (openKey == null || openKey.isUnbound()) {
			return null;
		}
		for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
			if (other != openKey && other.same(openKey) && !other.getName().startsWith("key.debug.")) {
				return other;
			}
		}
		return null;
	}

	/** Third icon right of "Back to Game", after the camera and the notepad. */
	private static void addPauseButton(Screen screen, int scaledHeight) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
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
				b -> Minecraft.getInstance().setScreenAndShow(new SettingsScreen(screen)), true)
			.sprite(Theme.sprite("brand"), 16, 16)
			.size(20, 20)
			.build();
		button.setPosition(x, y);
		button.setTooltip(Tooltip.create(Component.literal("HugoSMP Enhanced")));
		widgets.add(button);
	}
}
