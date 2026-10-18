package com.hugosmpenhanced.client.hud;

import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** The key for the HUD editor (not bound by default) and the HUD's registration. */
public final class HudKeys {
	public static KeyMapping editorKey;

	private HudKeys() {
	}

	public static void register() {
		HudManager.register();
		editorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hugosmpenhanced.hud_editor",
			InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), TpaKeybinds.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (editorKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.setScreenAndShow(new HudEditorScreen(null));
				}
			}
		});
	}
}
