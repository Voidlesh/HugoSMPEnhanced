package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.hugosmpenhanced.client.feature.streamermode.NameMask;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Streamer mode: every piece of text is drawn and measured through {@link Font}, so swapping the
 * names here covers chat, tab list, nametags, scoreboard, titles, signs and menus at once. Measuring
 * the same masked text that is drawn keeps centring, backgrounds and line wrapping right.
 */
@Mixin(Font.class)
public abstract class StreamerModeFontMixin {
	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At("HEAD"), argsOnly = true)
	private String hugosmpenhanced$maskPrepareString(String text) {
		return NameMask.mask(text);
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
		at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence hugosmpenhanced$maskPrepareSequence(FormattedCharSequence text) {
		return NameMask.mask(text);
	}

	//? if v262
	@ModifyVariable(method = "prepare8xTextOutline", at = @At("HEAD"), argsOnly = true)
	//? else
	@ModifyVariable(method = "drawInBatch8xOutline", at = @At("HEAD"), argsOnly = true)
	//? end
	private FormattedCharSequence hugosmpenhanced$maskOutline(FormattedCharSequence text) {
		return NameMask.mask(text);
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String hugosmpenhanced$maskWidthString(String text) {
		return NameMask.mask(text);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText hugosmpenhanced$maskWidthText(FormattedText text) {
		return NameMask.mask(text);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence hugosmpenhanced$maskWidthSequence(FormattedCharSequence text) {
		return NameMask.mask(text);
	}

	@ModifyVariable(method = {"split", "splitIgnoringLanguage", "wordWrapHeight", "substrByWidth"}, at = @At("HEAD"), argsOnly = true)
	private FormattedText hugosmpenhanced$maskWrapped(FormattedText text) {
		return NameMask.mask(text);
	}

	@ModifyVariable(method = "plainSubstrByWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), argsOnly = true)
	private String hugosmpenhanced$maskSubstring(String text) {
		return NameMask.mask(text);
	}
}
