package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.hugosmpenhanced.client.feature.hugoguard.HugoGuardChat;

//? if modern
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
//? else
import net.minecraft.client.GuiMessageTag;
//? end
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Marks chat lines written by a HugoGuard-listed scammer (see {@link HugoGuardChat}). */
@Mixin(ChatComponent.class)
public abstract class HugoGuardChatMixin {
	@ModifyVariable(
		//? if modern
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		//? else
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		//? end
		at = @At("HEAD"),
		argsOnly = true
	)
	//? if modern
	private Component hugosmpenhanced$markScammer(Component message, Component original, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
	//? else
	private Component hugosmpenhanced$markScammer(Component message, Component original, MessageSignature signature, GuiMessageTag tag) {
	//? end
		return HugoGuardChat.mark(message);
	}
}
