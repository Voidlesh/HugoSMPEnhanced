package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametag;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametagPosition;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Submits the second nametag line next to the local player's own nametag. It mirrors what
 * {@code NameTagFeatureRenderer.Storage#add} does for a vanilla nametag (billboard towards the
 * camera, a solid pass plus a see-through pass unless sneaking), but with its own scale, which the
 * vanilla nametag path has no hook for.
 */
@Mixin(AvatarRenderer.class)
public abstract class SecondNametagRenderMixin {
	/** World units per font pixel on a vanilla nametag. */
	private static final float PIXEL = 0.025F;
	/** Space between two stacked nametag lines, matching vanilla's 1.15 line pitch. */
	private static final float LINE_GAP = 9.0F * 1.15F * PIXEL - 10.0F * PIXEL;
	/** A nametag's background reaches one pixel above the glyph top and to the glyph bottom (9px). */
	private static final float BG_TOP = 1.0F * PIXEL;
	private static final float BG_BOTTOM = 9.0F * PIXEL;

	@Inject(
		method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("TAIL")
	)
	private void hugosmpenhanced$submitSecondNametag(
		AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 attachment = state.nameTagAttachment;
		if (state.nameTag == null || attachment == null || minecraft.player == null
			|| state.id != minecraft.player.getId() || !SecondNametag.isActive()) {
			return;
		}

		ModConfig config = ModConfigManager.CONFIG;
		float scale = (float) config.secondNametagScale;
		Font font = minecraft.font;
		FormattedCharSequence text = SecondNametag.text().getVisualOrderText();

		// Vertical centre-line of the vanilla lines, relative to the nametag pivot. The name is
		// pushed up by one line when a below-name score is shown; extra ears push everything up.
		float earsOffset = state.showExtraEars ? 10.0F * PIXEL : 0.0F;
		float lowestLineY = earsOffset;
		float nameY = lowestLineY + (state.scoreText != null ? 9.0F * 1.15F * PIXEL : 0.0F);

		float pivotY;
		if (config.secondNametagPosition == SecondNametagPosition.BELOW) {
			pivotY = lowestLineY - BG_BOTTOM - LINE_GAP - scale * BG_TOP;
		} else {
			pivotY = nameY + BG_TOP + LINE_GAP + scale * BG_BOTTOM;
		}

		poseStack.pushPose();
		poseStack.translate(attachment.x, attachment.y + 0.5 + pivotY, attachment.z);
		poseStack.mulPose(camera.orientation);
		poseStack.scale(PIXEL * scale, -PIXEL * scale, PIXEL * scale);

		float x = -font.width(text) / 2.0F;
		int background = config.secondNametagBackground
			? (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24
			: 0;
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
