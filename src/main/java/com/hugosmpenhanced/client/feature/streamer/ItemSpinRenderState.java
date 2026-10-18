package com.hugosmpenhanced.client.feature.streamer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;

/**
 * An item turning around its upright axis like an item lying on the ground, drawn in a square
 * area of the GUI. size is the area's edge length in GUI pixels.
 */
public record ItemSpinRenderState(ItemStack stack, float yaw, float tilt, int x0, int y0, int x1, int y1, float scale,
	@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {

	public static ItemSpinRenderState of(ItemStack stack, float yaw, float tilt, int centerX, int centerY, int size,
		@Nullable ScreenRectangle scissor) {
		int half = size / 2;
		int x0 = centerX - half;
		int y0 = centerY - half;
		return new ItemSpinRenderState(stack, yaw, tilt, x0, y0, x0 + size, y0 + size, size,
			scissor, PictureInPictureRenderState.getBounds(x0, y0, x0 + size, y0 + size, scissor));
	}
}
