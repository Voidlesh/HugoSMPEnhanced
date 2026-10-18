package com.hugosmpenhanced.client.feature.nametag;

import org.joml.Matrix4fc;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * A glyph (or text effect such as an underline) that renders through a {@link GlyphFxVertexConsumer}.
 * It is immutable, which matters in the GUI: glyphs are only turned into vertices at the end of the
 * frame, long after the code that submitted them has returned.
 */
record FxTextRenderable(TextRenderable inner, GlyphFx fx) implements TextRenderable {
	@Override
	public void render(Matrix4fc pose, VertexConsumer consumer, int light, boolean flat) {
		this.inner.render(pose, new GlyphFxVertexConsumer(consumer, this.fx), light, flat);
	}

	@Override
	public RenderType renderType(Font.DisplayMode mode) {
		return this.inner.renderType(mode);
	}

	@Override
	public GpuTextureView textureView() {
		return this.inner.textureView();
	}

	@Override
	public RenderPipeline guiPipeline() {
		return this.inner.guiPipeline();
	}

	@Override
	public float left() {
		return this.inner.left();
	}

	@Override
	public float top() {
		return this.inner.top();
	}

	@Override
	public float right() {
		return this.inner.right();
	}

	@Override
	public float bottom() {
		return this.inner.bottom();
	}
}
