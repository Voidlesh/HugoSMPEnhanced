package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.feature.messages.SideChat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Keeps a line out of the main chat when the second chat shows it and the player asked for that. */
@Mixin(ChatComponent.class)
public abstract class SideChatHideMixin {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$hideFromMain(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		if (SideChat.hideFromMain(message.getString())) {
			ci.cancel();
		}
	}
}
