package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.fakeclan.FakeClan;
import com.hugosmpenhanced.client.feature.fakeclan.FakeClanRules;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a "⚠ FAKE-CLAN [Tag]" line above the nametag of a player whose clan tag imitates a real clan
 * or is on the manual fake list. It sits above the HugoGuard line when that is shown too, so the
 * two never draw over each other. Drawn like the HugoGuard line.
 */
@Mixin(AvatarRenderer.class)
public abstract class FakeClanNametagMixin {
	private static final float PIXEL = 0.025F;
	private static final float LINE = 9.0F * 1.15F * PIXEL;

	@Inject(
		method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("TAIL")
	)
	private void hugosmpenhanced$submitFakeClanTag(
		AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 attachment = state.nameTagAttachment;
		if (attachment == null || minecraft.level == null) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		FakeClanRules.Match match = entity instanceof Player player ? FakeClan.of(player) : null;
		if (match == null) {
			return;
		}

		// Same base as the HugoGuard line, plus one more line if HugoGuard draws its own line above.
		boolean hugoGuardLine = ModConfigManager.CONFIG.hugoGuardOverhead
			&& entity instanceof Player player && HugoGuard.shown(player.getUUID()) != null;
		float y = (state.showExtraEars ? 10.0F * PIXEL : 0.0F)
			+ (state.scoreText != null ? LINE : 0.0F)
			+ (state.nameTag != null ? LINE : 0.0F)
			+ (hugoGuardLine ? LINE : 0.0F);

		poseStack.pushPose();
		poseStack.translate(attachment.x, attachment.y + 0.5 + y, attachment.z);
		poseStack.mulPose(camera.orientation);
		poseStack.scale(PIXEL, -PIXEL, PIXEL);

		Font font = minecraft.font;
		FormattedCharSequence text = Component.literal("⚠ FAKE-CLAN [" + match.clan() + "] ⚠")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).getVisualOrderText();
		float x = -font.width(text) / 2.0F;
		int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
		int light = state.lightCoords;
		if (!state.isDiscrete) {
			collector.submitText(poseStack, x, 0, text, false, Font.DisplayMode.NORMAL,
				LightTexture.lightCoordsWithEmission(light, 2), -1, 0, 0);
			collector.submitText(poseStack, x, 0, text, false, Font.DisplayMode.SEE_THROUGH,
				light, 0x80FFFFFF, background, 0);
		} else {
			collector.submitText(poseStack, x, 0, text, false, Font.DisplayMode.NORMAL,
				light, 0x80FFFFFF, background, 0);
		}
		poseStack.popPose();
	}
}
