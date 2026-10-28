package com.hugosmpenhanced.client.feature.streamer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
//? if v262
import net.minecraft.client.renderer.SubmitNodeCollector;
//? else
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//? end
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/**
 * Renders {@link ItemSpinRenderState}: the real item model (with enchantment glint and resource
 * packs) in its "on the ground" pose, turned by the yaw and scaled so it fills the area.
 */
public class ItemSpinRenderer extends PictureInPictureRenderer<ItemSpinRenderState> {
	private final ItemStackRenderState item = new ItemStackRenderState();

	//? if pre262
	public ItemSpinRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	//? end
	@Override
	public Class<ItemSpinRenderState> getRenderStateClass() {
		return ItemSpinRenderState.class;
	}

	@Override
	//? if v262
	protected void renderToTexture(ItemSpinRenderState state, PoseStack pose, SubmitNodeCollector submitNodeCollector) {
	//? else
	protected void renderToTexture(ItemSpinRenderState state, PoseStack pose) {
	//? end
		Minecraft client = Minecraft.getInstance();
		this.item.clear();
		client.getItemModelResolver().updateForTopItem(this.item, state.stack(), ItemDisplayContext.GROUND, client.level, null, 0);
		if (this.item.isEmpty()) {
			return;
		}
		AABB box = this.item.getModelBoundingBox();
		// The widest the model gets while turning: its diagonal seen from above, or its height.
		double across = Math.sqrt(box.getXsize() * box.getXsize() + box.getZsize() * box.getZsize());
		double extent = Math.max(Math.max(across, box.getYsize()), 0.05);
		float fit = (float) (0.92 / extent);
		pose.scale(fit, -fit, -fit);
		pose.rotate(Axis.XP.rotationDegrees(state.tilt()));
		pose.rotate(Axis.YP.rotationDegrees(state.yaw()));
		pose.translate(-box.getCenter().x, -box.getCenter().y, -box.getCenter().z);
		//? if v262
		client.gameRenderer.lighting().setupFor(this.item.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ENTITY_IN_UI);
		this.item.submit(pose, submitNodeCollector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
		//? else
		client.gameRenderer.getLighting().setupFor(this.item.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ENTITY_IN_UI);
		FeatureRenderDispatcher dispatcher = client.gameRenderer.getFeatureRenderDispatcher();
		this.item.submit(pose, dispatcher.getSubmitNodeStorage(), 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
		dispatcher.renderAllFeatures();
		//? end
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "hugosmpenhanced spinning item";
	}
}
