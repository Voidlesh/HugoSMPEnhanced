package com.hugosmpenhanced.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the user's nametag style effects to the in-world nametag floating above their own
 * player model (visible in third-person view). Vanilla submits the nametag as plain text, so when
 * a style is set for the local name, that submission is replaced by the styled one.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NametagWorldTextMixin {
	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$submitStyledNameTag(
		PoseStack poseStack, Vec3 attachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera,
		CallbackInfo ci
	) {
		if (NametagStyleRenderer.submitStyledNameTag((SubmitNodeCollection) (Object) this, poseStack, attachment, offset, name, seeThrough, lightCoords, camera)) {
			ci.cancel();
		}
	}
}
