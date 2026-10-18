package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a red "⚠ SCAMMER" or green "✔ TRUSTED" line above the nametag of players on a HugoGuard
 * list, drawn the
 * same way as {@link SecondNametagRenderMixin} draws the second nametag (visible through walls
 * unless the player sneaks).
 */
@Mixin(AvatarRenderer.class)
public abstract class HugoGuardNametagMixin {
	private static final float PIXEL = 0.025F;
	private static final float LINE = 9.0F * 1.15F * PIXEL;

	@Inject(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("TAIL")
	)
	private void hugosmpenhanced$submitScammerTag(
		AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 attachment = state.nameTagAttachment;
		if (attachment == null || minecraft.level == null || !ModConfigManager.CONFIG.hugoGuardOverhead) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		HugoGuard.Entry entry = entity instanceof Player player && player != minecraft.player ? HugoGuard.shown(player.getUUID()) : null;
		if (entry == null) {
			return;
		}

		// One line above the name (two if a below-name score pushes the name up).
		float y = (state.showExtraEars ? 10.0F * PIXEL : 0.0F)
			+ (state.scoreText != null ? LINE : 0.0F)
			+ (state.nameTag != null ? LINE : 0.0F);

		poseStack.pushPose();
		poseStack.translate(attachment.x, attachment.y + 0.5 + y, attachment.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(PIXEL, -PIXEL, PIXEL);

		Font font = minecraft.font;
		FormattedCharSequence text = (entry.status() == HugoGuard.Status.SCAMMER
			? Component.literal("⚠ SCAMMER ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
			: Component.literal("✔ TRUSTED ✔").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).getVisualOrderText();
		float x = -font.width(text) / 2.0F;
		int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
		int light = state.lightCoords;
		if (!state.isDiscrete) {
			collector.submitText(poseStack, x, 0, text, false, Font.DisplayMode.NORMAL,
				LightCoordsUtil.lightCoordsWithEmission(light, 2), -1, 0, 0);
			collector.submitText(poseStack, x, 0, text, false, Font.DisplayMode.SEE_THROUGH,
				light, 0x80FFFFFF, background, 0);
		} else {
			collector.submitText(poseStack, x, 0, text, false, Font.DisplayMode.NORMAL,
				light, 0x80FFFFFF, background, 0);
		}
		poseStack.popPose();
	}
}
