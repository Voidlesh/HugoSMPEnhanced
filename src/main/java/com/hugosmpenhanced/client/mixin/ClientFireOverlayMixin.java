package com.hugosmpenhanced.client.mixin;
//? if modern

import com.mojang.blaze3d.vertex.PoseStack;
//? end

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if modern
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? else
import org.spongepowered.asm.mixin.injection.ModifyArg;
//? end

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.client.renderer.ScreenEffectRenderer;
//? if modern
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? end

//? if modern
/**
 * "Lower fire": the burning overlay sits lower, so the middle of the screen stays visible. The
 * overlay is submitted as one custom geometry quad that inherits the pose it is submitted with, so
 * lowering that pose for its duration moves the whole overlay.
 */
//? else
/** "Lower fire": the burning overlay sits lower, so the middle of the screen stays visible. */
//? end
@Mixin(ScreenEffectRenderer.class)
public abstract class ClientFireOverlayMixin {
	//? if modern
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
	//? else
	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 1)
	private static float hugosmpenhanced$lowerFire(float y) {
		return ModConfigManager.CONFIG.clientLowFire ? y - 0.18F : y;
	//? end
	}
}
