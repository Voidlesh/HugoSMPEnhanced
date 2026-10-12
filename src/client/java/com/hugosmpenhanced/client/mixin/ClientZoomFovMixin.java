package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.feature.client.Zoom;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

/** Zoom narrows the world's field of view; the hand (useFovSetting false) stays as it is. */
@Mixin(GameRenderer.class)
public abstract class ClientZoomFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void hugosmpenhanced$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (useFovSetting) {
			double factor = Zoom.factor();
			if (factor != 1.0) {
				cir.setReturnValue((float) (cir.getReturnValueF() / factor));
			}
		}
	}
}
