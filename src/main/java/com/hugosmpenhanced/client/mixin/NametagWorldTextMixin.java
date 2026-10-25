package com.hugosmpenhanced.client.mixin;

//? if modern
import com.mojang.blaze3d.vertex.PoseStack;

//? else
import org.joml.Matrix4f;
//? end
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if modern
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? else
import org.spongepowered.asm.mixin.injection.Redirect;
//? end

import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

//? if modern
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//? else
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
//? end
import net.minecraft.network.chat.Component;
//? if modern
import net.minecraft.world.phys.Vec3;
//? end

/**
 * Applies the user's nametag style effects to the in-world nametag floating above their own
 //? if modern
 * player model (visible in third-person view). Vanilla submits the nametag as plain text, so when
 * a style is set for the local name, that submission is replaced by the styled one.
 //? else
 * player model (visible in third-person view), by intercepting the batched glyph draw call that
 * {@link NameTagFeatureRenderer} issues once per submitted nametag.
 //? end
 */
//? if modern
@Mixin(SubmitNodeCollection.class)
//? else
@Mixin(NameTagFeatureRenderer.class)
//? end
public abstract class NametagWorldTextMixin {
	//? if modern
	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$submitStyledNameTag(
		PoseStack poseStack, Vec3 attachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera,
		CallbackInfo ci
	//? else
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
	//? end
	) {
		//? if modern
		if (NametagStyleRenderer.submitStyledNameTag((SubmitNodeCollection) (Object) this, poseStack, attachment, offset, name, seeThrough, lightCoords, camera)) {
			ci.cancel();
		//? else
		if (NametagStyleRenderer.hasAnyEffect() && NametagStyleRenderer.matchesLocalName(component.getString())) {
			NametagStyleRenderer.renderComponent3D(font, component, x, y, dropShadow, pose, buffer, mode, backgroundColor, lightCoords, color);
		} else {
			font.drawInBatch(component, x, y, color, dropShadow, pose, buffer, mode, backgroundColor, lightCoords);
		//? end
		}
	}
}
