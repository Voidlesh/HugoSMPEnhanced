package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Live-animates the local player's name wherever it appears in chat, by drawing the animated
 * glyphs on top of each visible line every frame, right after vanilla draws the line underneath.
 * Vanilla's own draw call still runs first and unchanged, so hover/click detection (which is
 * computed against the untouched vanilla text) keeps working normally on the rest of the line.
 *
 * <p>{@link net.minecraft.client.gui.components.ChatComponent} has two line-drawing helpers with
 * this same method signature (for focused vs. unfocused chat) that would otherwise share one
 * mixin class, but each declares its own distinct {@code graphics} field; targeting both from a
 * single mixin makes the {@code @Shadow} field mapping ambiguous at remap time, so each gets its
 * own mixin below instead.
 */
public final class NametagChatMixin {
	private NametagChatMixin() {
	}

	@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
	public abstract static class Background {
		@Shadow
		@Final
		private GuiGraphicsExtractor graphics;

		@Inject(method = "handleMessage", at = @At("TAIL"))
		private void hugosmpenhanced$overlayStyledName(int y, float opacity, FormattedCharSequence sequence, CallbackInfoReturnable<Boolean> cir) {
			if (NametagStyleRenderer.hasAnyEffect()) {
				NametagStyleRenderer.overlayChatLine(this.graphics, Minecraft.getInstance().font, sequence, y, opacity, true);
			}
		}
	}

	@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
	public abstract static class Focused {
		@Shadow
		@Final
		private GuiGraphicsExtractor graphics;

		@Inject(method = "handleMessage", at = @At("TAIL"))
		private void hugosmpenhanced$overlayStyledName(int y, float opacity, FormattedCharSequence sequence, CallbackInfoReturnable<Boolean> cir) {
			if (NametagStyleRenderer.hasAnyEffect()) {
				NametagStyleRenderer.overlayChatLine(this.graphics, Minecraft.getInstance().font, sequence, y, opacity, true);
			}
		}
	}
}
