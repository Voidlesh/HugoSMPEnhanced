package com.hugosmpenhanced.client.feature.nametag;

/**
 * Spatial colour styles: each one is a colour <em>field</em> over the name, sampled at every font
 * texel corner of every glyph (see {@link GlyphFxVertexConsumer}) instead of once per letter. That is
 * what lets a gradient flow smoothly through the inside of each letter, a chrome style carry a
 * horizon line across the word, and fire burn upwards through it.
 *
 * <h2>Coordinates</h2>
 * {@code x} is in font pixels from the left edge of the styled span, {@code v} runs from 0 at the top
 * of the glyph cell to 1 at its bottom (one cell is {@link #CELL_HEIGHT} font pixels). Distances are
 * deliberately in font pixels rather than relative to the name's width, so a long and a short name
 * get the same stripe density, and so does the name in the tab list and above a player's head.
 *
 * <h2>Colour quality</h2>
 * Every palette is built in Oklab and baked into a lookup table once. Interpolating in Oklab keeps
 * the midpoints of a blend as vivid and as bright as its ends (an sRGB blend from violet to pink
 * sags through a duller purple), and the tables keep the per-vertex cost to an index and a lerp,
 * which matters at roughly a thousand samples per styled name per frame.
 */
public final class NametagColorFields {
	/** Height of one glyph cell in font pixels; {@code v} is measured against it. */
	public static final float CELL_HEIGHT = 8f;

	/** A colour as a function of position inside the styled span. Returns opaque ARGB. */
	@FunctionalInterface
	public interface ColorField {
		int colorAt(float x, float v);
	}

	private static final int LUT_SIZE = 1024;

	private static final int[] GRADIENT = cyclicPalette(0xFF8B5CF6, 0xFFD946EF, 0xFFF472B6, 0xFFC084FC);
	private static final int[] AURORA = cyclicPalette(0xFF2DD4BF, 0xFF4ADE80, 0xFF22D3EE, 0xFF818CF8, 0xFFC084FC, 0xFF38BDF8);
	private static final int[] RAINBOW = rainbowTable();
	/**
	 * Chrome is a reflection, not a tint: a bright sky in the top half, a hard dark horizon just
	 * above the middle, then the darker ground brightening again toward the bottom edge.
	 */
	private static final int[] CHROME = rampTable(
		new float[] {0.00f, 0.22f, 0.40f, 0.47f, 0.60f, 0.82f, 1.00f},
		new int[] {0xFFFBFCFE, 0xFFE2E8F0, 0xFFB8C2D1, 0xFF4B5567, 0xFF6F7A8D, 0xFFB4BECB, 0xFFE6EBF1}
	);
	/** Black-body style heat ramp, kept bright at the cold end so the name stays readable. */
	private static final int[] FIRE = rampTable(
		new float[] {0.00f, 0.28f, 0.52f, 0.74f, 0.90f, 1.00f},
		new int[] {0xFFA3141B, 0xFFE2361F, 0xFFF97316, 0xFFFBBF24, 0xFFFDE68A, 0xFFFFFBEB}
	);

	private NametagColorFields() {
	}

	// ---------------------------------------------------------------- colour styles ----

	/**
	 * The field for a colour style, or null if {@code effect} is not one. {@code spanWidth} is the
	 * styled span's width in font pixels, for highlights that sweep from one end of it to the other.
	 */
	public static ColorField forStyle(NametagEffect effect, double now, float spanWidth) {
		if (effect == null) {
			return null;
		}
		return switch (effect) {
			case GRADIENT -> gradient(now);
			case RAINBOW -> rainbow(now);
			case METALLIC -> metallic(now, spanWidth);
			case FIRE -> fire(now);
			case AURORA -> aurora(now);
			default -> null;
		};
	}

	/** Violet through fuchsia to pink, flowing left to right; one palette cycle per 44px. */
	private static ColorField gradient(double now) {
		float scroll = (float) (now / NametagEffect.GRADIENT.periodSeconds());
		return (x, v) -> relief(sample(GRADIENT, x / 44f - scroll), v);
	}

