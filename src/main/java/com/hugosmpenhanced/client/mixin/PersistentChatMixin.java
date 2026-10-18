package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * HugoSMP switches players between its worlds via an internal reconnect, which vanilla
 * treats like any other disconnect and wipes the chat log ({@link Gui#onDisconnected()}).
 * This keeps the chat history across that reconnect instead.
 */
@Mixin(Gui.class)
public abstract class PersistentChatMixin {
	@Redirect(method = "onDisconnected", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private void hugosmpenhanced$keepChatOnDisconnect(ChatComponent chat, boolean clearHistory) {
		if (!ModConfigManager.CONFIG.persistentChatEnabled || !ServerCheck.wasHugoSmp()) {
			chat.clearMessages(clearHistory);
		}
	}
}
