package com.hugosmpenhanced.client.mixin;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.hugosmpenhanced.client.feature.client.ClientTweaks;

import net.minecraft.client.renderer.LightTexture;

/**
 * Fullbright: the lightmap gets full night vision and a neutral sky colour, so everything is lit
 * like at noon (the potion alone tints the night blue). Unlike a gamma above 100 % this keeps
 * colours and contrast, and it works with Sodium and Iris.
 */
@Mixin(LightTexture.class)
public abstract class ClientFullbrightMixin {
	private static final Vector3fc WHITE = new Vector3f(1.0F, 1.0F, 1.0F);

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3))
	private float hugosmpenhanced$fullbright(float nightVision) {
		return ClientTweaks.fullbright() ? 1.0F : nightVision;
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 0))
	private Vector3fc hugosmpenhanced$neutralSky(Vector3fc skyColor) {
		return ClientTweaks.fullbright() ? WHITE : skyColor;
	}
}
