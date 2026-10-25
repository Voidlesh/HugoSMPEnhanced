package com.hugosmpenhanced.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.overhead.OverheadLines;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if modern
import net.minecraft.util.LightCoordsUtil;
//? else
import net.minecraft.client.renderer.LightTexture;
//? end
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//? if modern
import net.minecraft.client.renderer.state.level.CameraRenderState;
//? else
import net.minecraft.client.renderer.state.CameraRenderState;
//? end
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the {@link OverheadLines} above a player's nametag, one under the other, the same way as
 * {@link SecondNametagRenderMixin} draws the second nametag (visible through walls unless the player
 * sneaks). The size comes from the overhead scale setting.
 */
@Mixin(AvatarRenderer.class)
public abstract class OverheadTagMixin {
	private static final float PIXEL = 0.025F;
	/** Height of one line of text, in font pixels, with a little space between lines. */
	private static final float LINE = 9.0F * 1.15F;

	@Inject(
		//? if modern
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer.state.level.CameraRenderState;)V",
		//? else
		method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		//? end
		at = @At("TAIL")
	)
	private void hugosmpenhanced$submitOverheadTags(
		AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 attachment = state.nameTagAttachment;
		if (attachment == null || minecraft.level == null) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		if (!(entity instanceof Player player) || player == minecraft.player) {
			return;
		}
		List<Component> lines = OverheadLines.of(player);
		if (lines.isEmpty()) {
			return;
		}

		float scale = (float) Math.clamp(ModConfigManager.CONFIG.overheadScale, 0.5, 2.0);
		float pixel = PIXEL * scale;
		float step = LINE * pixel;
		// The space the name and a below-name score take up is vanilla's, so it does not scale;
		// only the lines themselves and the gap between them do.
		float bottom = (state.showExtraEars ? 10.0F * PIXEL : 0.0F)
			+ (state.scoreText != null ? LINE * PIXEL : 0.0F)
			+ (state.nameTag != null ? LINE * PIXEL : 0.0F);
		for (int i = 0; i < lines.size(); i++) {
			drawLine(state, poseStack, collector, camera, attachment, lines.get(i), bottom + i * step, pixel);
		}
	}

	private static void drawLine(
		AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		Vec3 attachment, Component component, float y, float pixel
	) {
		Minecraft minecraft = Minecraft.getInstance();
		poseStack.pushPose();
		poseStack.translate(attachment.x, attachment.y + 0.5 + y, attachment.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(pixel, -pixel, pixel);

		Font font = minecraft.font;
		FormattedCharSequence text = component.getVisualOrderText();
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
