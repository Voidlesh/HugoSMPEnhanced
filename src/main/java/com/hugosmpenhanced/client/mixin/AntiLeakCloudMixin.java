package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;

/** Anti-Leak: the cloud pattern is tied to X and Z, so it is shifted by a secret amount per game start. */
@Mixin(CloudRenderer.class)
public abstract class AntiLeakCloudMixin {
	@ModifyVariable(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), argsOnly = true)
	private Vec3 hugosmpenhanced$shift(Vec3 camera) {
		double x = AntiLeak.cloudShiftX();
		double z = AntiLeak.cloudShiftZ();
		return x == 0 && z == 0 ? camera : camera.add(x, 0, z);
	}
}
