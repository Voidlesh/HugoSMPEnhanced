package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.keywords.KeywordAlarm;
import com.hugosmpenhanced.client.feature.mentions.MentionHighlightFeature;

import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Marks the chat line that mentions you or has one of your keywords ({@link MentionHighlightFeature}, {@link KeywordAlarm}). */
@Mixin(ChatComponent.class)
public abstract class MentionChatMixin {
	private static final String ADD_MESSAGE =
		"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";

	@ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
	private GuiMessageTag hugosmpenhanced$mentionTag(GuiMessageTag tag, Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag original) {
		return KeywordAlarm.tag(message, MentionHighlightFeature.tag(message, tag));
	}

	@ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true)
	private Component hugosmpenhanced$mentionColor(Component message) {
		return KeywordAlarm.marked(MentionHighlightFeature.colored(message));
	}

	@Inject(method = ADD_MESSAGE, at = @At("TAIL"))
	private void hugosmpenhanced$mentionHint(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		MentionHighlightFeature.afterAdded(message);
		KeywordAlarm.afterAdded(message);
	}
}
