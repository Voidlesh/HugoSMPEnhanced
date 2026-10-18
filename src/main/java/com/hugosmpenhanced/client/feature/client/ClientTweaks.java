package com.hugosmpenhanced.client.feature.client;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.hud.HudManager;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/** General client settings that are not tied to HugoSMP: fullbright, FPS display and overlays. */
public final class ClientTweaks {
	public static KeyMapping fullbrightKey;

	private ClientTweaks() {
	}

	public static void register() {
		fullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hugosmpenhanced.fullbright",
			InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), TpaKeybinds.CATEGORY));
		HudManager.add(FpsHud.INSTANCE);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (fullbrightKey.consumeClick()) {
				ModConfigManager.CONFIG.clientFullbright = !ModConfigManager.CONFIG.clientFullbright;
				ModConfigManager.save();
				if (client.player != null) {
					client.player.displayClientMessage(Component.literal("Fullbright " + (fullbright() ? "an" : "aus")), true);
				}
			}
		});
	}

	public static boolean fullbright() {
		return ModConfigManager.CONFIG.clientFullbright;
	}
}
