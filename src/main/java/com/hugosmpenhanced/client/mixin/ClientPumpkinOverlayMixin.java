package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Hides the carved pumpkin's view-blocking overlay; other equipment overlays stay. */
@Mixin(Hud.class)
public abstract class ClientPumpkinOverlayMixin {
	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"))
	private void hugosmpenhanced$noPumpkin(Hud hud, GuiGraphicsExtractor graphics, Identifier texture, float alpha, Operation<Void> original) {
		if (ModConfigManager.CONFIG.clientNoPumpkinBlur && texture.getPath().contains("pumpkinblur")) {
			return;
		}
		original.call(hud, graphics, texture, alpha);
	}
}
