package com.hugosmpenhanced.client.feature.hugoguard;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A soft red (scammer) or green (trusted) shimmer around players on a HugoGuard list: the player
 * model drawn a little larger as a translucent, full-bright shell. It goes through the normal
 * depth test like any other model, so walls and blocks hide it; it never shows players through
 * walls the way vanilla's glowing outline does.
 */
public class HugoGuardGlowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("hugosmpenhanced", "textures/entity/glow.png");
	private static final float GROW = 1.07F;
	/** Model space has its origin at the neck and y pointing down; the body's middle is 12 pixels lower. */
	private static final float CENTER_Y = 0.75F;
	private static final int SCAMMER = 0xFF3030;
	private static final int TRUSTED = 0x30FF50;

	public HugoGuardGlowLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new HugoGuardGlowLayer(avatar));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ModConfigManager.CONFIG.hugoGuardGlow || state.isInvisible || minecraft.level == null) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		HugoGuard.Entry entry = entity instanceof Player player && player != minecraft.player ? HugoGuard.shown(player.getUUID()) : null;
		if (entry == null) {
			return;
		}

		// Slow pulse between about 20 % and 35 % opacity.
		float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 400.0F);
		int alpha = (int) (50 + 40 * pulse);
		int color = alpha << 24 | (entry.status() == HugoGuard.Status.SCAMMER ? SCAMMER : TRUSTED);

		// Tint, no sprite, outline colour 0: the short overload's int is the outline colour, which
		// would draw vanilla's see-through glowing outline instead.
		poseStack.pushPose();
		poseStack.translate(0.0F, CENTER_Y, 0.0F);
		poseStack.scale(GROW, GROW, GROW);
		poseStack.translate(0.0F, -CENTER_Y, 0.0F);
		collector.submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE),
			LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, 0, null);
		poseStack.popPose();
	}
}
