package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.gui.HugoStyle;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps button, text field and slider sprites for {@link HugoStyle} on the mod's screens. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsStyleMixin {
	@Inject(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
		at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$styleSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height,
			int color, CallbackInfo ci) {
		if (HugoStyle.replaceSprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height)) {
			ci.cancel();
		}
	}

	@Inject(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIF)V",
		at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$styleSpriteAlpha(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height,
			float alpha, CallbackInfo ci) {
		if (HugoStyle.replaceSprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height)) {
			ci.cancel();
		}
	}
}
