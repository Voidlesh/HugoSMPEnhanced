package com.hugosmpenhanced.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.hugosmpenhanced.client.feature.antileak.BetterF3Coordinates;
import com.hugosmpenhanced.client.feature.antileak.DebugCoordinates;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;

/** Anti-Leak: the F3 lines are drawn with the coordinates masked. */
@Mixin(DebugScreenOverlay.class)
public abstract class AntiLeakDebugOverlayMixin {
	@Shadow
	@Final
	private Font font;

	//? if modern
	@Inject(method = "extractLines", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$maskCoordinates(GuiGraphicsExtractor graphics, List<String> lines, boolean left, int width, CallbackInfo ci) {
	//? else
	@Inject(method = "renderLines", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$maskCoordinates(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo ci) {
	//? end
		// BetterF3 draws its own lines here; its values are masked where it sets them
		if (AntiLeak.maskF3() && !BetterF3Coordinates.drawsF3()) {
			DebugCoordinates.renderLines(graphics, this.font, lines, left);
			ci.cancel();
		}
	}
}
