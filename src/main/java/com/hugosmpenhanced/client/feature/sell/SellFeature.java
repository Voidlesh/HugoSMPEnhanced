package com.hugosmpenhanced.client.feature.sell;

//? if modern
import com.hugosmpenhanced.client.util.KeyCodes;
//? end
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.hud.HudManager;
import com.mojang.blaze3d.platform.InputConstants;
//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
		sellKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hugosmpenhanced.sell_now",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_G, TpaKeybinds.CATEGORY));
		afkKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hugosmpenhanced.sell_afk",
			//? if modern
			InputConstants.Type.KEYBOARD, KeyCodes.UNKNOWN, TpaKeybinds.CATEGORY));
			//? else
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, TpaKeybinds.CATEGORY));
			//? end

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (sellKey.consumeClick()) {
				if (client.gui.screen() == null) {
					if (shiftDown() && afkKey.isUnbound()) {
						SellEngine.toggleAfk();
					} else {
						SellEngine.pressSell();
					}
				}
			}
			while (afkKey.consumeClick()) {
				if (client.gui.screen() == null) {
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
		//? if modern
		return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
		//? else
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
		//? end
	}
}
