package com.hugosmpenhanced.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * "Lower fire": the burning overlay sits lower, so the middle of the screen stays visible. The
 * overlay is submitted as one custom geometry quad that inherits the pose it is submitted with, so
 * lowering that pose for its duration moves the whole overlay.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ClientFireOverlayMixin {
	private static final float LOWER = -0.18F;

	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void hugosmpenhanced$lowerFireBegin(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (ModConfigManager.CONFIG.clientLowFire) {
			poseStack.pushPose();
			poseStack.translate(0.0F, LOWER, 0.0F);
		}
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void hugosmpenhanced$lowerFireEnd(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (ModConfigManager.CONFIG.clientLowFire) {
			poseStack.popPose();
		}
	}
}
