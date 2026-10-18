package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales the tab list (player list) rendering around its top-center anchor to shrink
 * it uniformly, instead of re-implementing its internal layout math.
 */
@Mixin(Gui.class)
public abstract class CompactTabListMixin {
	@Inject(method = "renderTabList", at = @At("HEAD"))
	private void hugosmpenhanced$beginScale(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!shouldScale()) {
			return;
		}

		float scale = (float) ModConfigManager.CONFIG.tabListScale;
		float centerX = graphics.guiWidth() / 2.0f;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, 0);
		pose.scale(scale, scale);
		pose.translate(-centerX, 0);
	}

	@Inject(method = "renderTabList", at = @At("TAIL"))
	private void hugosmpenhanced$endScale(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (shouldScale()) {
			graphics.pose().popMatrix();
		}
	}

	private static boolean shouldScale() {
		return ModConfigManager.CONFIG.tabListCompactEnabled && ServerCheck.isHugoSmp();
	}
}
