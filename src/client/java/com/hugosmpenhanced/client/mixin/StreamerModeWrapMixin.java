package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.hugosmpenhanced.client.feature.streamermode.NameMask;

import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;

/**
 * Streamer mode: chat lines are wrapped once when a message arrives and kept. Wrapping the masked
 * text makes the stored lines fit the stand-ins, and click/hover lookups in chat run on the same
 * lines that are drawn.
 */
@Mixin(ComponentRenderUtils.class)
public abstract class StreamerModeWrapMixin {
	@ModifyVariable(method = "wrapComponents", at = @At("HEAD"), argsOnly = true)
	private static FormattedText hugosmpenhanced$maskWrapped(FormattedText text) {
		return NameMask.mask(text);
	}
}
