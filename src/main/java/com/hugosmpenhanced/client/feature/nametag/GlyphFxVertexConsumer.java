package com.hugosmpenhanced.client.feature.nametag;

import org.joml.Matrix4fc;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Sits between a glyph and the real vertex buffer and reshapes every glyph quad before it is
 * written.
 *
 * <p>A glyph arrives as one textured quad with a single colour. When the {@link GlyphFx} carries a
 * colour field or a row shift, the quad is cut into a grid of cells one font pixel in size, with
 * position and texture coordinates interpolated across it. Because font textures are sampled with
 * nearest filtering, the cut is invisible on its own: every cell shows exactly the texels it showed
 * before. What it buys is a vertex at every texel corner, so the colour field is sampled at font
 * pixel resolution and blended smoothly in between instead of being flat across the whole letter,
 * and so a Glitch can tear individual pixel rows sideways.
 *
 * <p>The positions come in <em>local</em> text coordinates, before the pose matrix: glyphs call
 * {@link #addVertex(Matrix4fc, float, float, float)}, which this class overrides to capture the
 * coordinates instead of transforming them right away. That is what lets the colour field and the
 * per-letter transform work in the text's own space, under whatever word transform, GUI matrix or
 * world billboard the pose carries.
 *
 * <p>Glyph quads are emitted as four vertices, each finished by its light value, so a quad is
 * reshaped as soon as its fourth vertex's light arrives. Vertices added already transformed (not a
 * glyph code path, but allowed by the interface) are passed through untouched.
 */
final class GlyphFxVertexConsumer implements VertexConsumer {
	private static final int MAX_COLUMNS = 24;
	private static final int MAX_ROWS = 16;

	private final VertexConsumer out;
	private final GlyphFx fx;

	private Matrix4fc pose;
	private final float[] qx = new float[4];
	private final float[] qy = new float[4];
	private final float[] qz = new float[4];
	private final float[] qu = new float[4];
	private final float[] qv = new float[4];
	private final int[] qColor = new int[4];
	private final int[] qLight = new int[4];
	private int count;
	private boolean passthrough;

	// Grid node scratch, reused across quads.
	private final float[] nx = new float[(MAX_COLUMNS + 1) * (MAX_ROWS + 1)];
	private final float[] ny = new float[nx.length];
	private final float[] nz = new float[nx.length];
	private final float[] nu = new float[nx.length];
	private final float[] nv = new float[nx.length];
	private final int[] nColor = new int[nx.length];

	GlyphFxVertexConsumer(VertexConsumer out, GlyphFx fx) {
		this.out = out;
		this.fx = fx;
	}

	@Override
	public VertexConsumer addVertex(Matrix4fc matrix, float x, float y, float z) {
		if (this.count == 4) {
			this.count = 0;
		}
		this.passthrough = false;
		this.pose = matrix;
		int i = this.count++;
		this.qx[i] = x;
		this.qy[i] = y;
		this.qz[i] = z;
		this.qColor[i] = 0xFFFFFFFF;
		this.qLight[i] = 0;
		return this;
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		this.passthrough = true;
		this.out.addVertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		return this.setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
	}

	@Override
	public VertexConsumer setColor(int argb) {
		if (this.passthrough) {
			this.out.setColor(argb);
		} else if (this.count > 0) {
			this.qColor[this.count - 1] = argb;
		}
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		if (this.passthrough) {
			this.out.setUv(u, v);
		} else if (this.count > 0) {
			this.qu[this.count - 1] = u;
			this.qv[this.count - 1] = v;
		}
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		if (this.passthrough) {
			this.out.setUv1(u, v);
		}
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		if (this.passthrough) {
			this.out.setUv2(u, v);
		} else if (this.count > 0) {
			this.qLight[this.count - 1] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
			if (this.count == 4) {
				this.emitQuad();
			}
		}
		return this;
	}

	@Override
	public VertexConsumer setUv3(float u, float v) {
		if (this.passthrough) {
			this.out.setUv3(u, v);
		}
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		if (this.passthrough) {
			this.out.setNormal(x, y, z);
		}
		return this;
	}

	@Override
	public VertexConsumer setLineWidth(float width) {
		if (this.passthrough) {
			this.out.setLineWidth(width);
		}
		return this;
	}

	// ---------------------------------------------------------------- reshaping ----

	private void emitQuad() {
		int columns = 1;
		int rows = 1;
		if (this.fx.subdivides()) {
			// Corner order is top-left, bottom-left, bottom-right, top-right for glyphs; effect
			// quads (underline, background) wind the other way round, which only swaps the two grid
			// axes, so the interpolation below covers them just the same.
			columns = cells(distance(0, 3));
			rows = cells(distance(0, 1));
			columns = Math.min(columns, MAX_COLUMNS);
			rows = Math.min(rows, MAX_ROWS);
		}

		int stride = columns + 1;
		for (int j = 0; j <= rows; j++) {
			float r = j / (float) rows;
			for (int i = 0; i <= columns; i++) {
				float s = i / (float) columns;
				int n = j * stride + i;
				this.nx[n] = bilerp(this.qx, s, r);
				this.ny[n] = bilerp(this.qy, s, r);
				this.nz[n] = bilerp(this.qz, s, r);
				this.nu[n] = bilerp(this.qu, s, r);
				this.nv[n] = bilerp(this.qv, s, r);
				int color = this.qColor[0];
				if (this.fx.field() != null) {
					float v = (this.ny[n] - this.fx.originY()) / NametagColorFields.CELL_HEIGHT;
					color = NametagColorFields.multiply(color, this.fx.field().colorAt(this.nx[n] - this.fx.spanStartX(), v));
				}
				this.nColor[n] = color;
			}
		}

		int light = this.qLight[0];
		float[] rowShift = this.fx.rowShift();
		for (int j = 0; j < rows; j++) {
			for (int i = 0; i < columns; i++) {
				int a = j * stride + i;
				int b = (j + 1) * stride + i;
				float shift = 0f;
				if (rowShift != null) {
					float centreV = ((this.ny[a] + this.ny[b]) * 0.5f - this.fx.originY()) / NametagColorFields.CELL_HEIGHT;
					int row = Math.max(0, Math.min(rowShift.length - 1, (int) (centreV * rowShift.length)));
					shift = rowShift[row];
				}
				this.vertex(a, shift, light);
				this.vertex(b, shift, light);
				this.vertex(b + 1, shift, light);
				this.vertex(a + 1, shift, light);
			}
		}
		this.count = 0;
	}

	private void vertex(int n, float shift, int light) {
		float x = this.nx[n] + shift;
		float y = this.ny[n];
		GlyphFx.Affine transform = this.fx.transform();
		if (transform != null) {
			float tx = transform.applyX(x, y);
			y = transform.applyY(x, y);
			x = tx;
		}
		this.out.addVertex(this.pose, x, y, this.nz[n]).setColor(this.nColor[n]).setUv(this.nu[n], this.nv[n]).setLight(light);
	}

	/** One cell per font pixel; a bold glyph's extra 0.2px does not earn a cell of its own. */
	private static int cells(float length) {
		return Math.max(1, Math.round(length));
	}

	private float distance(int a, int b) {
		float dx = this.qx[b] - this.qx[a];
		float dy = this.qy[b] - this.qy[a];
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	/** Bilinear interpolation over the quad, {@code s} along the 0-3 edge and {@code r} along 0-1. */
	private static float bilerp(float[] q, float s, float r) {
		float top = q[0] + (q[3] - q[0]) * s;
		float bottom = q[1] + (q[2] - q[1]) * s;
		return top + (bottom - top) * r;
	}
}
