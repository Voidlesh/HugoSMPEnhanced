package com.hugosmpenhanced.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;

/**
 * Applies the user's nametag style effects to the in-world nametag floating above their own
 * player model (visible in third-person view), by intercepting the batched glyph draw call that
 * {@link NameTagFeatureRenderer} issues once per submitted nametag.
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NametagWorldTextMixin {
	@Redirect(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
		)
	)
	private void hugosmpenhanced$drawStyledNameTag(
		Font font, Component component, float x, float y, int color, boolean dropShadow,
		Matrix4f pose, MultiBufferSource buffer, Font.DisplayMode mode, int backgroundColor, int lightCoords
	) {
		if (NametagStyleRenderer.hasAnyEffect() && NametagStyleRenderer.matchesLocalName(component.getString())) {
			NametagStyleRenderer.renderComponent3D(font, component, x, y, dropShadow, pose, buffer, mode, backgroundColor, lightCoords, color);
		} else {
			font.drawInBatch(component, x, y, color, dropShadow, pose, buffer, mode, backgroundColor, lightCoords);
		}
	}
}
