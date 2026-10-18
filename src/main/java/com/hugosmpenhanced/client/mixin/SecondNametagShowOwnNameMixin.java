package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.feature.secondnametag.SecondNametag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Vanilla never draws the camera entity's own nametag. While the second nametag is active, the
 * local player's nametag is shown in third-person view so the second line has something to sit
 * next to.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SecondNametagShowOwnNameMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
	private void hugosmpenhanced$showOwnName(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || !SecondNametag.isActive()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.player
			&& !minecraft.options.getCameraType().isFirstPerson()
			&& !minecraft.gui.hud.isHidden()
			&& !entity.isInvisibleTo(minecraft.player)
			&& !entity.isVehicle()) {
			cir.setReturnValue(true);
		}
	}
}
