package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.hugosmpenhanced.client.feature.antileak.BetterF3Coordinates;

/** Anti-Leak with BetterF3: coordinate lines get their values masked as they are set. */
@Pseudo
@Mixin(targets = "me.cominixo.betterf3.utils.DebugLine", remap = false)
public abstract class AntiLeakBetterF3LineMixin {
	@Shadow
	@Final
	private String id;

	@ModifyVariable(method = "value(Ljava/lang/Object;)V", at = @At("HEAD"), argsOnly = true, require = 0)
	private Object hugosmpenhanced$mask(Object value) {
		return BetterF3Coordinates.mask(this.id, value);
	}
}
