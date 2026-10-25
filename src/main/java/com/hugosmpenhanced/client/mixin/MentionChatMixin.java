package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.keywords.KeywordAlarm;
import com.hugosmpenhanced.client.feature.mentions.MentionHighlightFeature;

//? if modern
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
//? else
import net.minecraft.client.GuiMessageTag;
//? end
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Marks the chat line that mentions you or has one of your keywords ({@link MentionHighlightFeature}, {@link KeywordAlarm}). */
@Mixin(ChatComponent.class)
public abstract class MentionChatMixin {
	private static final String ADD_MESSAGE =
		//? if modern
		"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
		//? else
		"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";
		//? end

	@ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
	//? if modern
	private GuiMessageTag hugosmpenhanced$mentionTag(GuiMessageTag tag, Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag original) {
	//? else
	private GuiMessageTag hugosmpenhanced$mentionTag(GuiMessageTag tag, Component message, MessageSignature signature, GuiMessageTag original) {
	//? end
		return KeywordAlarm.tag(message, MentionHighlightFeature.tag(message, tag));
	}

	@ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
	private Component hugosmpenhanced$mentionColor(Component message) {
		return KeywordAlarm.marked(MentionHighlightFeature.colored(message));
	}

	@Inject(method = ADD_MESSAGE, at = @At("TAIL"))
	//? if modern
	private void hugosmpenhanced$mentionHint(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
	//? else
	private void hugosmpenhanced$mentionHint(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
	//? end
		MentionHighlightFeature.afterAdded(message);
		KeywordAlarm.afterAdded(message);
	}
}