	/** The full hue circle at constant perceived lightness; one cycle per 60px. */
	private static ColorField rainbow(double now) {
		float scroll = (float) (now / NametagEffect.RAINBOW.periodSeconds());
		return (x, v) -> relief(sample(RAINBOW, x / 60f - scroll), v);
	}

	/**
	 * Chrome: the vertical reflection ramp, plus a narrow diagonal highlight that sweeps across the
	 * word in the first 55% of each period and then rests, so it reads as light catching the metal
	 * rather than as a pulse.
	 */
	private static ColorField metallic(double now, float spanWidth) {
		double cycle = (now / NametagEffect.METALLIC.periodSeconds()) % 1.0;
		if (cycle >= 0.55) {
			return (x, v) -> chrome(v);
		}
		float progress = (float) smootherstep(cycle / 0.55);
		return (x, v) -> sheen(chrome(v), x, v, progress, spanWidth);
	}

	/** The chrome ramp is not cyclic, so its far end must not wrap back around to the top colour. */
	private static int chrome(float v) {
		return sample(CHROME, clamp01(v) * (LUT_SIZE - 1) / (float) LUT_SIZE);
	}

	/**
	 * Fire: heat rises from the bottom of each glyph, broken up by two octaves of noise that scroll
	 * upwards, so flame tongues lick up through the letters instead of the colour just pulsing.
	 */
	private static ColorField fire(double now) {
		float rise = (float) (now * 1.9);
		float flicker = (float) (0.93 + 0.07 * fbm(now * 5.3, 11.7));
		return (x, v) -> {
			float n = (float) fbm(x * 0.23, v * 1.5 + rise);
			float heat = clamp01(1.08f - 1.12f * (1f - v) + 0.95f * (n - 0.5f));
			return scale(sample(FIRE, heat * (LUT_SIZE - 1) / (float) LUT_SIZE), flicker);
		};
	}

	/** Aurora: slow, noise-driven curtains of teal, green and violet, brightest along the top. */
	private static ColorField aurora(double now) {
		double drift = now / NametagEffect.AURORA.periodSeconds();
		return (x, v) -> {
			float n = (float) fbm(x * 0.05 - drift * 1.4, drift * 0.6 + 3.7);
			int c = sample(AURORA, n * 1.7f + x / 140f - (float) drift * 0.35f);
			float top = 1f - v;
			return mixWhite(c, 0.20f * top * top);
		};
	}

	// ---------------------------------------------------------------- shimmer ----

	/** How far a white name is dimmed between highlights; the band of light lifts it back to full. */
	private static final float SHIMMER_DIM = 0.26f;

	/**
	 * Shimmer as a filter on any field (or on the glyph's plain colour): a narrow diagonal band of
	 * light with a soft bloom, sweeping across the span in the first 60% of the period.
	 *
	 * <p>The name rests slightly dimmed and the band brings it up to full brightness and on toward
	 * white, the "slide to unlock" look. Only adding light would do nothing at all on white text —
	 * which most names are — since white has nowhere brighter to go. The dimming scales with the
	 * colour's luminance squared: white gets the full amount so the band has room to show, while a
	 * saturated colour style is barely touched and keeps its vibrancy.
	 */
	public static ColorField shimmer(ColorField base, double now, float spanWidth) {
		double cycle = (now / NametagEffect.SHIMMER.periodSeconds()) % 1.0;
		if (cycle >= 0.6) {
			return (x, v) -> dimForShimmer(base.colorAt(x, v), 0f);
		}
		float progress = (float) smootherstep(cycle / 0.6);
		float pos = -10f + (spanWidth + 20f) * progress;
		return (x, v) -> {
			float d = x + (0.5f - v) * 4.5f - pos;
			float core = gaussian(d, 1.6f);
			float bloom = gaussian(d, 5.5f);
			int c = dimForShimmer(base.colorAt(x, v), Math.min(1f, core + 0.55f * bloom));
			return mixWhite(c, 0.85f * core + 0.18f * bloom);
		};
	}

