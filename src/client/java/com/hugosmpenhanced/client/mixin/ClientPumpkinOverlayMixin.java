package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Hides the carved pumpkin's view-blocking overlay; other equipment overlays stay. */
@Mixin(Gui.class)
public abstract class ClientPumpkinOverlayMixin {
	@WrapOperation(method = "renderCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V",
		ordinal = 0))
	private void hugosmpenhanced$noPumpkin(Gui gui, GuiGraphics graphics, Identifier texture, float alpha, Operation<Void> original) {
		if (ModConfigManager.CONFIG.clientNoPumpkinBlur && texture.getPath().contains("pumpkinblur")) {
			return;
		}
		original.call(gui, graphics, texture, alpha);
	}
}
