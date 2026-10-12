package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.client.renderer.ScreenEffectRenderer;

/** "Lower fire": the burning overlay sits lower, so the middle of the screen stays visible. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ClientFireOverlayMixin {
	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 1)
	private static float hugosmpenhanced$lowerFire(float y) {
		return ModConfigManager.CONFIG.clientLowFire ? y - 0.18F : y;
	}
}