	private static int dimForShimmer(int argb, float lift) {
		float luma = (0.2126f * ((argb >> 16) & 0xFF) + 0.7152f * ((argb >> 8) & 0xFF) + 0.0722f * (argb & 0xFF)) / 255f;
		return scale(argb, 1f - SHIMMER_DIM * luma * luma * (1f - lift));
	}

	private static int sheen(int base, float x, float v, float progress, float spanWidth) {
		float pos = -12f + (spanWidth + 24f) * progress;
		float d = x + (0.5f - v) * 6f - pos;
		float core = gaussian(d, 1.3f);
		float bloom = 0.30f * gaussian(d, 4.5f);
		return mixWhite(base, Math.min(1f, 0.95f * core + bloom));
	}

	/** Constant colour, for a glyph that has no colour style but needs a field for Shimmer. */
	public static ColorField flat(int argb) {
		return (x, v) -> argb;
	}

	// ---------------------------------------------------------------- helpers ----

	/**
	 * A light top-to-bottom relief: a touch lighter along the top edge, a touch darker along the
	 * bottom. Barely visible on its own, it stops a flat gradient from looking printed on.
	 */
	private static int relief(int argb, float v) {
		float k = 0.5f - v;
		return k > 0 ? mixWhite(argb, 0.16f * k) : scale(argb, 1f + 0.16f * k);
	}

	private static int sample(int[] table, float position) {
		float p = position - (float) Math.floor(position);
		float f = p * table.length;
		int i0 = (int) f;
		if (i0 >= table.length) {
			i0 = 0;
		}
		int i1 = i0 + 1 == table.length ? 0 : i0 + 1;
		return lerpArgb(table[i0], table[i1], f - (int) f);
	}

	public static int mixWhite(int argb, float amount) {
		if (amount <= 0f) {
			return argb;
		}
		float a = Math.min(1f, amount);
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (argb & 0xFF000000)
			| (Math.round(r + (255 - r) * a) << 16)
			| (Math.round(g + (255 - g) * a) << 8)
			| Math.round(b + (255 - b) * a);
	}

	public static int scale(int argb, float factor) {
		if (factor == 1f) {
			return argb;
		}
		float k = clamp01(factor);
		return (argb & 0xFF000000)
			| (Math.round(((argb >> 16) & 0xFF) * k) << 16)
			| (Math.round(((argb >> 8) & 0xFF) * k) << 8)
			| Math.round((argb & 0xFF) * k);
	}

	/** Component-wise product of two ARGB colours, alpha included. */
	public static int multiply(int a, int b) {
		if (b == 0xFFFFFFFF) {
			return a;
		}
		if (a == 0xFFFFFFFF) {
			return b;
		}
		return (mulChannel(a >>> 24, b >>> 24) << 24)
			| (mulChannel((a >> 16) & 0xFF, (b >> 16) & 0xFF) << 16)
			| (mulChannel((a >> 8) & 0xFF, (b >> 8) & 0xFF) << 8)
			| mulChannel(a & 0xFF, b & 0xFF);
	}

	private static int mulChannel(int a, int b) {
		return (a * b + 127) / 255;
	}

	private static int lerpArgb(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return 0xFF000000
			| (Math.round(ar + (br - ar) * t) << 16)
			| (Math.round(ag + (bg - ag) * t) << 8)
			| Math.round(ab + (bb - ab) * t);
	}

	private static float gaussian(float d, float width) {
		float k = d / width;
		return (float) Math.exp(-(k * k));
	}

