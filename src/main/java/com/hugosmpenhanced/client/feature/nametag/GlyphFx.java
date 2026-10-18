package com.hugosmpenhanced.client.feature.nametag;

import com.hugosmpenhanced.client.feature.nametag.NametagColorFields.ColorField;

/**
 * What {@link GlyphFxVertexConsumer} does to one glyph draw.
 *
 * @param field      colour per position, multiplied onto the colour the glyph was drawn with; null
 *                   keeps that colour as is
 * @param spanStartX left edge of the styled span in local text space, so field coordinates are
 *                   relative to the span rather than to wherever the span happens to be drawn
 * @param originY    top of this glyph's cell in local text space, including any letter offset, so
 *                   the field's {@code v} stays attached to the glyph while it moves
 * @param transform  per-letter affine transform in local text space, or null
 * @param rowShift   horizontal displacement per font pixel row (Glitch), or null
 */
public record GlyphFx(ColorField field, float spanStartX, float originY, Affine transform, float[] rowShift) {
	/** Whether the glyph quad has to be cut into font-pixel cells (as opposed to only moved). */
	boolean subdivides() {
		return field != null || rowShift != null;
	}

	/** A 2D affine map {@code (x, y) -> (m00 x + m01 y + tx, m10 x + m11 y + ty)}. */
	public record Affine(float m00, float m01, float m10, float m11, float tx, float ty) {
		public static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

		public float applyX(float x, float y) {
			return m00 * x + m01 * y + tx;
		}

		public float applyY(float x, float y) {
			return m10 * x + m11 * y + ty;
		}

		/** Scales by {@code (sx, sy)} about {@code (px, py)}. */
		public static Affine scaleAbout(float sx, float sy, float px, float py) {
			return new Affine(sx, 0, 0, sy, px - sx * px, py - sy * py);
		}

		/** Rotates by {@code radians} about {@code (px, py)}. */
		public static Affine rotateAbout(float radians, float px, float py) {
			float c = (float) Math.cos(radians);
			float s = (float) Math.sin(radians);
			return new Affine(c, -s, s, c, px - c * px + s * py, py - s * px - c * py);
		}

		public static Affine translate(float dx, float dy) {
			return new Affine(1, 0, 0, 1, dx, dy);
		}

		/** {@code this} applied after {@code first}. */
		public Affine after(Affine first) {
			return new Affine(
				m00 * first.m00 + m01 * first.m10,
				m00 * first.m01 + m01 * first.m11,
				m10 * first.m00 + m11 * first.m10,
				m10 * first.m01 + m11 * first.m11,
				m00 * first.tx + m01 * first.ty + tx,
				m10 * first.tx + m11 * first.ty + ty
			);
		}
	}
}
