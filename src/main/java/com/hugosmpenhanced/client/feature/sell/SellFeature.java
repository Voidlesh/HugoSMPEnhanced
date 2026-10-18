package com.hugosmpenhanced.client.feature.sell;

import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.hud.HudManager;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Wires the sell assistant into the game: the two keys, the tick, chat for the income and the
 * join / leave events for the autostart. The logic lives in {@link SellEngine}.
 */
public final class SellFeature {
	public static KeyMapping sellKey;
	public static KeyMapping afkKey;

	private SellFeature() {
	}

	public static void register() {
		sellKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hugosmpenhanced.sell_now",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, TpaKeybinds.CATEGORY));
		afkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hugosmpenhanced.sell_afk",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (sellKey.consumeClick()) {
				if (client.screen == null) {
					if (shiftDown() && afkKey.isUnbound()) {
						SellEngine.toggleAfk();
					} else {
						SellEngine.pressSell();
					}
				}
			}
			while (afkKey.consumeClick()) {
				if (client.screen == null) {
					SellEngine.toggleAfk();
				}
			}
			SellEngine.tick(client);
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> SellEngine.onGameMessage(message));
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> SellEngine.onGameMessage(message));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SellEngine.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SellEngine::onDisconnect));

		SellOverlay.register();
		HudManager.add(SellHud.INSTANCE);
	}

	private static boolean shiftDown() {
		var window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
	}
}
