package com.hugosmpenhanced.client.feature.tpa;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class TpaKeybinds {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hugosmpenhanced", "tpa"));

	public static KeyMapping acceptKey;

	private TpaKeybinds() {
	}

	public static void register() {
		acceptKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.hugosmpenhanced.accept_tpa",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			TpaState.expireIfNeeded();
			while (acceptKey.consumeClick()) {
				String player = TpaState.pendingPlayer();
				if (player != null && client.player != null) {
					client.player.connection.sendCommand("tpaccept " + player);
					TpaState.clear();
				}
			}
		});
	}
}
