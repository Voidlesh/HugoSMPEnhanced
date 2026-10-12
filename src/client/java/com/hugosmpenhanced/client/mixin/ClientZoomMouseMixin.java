package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.client.Zoom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/** While zoomed the mouse turns slower by the zoom factor, and the wheel changes the zoom instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class ClientZoomMouseMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double hugosmpenhanced$slowerX(double x) {
		return x * Zoom.mouseScale();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double hugosmpenhanced$slowerY(double y) {
		return y * Zoom.mouseScale();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (window == this.minecraft.getWindow().handle() && this.minecraft.screen == null && this.minecraft.getOverlay() == null
			&& Zoom.scroll(vertical)) {
			ci.cancel();
		}
	}
}