	private static double smootherstep(double t) {
		double c = t < 0 ? 0 : (t > 1 ? 1 : t);
		return c * c * c * (c * (c * 6 - 15) + 10);
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	// ---------------------------------------------------------------- noise ----

	/** Two octaves of value noise with quintic smoothing, in 0..1. */
	static double fbm(double x, double y) {
		return (valueNoise(x, y) * 0.65 + valueNoise(x * 2.03 + 17.1, y * 2.03 - 4.3) * 0.35);
	}

	private static double valueNoise(double x, double y) {
		double fx = Math.floor(x);
		double fy = Math.floor(y);
		int ix = (int) fx;
		int iy = (int) fy;
		double tx = fade(x - fx);
		double ty = fade(y - fy);
		double a = hash01(ix, iy);
		double b = hash01(ix + 1, iy);
		double c = hash01(ix, iy + 1);
		double d = hash01(ix + 1, iy + 1);
		double top = a + (b - a) * tx;
		double bottom = c + (d - c) * tx;
		return top + (bottom - top) * ty;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	/** Deterministic hash of an integer lattice point to 0..1. */
	static double hash01(int x, int y) {
		int h = x * 0x27D4EB2D ^ y * 0x165667B1;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	// ---------------------------------------------------------------- table building (Oklab) ----

	private static int[] cyclicPalette(int... stops) {
		float[] positions = new float[stops.length + 1];
		int[] colors = new int[stops.length + 1];
		for (int i = 0; i < stops.length; i++) {
			positions[i] = i / (float) stops.length;
			colors[i] = stops[i];
		}
		positions[stops.length] = 1f;
		colors[stops.length] = stops[0];
		return rampTable(positions, colors);
	}

	private static int[] rampTable(float[] positions, int[] colors) {
		double[][] lab = new double[colors.length][];
		for (int i = 0; i < colors.length; i++) {
			lab[i] = srgbToOklab(colors[i]);
		}
		int[] table = new int[LUT_SIZE];
		int segment = 0;
		for (int i = 0; i < LUT_SIZE; i++) {
			float p = i / (float) (LUT_SIZE - 1);
			while (segment < positions.length - 2 && p > positions[segment + 1]) {
				segment++;
			}
			float span = positions[segment + 1] - positions[segment];
			double t = span <= 0 ? 0 : clamp01((p - positions[segment]) / span);
			double[] a = lab[segment];
			double[] b = lab[segment + 1];
			table[i] = oklabToSrgb(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
		}
		return table;
	}

	/**
	 * Oklch at constant lightness. Only lightness is held exactly: sRGB cannot reach the same chroma
	 * at every hue, and uneven lightness is what makes a rainbow throb, uneven chroma is not.
	 */
	private static int[] rainbowTable() {
		int[] table = new int[LUT_SIZE];
		for (int i = 0; i < LUT_SIZE; i++) {
			table[i] = NametagEffectEngine.oklch(0.74, 0.19, 360.0 * i / LUT_SIZE);
		}
		return table;
	}

	private static double[] srgbToOklab(int argb) {
		double r = srgbToLinear(((argb >> 16) & 0xFF) / 255.0);
		double g = srgbToLinear(((argb >> 8) & 0xFF) / 255.0);
		double b = srgbToLinear((argb & 0xFF) / 255.0);
		double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
		double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
		double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
		return new double[] {
			0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
			1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
			0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
		};
	}

	private static int oklabToSrgb(double l, double a, double b) {
		double lp = l + 0.3963377774 * a + 0.2158037573 * b;
		double mp = l - 0.1055613458 * a - 0.0638541728 * b;
		double sp = l - 0.0894841775 * a - 1.2914855480 * b;
		double lc = lp * lp * lp;
		double mc = mp * mp * mp;
		double sc = sp * sp * sp;
		double r = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc;
		double g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc;
		double bl = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc;
		return 0xFF000000 | (toByte(r) << 16) | (toByte(g) << 8) | toByte(bl);
	}

	private static double srgbToLinear(double c) {
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static int toByte(double linear) {
		double c = linear <= 0 ? 0 : (linear >= 1 ? 1 : linear);
		double s = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
		return (int) Math.round(s * 255.0);
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
