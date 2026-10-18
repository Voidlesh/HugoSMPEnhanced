package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.hugosmpenhanced.client.feature.hugoguard.HugoGuardChat;

import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Marks chat lines written by a HugoGuard-listed scammer (see {@link HugoGuardChat}). */
@Mixin(ChatComponent.class)
public abstract class HugoGuardChatMixin {
	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private Component hugosmpenhanced$markScammer(Component message, Component original, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
		return HugoGuardChat.mark(message);
	}
}
