package com.hugosmpenhanced.client.feature.nametag;

import java.util.Set;

/**
 * Pure math for the nametag style effects, implementing {@code docs/SPEC.md} /
 * {@code docs/effects.json} literally — every constant here is the spec's constant, so the Java
 * renderer, the CSS reference in {@code docs/preview.html} and any future shader all produce the
 * same motion.
 *
 * <p>Nothing in here touches Minecraft classes: every value is a function of (effect, letter index,
 * letter count, elapsed seconds) only, which is what lets the exact same computation drive the 2D
 * (tab list, scoreboard, chat) renderer, the 3D in-world nametag and the animated preview buttons
 * in the style picker, and lets it be checked in isolation.
 *
 * <h2>Units</h2>
 * All distances are returned in <strong>em</strong>, where 1em is the rendered text height — the
 * caller multiplies by {@code font.lineHeight}. Keeping them relative is what makes the same
 * numbers work unchanged for a crisp GUI label and for a nametag shrunk by distance above a
 * player's head, with no per-context amplitude tuning.
 *
 * <h2>Time</h2>
 * {@code t} runs 0..1 across one period and every curve is built to be seamless at the loop point
 * (the jitter and flicker curves use only harmonics that complete whole cycles), so nothing has to
 * be cross-faded at the wrap.
 */
public final class NametagEffectEngine {
	/** Spec fill priority step 4: the base ink colour when nothing else claims the fill. */
	public static final int BASE_COLOR = 0xFFE8E8EE;
	/** Outline ring around light text: near-black with a hint of blue, so it reads as ink, not a hole. */
	public static final int OUTLINE_DARK = 0xF0141420;
	/** Outline ring around dark text. */
	public static final int OUTLINE_LIGHT = 0xFFEDEDF3;
	/** Spec fill priority step 3: Glowing with no colour style gives a solid white fill. */
	public static final int GLOW_FILL_COLOR = 0xFFFFFFFF;

	private static final double TAU = Math.PI * 2;

	// Time is measured from class load rather than System.nanoTime()'s raw (and on some systems
	// very large, e.g. days of machine uptime) origin, so the double stays small and every sine
	// sample keeps full precision for the entire play session instead of slowly losing
	// sub-millisecond resolution — the main fix for animations that looked like they were
	// "catching"/stepping instead of gliding.
	private static final long START_NANOS = System.nanoTime();

	private NametagEffectEngine() {
	}

	public static double nowSeconds() {
		return (System.nanoTime() - START_NANOS) / 1_000_000_000.0;
	}

	/**
	 * The effect's normalised phase for letter {@code index} of {@code total}.
	 *
	 * <p>Letter-scope effects are advanced by {@code (n - 1 - i) * staggerSeconds}, so letter 0
	 * leads and the effect sweeps left to right — the same sign convention as the negative
	 * {@code animation-delay} the CSS reference uses.
	 */
	public static double phase(NametagEffect effect, int index, int total, double elapsed) {
		double period = effect.periodSeconds();
		if (period <= 0) {
			return 0;
		}
		double offset = effect.scope() == NametagEffect.Scope.LETTER
			? (total - 1 - index) * effect.staggerSeconds()
			: 0;
		double t = ((elapsed + offset) / period) % 1.0;
		return t < 0 ? t + 1 : t;
	}

	/** The single active colour effect (they are mutually exclusive), or null for none. */
	public static NametagEffect colorEffect(Set<NametagEffect> effects) {
		for (NametagEffect effect : NametagEffect.values()) {
			if (effect.group() == NametagEffect.Group.COLOR && effects.contains(effect)) {
				return effect;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- motion, word scope ----

	/**
	 * The composed word-scope transform. Held as separate components rather than a baked matrix so
	 * the caller can apply them in the spec's nesting order — shake, flip, skew, grow, outermost
	 * first — which is what makes the composition match the CSS reference's nested layers exactly.
	 */
	public record WordTransform(
		float shakeDxEm, float shakeDyEm, float shakeRotationRad,
		float flipScaleY,
		float skewShearX,
		float growScale
	) {
		public static final WordTransform IDENTITY = new WordTransform(0, 0, 0, 1, 0, 1);

		public boolean isIdentity() {
			return shakeDxEm == 0 && shakeDyEm == 0 && shakeRotationRad == 0
				&& flipScaleY == 1 && skewShearX == 0 && growScale == 1;
		}
	}

	public static WordTransform wordTransform(Set<NametagEffect> effects, double elapsed) {
		float dx = 0, dy = 0, rot = 0, flipScaleY = 1, shear = 0, scale = 1;

		if (effects.contains(NametagEffect.SHAKING)) {
			// Harmonics 3/5/7 all complete whole cycles per period, so the jitter reads as nervous
			// motion yet still returns exactly to its starting value — no seam at the loop point.
			double t = phase(NametagEffect.SHAKING, 0, 1, elapsed);
			dx = (float) (0.085 * (0.60 * Math.sin(TAU * 3 * t)
				+ 0.30 * Math.sin(TAU * 5 * t + 1.7)
				+ 0.20 * Math.sin(TAU * 7 * t + 0.6)));
			dy = (float) (0.022 * Math.sin(TAU * 5 * t + 2.1));
			rot = (float) Math.toRadians(1.5 * (0.45 * Math.sin(TAU * 3 * t + 0.9)
				+ 0.20 * Math.sin(TAU * 5 * t)));
		}
		if (effects.contains(NametagEffect.FLIPPING)) {
			flipScaleY = flipScaleY(phase(NametagEffect.FLIPPING, 0, 1, elapsed));
		}
		if (effects.contains(NametagEffect.SKEWING)) {
			double t = phase(NametagEffect.SKEWING, 0, 1, elapsed);
			// CSS skewX(a) is x' = x + tan(a)*y, so the shear factor is the tangent, not the angle.
			shear = (float) Math.tan(Math.toRadians(14.0 * Math.sin(TAU * t)));
		}
		if (effects.contains(NametagEffect.GROWING)) {
			double t = phase(NametagEffect.GROWING, 0, 1, elapsed);
			scale = (float) (1.14 + 0.14 * Math.sin(TAU * t - Math.PI / 2));
		}

		return new WordTransform(dx, dy, rot, flipScaleY, shear, scale);
	}

	/**
	 * rotateX 0deg to 360deg across the first 52% of the period, then a hold — the hold is what
	 * makes it read as a deliberate flip rather than a constant tumble.
	 *
	 * <p>Projected to a plain vertical scale: with the spec's 40em perspective and glyphs only
	 * about half an em from the pivot, the perspective foreshortening term stays under ~1.5% and is
	 * not an affine transform anyway, so {@code cos} alone reproduces the reference to well within a
	 * pixel. The scale is deliberately left signed so the back half renders flipped, exactly as a
	 * real rotateX does, instead of folding flat and bouncing back.
	 */
	private static float flipScaleY(double t) {
		double segment = Math.min(1.0, t / 0.52);
		double eased = cubicBezier(0.62, 0.0, 0.38, 1.0, segment);
		return (float) Math.cos(TAU * eased);
	}

	// ---------------------------------------------------------------- motion, letter scope ----

	/**
	 * The letter's vertical offset in em: Waving's continuous swell plus Iterating's single hop.
	 *
	 * <p>Both write translateY, which the spec's one-property-per-effect rule would normally
	 * forbid — but translations simply add, so there is nothing to resolve and the two read as one
	 * combined motion.
	 */
	public static float letterOffsetYEm(Set<NametagEffect> effects, int index, int total, double elapsed) {
		float dy = 0f;
		if (effects.contains(NametagEffect.WAVING)) {
			double t = phase(NametagEffect.WAVING, index, total, elapsed);
			dy += (float) (-0.15 * Math.sin(TAU * t));
		}
		if (effects.contains(NametagEffect.ITERATING)) {
			// A narrow bump rather than a full sine: each letter lifts and settles once, and the
			// 0.10s stagger sends that hop down the word letter by letter, with a clear rest before
			// it starts over. The bump is centred on t=0 and built from min(t, 1-t), so it is
			// symmetric across the loop point and never snaps.
			double t = phase(NametagEffect.ITERATING, index, total, elapsed);
			double d = Math.min(t, 1 - t);
			dy += (float) (-0.20 * gaussian(d, 0.070));
		}
		return dy;
	}

	// ---------------------------------------------------------------- motion, per-letter transform ----

	/**
	 * The per-letter transform from Bouncing and Swinging, in local text coordinates, or null if
	 * neither is active. {@code centreX} is the glyph's horizontal centre and {@code top} the top of
	 * its cell (with any Waving/Iterating offset already applied), so the transform is anchored to
	 * the letter wherever it currently is.
	 */
	public static GlyphFx.Affine letterTransform(Set<NametagEffect> effects, int index, int total, double elapsed,
		float centreX, float top, float unit) {
		GlyphFx.Affine transform = null;
		if (effects.contains(NametagEffect.BOUNCING)) {
			transform = bounce(phase(NametagEffect.BOUNCING, index, total, elapsed), centreX, top + BASELINE_EM * unit, unit);
		}
		if (effects.contains(NametagEffect.SWINGING)) {
			// A pendulum hung from the top edge of the letter. The small second harmonic makes the
			// turn at each end of the swing linger a little, the way a real hanging sign's does.
			double t = phase(NametagEffect.SWINGING, index, total, elapsed);
			double degrees = 10.5 * Math.sin(TAU * t) + 1.2 * Math.sin(TAU * 3 * t);
			GlyphFx.Affine swing = GlyphFx.Affine.rotateAbout((float) Math.toRadians(degrees), centreX, top - 0.5f);
			transform = transform == null ? swing : swing.after(transform);
		}
		return transform;
	}

	/** The baseline sits 7 of the 9 font pixels down the cell. */
	private static final float BASELINE_EM = 7f / 9f;

	/**
	 * One bounce cycle: a gravity arc in the first half of the period, a squash on landing, a rest,
	 * then a short crouch that leads straight into the next take-off. Every phase starts and ends at
	 * scale 1, so the cycle has no seams. Scaling is about the bottom centre of the letter, so it
	 * squashes into the ground rather than shrinking towards its middle.
	 */
	private static GlyphFx.Affine bounce(double t, float centreX, float baseline, float unit) {
		double lift = 0;
		double sx = 1;
		double sy = 1;
		if (t < 0.5) {
			double u = t / 0.5;
			lift = 4 * u * (1 - u) * 0.42;
			// Stretch with speed: strongest at take-off and landing, gone at the top of the arc.
			double speed = (1 - 2 * u) * (1 - 2 * u);
			double stretch = 0.13 * speed * smoothstep(0, 0.10, u) * (1 - smoothstep(0.90, 1, u));
			sy = 1 + stretch;
			sx = 1 - 0.55 * stretch;
		} else if (t < 0.72) {
			double w = (t - 0.5) / 0.22;
			double squash = Math.sin(Math.PI * w) * (1 - 0.4 * w);
			sy = 1 - 0.22 * squash;
			sx = 1 + 0.14 * squash;
		} else if (t >= 0.86) {
			double crouch = Math.sin(Math.PI * (t - 0.86) / 0.14);
			sy = 1 - 0.08 * crouch;
			sx = 1 + 0.05 * crouch;
		}
		GlyphFx.Affine scale = GlyphFx.Affine.scaleAbout((float) sx, (float) sy, centreX, baseline);
		return GlyphFx.Affine.translate(0, (float) (-lift * unit)).after(scale);
	}

	// ---------------------------------------------------------------- overlays, glitch ----

	/**
	 * One frame of a Glitch burst: a horizontal shift for each of the glyph cell's 8 pixel rows (in
	 * font pixels) and an RGB split offset (in em). Null between bursts.
	 */
	public record Glitch(float[] rowShift, float splitXEm, float splitYEm) {
		public static final int COLOR_A = 0xFF2E63;
		public static final int COLOR_B = 0x08D9D6;
		public static final float ALPHA = 0.70f;
	}

	/** Glitch frames advance in hard steps at this rate, like a corrupted video stream. */
	private static final double GLITCH_STEPS_PER_SECOND = 30;

	public static Glitch glitch(Set<NametagEffect> effects, double elapsed) {
		if (!effects.contains(NametagEffect.GLITCH)) {
			return null;
		}
		// Each window of one period holds one burst at a random point, sometimes followed by a short
		// echo; random spacing is what keeps it from reading as a metronome.
		double period = NametagEffect.GLITCH.periodSeconds();
		double position = elapsed / period;
		int window = (int) Math.floor(position);
		double f = position - window;
		double start = 0.08 + 0.62 * NametagColorFields.hash01(window, 1);
		double length = 0.06 + 0.14 * NametagColorFields.hash01(window, 2);
		boolean burst = f >= start && f < start + length;
		if (!burst && NametagColorFields.hash01(window, 3) > 0.5) {
			double echo = start + length + 0.04 + 0.10 * NametagColorFields.hash01(window, 4);
			burst = f >= echo && f < echo + 0.045;
		}
		if (!burst) {
			return null;
		}

		int step = (int) Math.floor(elapsed * GLITCH_STEPS_PER_SECOND);
		float[] rows = new float[8];
		// About a third of the frames only flash the colour split; the rest also tear the cell in
		// bands of two to three rows, of which only some move, by one or (rarely) two whole pixels.
		// Kept that small on purpose: the name must stay readable through a burst, and whole-pixel
		// steps keep the torn edges as crisp as the font itself.
		if (NametagColorFields.hash01(step, 5) > 0.33) {
			int row = 0;
			int band = 0;
			while (row < rows.length) {
				int height = 2 + (int) (NametagColorFields.hash01(step, 40 + band) * 2);
				float shift = 0f;
				if (NametagColorFields.hash01(step, 60 + band) > 0.55) {
					float sign = NametagColorFields.hash01(step, 80 + band) < 0.5 ? -1f : 1f;
					shift = sign * (NametagColorFields.hash01(step, 100 + band) > 0.8 ? 2f : 1f);
				}
				for (int k = 0; k < height && row < rows.length; k++) {
					rows[row++] = shift;
				}
				band++;
			}
		}
		float splitX = (float) ((0.45 + 0.55 * NametagColorFields.hash01(step, 7)) * 0.12
			* (NametagColorFields.hash01(step, 9) < 0.5 ? -1 : 1));
		float splitY = (float) ((NametagColorFields.hash01(step, 8) - 0.5) * 0.06);
		return new Glitch(rows, splitX, splitY);
	}

	// ---------------------------------------------------------------- overlays, word scope ----

	/**
	 * Fade, as a brightness multiplier running 0.00 (black) to 1.00 and back.
	 *
	 * <p>The spec animated opacity here, which let the background show through. Darkening the
	 * colour instead keeps the name solid, and means the effect also works where the renderer draws
	 * on top of text vanilla already drew, such as chat.
	 */
	public static float wordBrightness(Set<NametagEffect> effects, double elapsed) {
		if (!effects.contains(NametagEffect.FADE)) {
			return 1f;
		}
		double t = phase(NametagEffect.FADE, 0, 1, elapsed);
		return (float) (0.5 - 0.5 * Math.cos(TAU * t));
	}

	/** An RGB split that follows the glyph outline; offsets in em. */
	public record Chromatic(float xEm, float yEm) {
		public static final int COLOR_A = 0xFF3B6B;
		public static final int COLOR_B = 0x3BD6FF;
		public static final float ALPHA = 0.90f;
	}

	public static Chromatic chromatic(Set<NametagEffect> effects, double elapsed) {
		if (!effects.contains(NametagEffect.CHROMATIC)) {
			return null;
		}
		double t = phase(NametagEffect.CHROMATIC, 0, 1, elapsed);
		return new Chromatic((float) (0.085 * Math.sin(TAU * t)), (float) (0.022 * Math.sin(2 * TAU * t)));
	}

	/**
	 * Two layered halos whose radius and alpha breathe together. Radii in em, alphas in 0..1.
	 *
	 * <p>The colours here are for white text only. Over a coloured name the halo takes the text's
	 * own colour instead (see {@link #glowColors}), so it reads as the letters emitting light, like
	 * neon, rather than as a lavender cloud behind them.
	 *
	 * <p>The outer radius peaks at 0.65em rather than the spec's original 0.96em: past about half
	 * a glyph the ring copies stop blending and show up as ghost letters around the name.
	 */
	public record Glow(float innerRadiusEm, float innerAlpha, float outerRadiusEm, float outerAlpha) {
		public static final int INNER_COLOR = 0xFFFFFF;
		public static final int OUTER_COLOR = 0xA896FF;
	}

	public static Glow glow(Set<NametagEffect> effects, double elapsed) {
		if (!effects.contains(NametagEffect.GLOWING)) {
			return null;
		}
		double t = phase(NametagEffect.GLOWING, 0, 1, elapsed);
		double k = (1 - Math.cos(TAU * t)) / 2;
		return new Glow(
			(float) (0.10 + 0.18 * k), (float) (0.45 + 0.45 * k),
			(float) (0.22 + 0.43 * k), (float) (0.22 + 0.40 * k)
		);
	}

	/**
	 * The Outline ring colour for a glyph filled with {@code fill}: dark around light text, light
	 * around dark text. The switch sits low, at a relative luminance of 0.06 (about {@code #474747},
	 * using a gamma of 2 as a cheap stand-in for sRGB decoding): mid-tones such as a saturated red or
	 * blue still read better with a dark ring, and only genuinely dark text gets a light one.
	 */
	public static int outlineColor(int fill) {
		double r = ((fill >> 16) & 0xFF) / 255.0;
		double g = ((fill >> 8) & 0xFF) / 255.0;
		double b = (fill & 0xFF) / 255.0;
		double luma = 0.2126 * r * r + 0.7152 * g * g + 0.0722 * b * b;
		return luma < 0.06 ? OUTLINE_LIGHT : OUTLINE_DARK;
	}

	/**
	 * Halo colours {@code {inner, outer}} (RGB) for a glyph whose fill colour at its centre is
	 * {@code fill}. A near-neutral fill (white, grey) keeps the white and lavender defaults; a
	 * coloured one glows in its own colour, with the inner halo lifted toward white so the core
	 * still looks hot.
	 */
	public static int[] glowColors(int fill) {
		int r = (fill >> 16) & 0xFF;
		int g = (fill >> 8) & 0xFF;
		int b = fill & 0xFF;
		int spread = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
		if (spread < 40) {
			return new int[] {Glow.INNER_COLOR, Glow.OUTER_COLOR};
		}
		return new int[] {NametagColorFields.mixWhite(fill, 0.55f) & 0xFFFFFF, fill & 0xFFFFFF};
	}

	// ---------------------------------------------------------------- helpers ----

	/**
	 * Oklch (perceptual lightness, chroma, hue in degrees) to opaque ARGB.
	 *
	 * <p>sRGB cannot hold the same chroma at every hue, so a request that falls outside the gamut
	 * has its chroma reduced — by bisection, keeping lightness and hue exactly — until it fits.
	 * Clipping the RGB channels instead would shift the hue and flatten the lightness, which is
	 * precisely the banding this colour space is being used to avoid.
	 */
	public static int oklch(double lightness, double chroma, double hueDegrees) {
		double h = Math.toRadians(hueDegrees);
		double ax = Math.cos(h);
		double by = Math.sin(h);
		double fitted = chroma;
		if (!oklabInGamut(lightness, chroma * ax, chroma * by)) {
			double lo = 0;
			double hi = chroma;
			for (int i = 0; i < 20; i++) {
				double mid = (lo + hi) / 2;
				if (oklabInGamut(lightness, mid * ax, mid * by)) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			fitted = lo;
		}
		return oklabToArgb(lightness, fitted * ax, fitted * by);
	}

	private static boolean oklabInGamut(double l, double a, double b) {
		double[] rgb = oklabToLinear(l, a, b);
		for (double v : rgb) {
			if (v < -1e-4 || v > 1 + 1e-4) {
				return false;
			}
		}
		return true;
	}

	private static double[] oklabToLinear(double l, double a, double b) {
		double lp = l + 0.3963377774 * a + 0.2158037573 * b;
		double mp = l - 0.1055613458 * a - 0.0638541728 * b;
		double sp = l - 0.0894841775 * a - 1.2914855480 * b;
		double lc = lp * lp * lp;
		double mc = mp * mp * mp;
		double sc = sp * sp * sp;
		return new double[] {
			4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
			-1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
			-0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc
		};
	}

	private static int oklabToArgb(double l, double a, double b) {
		double[] rgb = oklabToLinear(l, a, b);
		return 0xFF000000
			| (channel((float) linearToSrgb(rgb[0])) << 16)
			| (channel((float) linearToSrgb(rgb[1])) << 8)
			| channel((float) linearToSrgb(rgb[2]));
	}

	private static double linearToSrgb(double v) {
		double c = clamp01(v);
		return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
	}

	/** {@code exp(-(d/width)^2)} — the falloff every travelling highlight in the spec is built on. */
	private static double gaussian(double d, double width) {
		double k = d / width;
		return Math.exp(-(k * k));
	}

	/**
	 * Solves a CSS {@code cubic-bezier(x1, y1, x2, y2)} for the given input progress. Newton's
	 * method converges in a couple of iterations for the well-behaved curves used here; the
	 * bisection pass afterwards covers the flat-derivative case Newton can stall on.
	 */
	public static double cubicBezier(double x1, double y1, double x2, double y2, double x) {
		if (x <= 0) {
			return 0;
		}
		if (x >= 1) {
			return 1;
		}
		double t = x;
		for (int i = 0; i < 8; i++) {
			double dx = bezierAxis(t, x1, x2) - x;
			if (Math.abs(dx) < 1e-6) {
				return bezierAxis(t, y1, y2);
			}
			double slope = bezierAxisSlope(t, x1, x2);
			if (Math.abs(slope) < 1e-6) {
				break;
			}
			t -= dx / slope;
		}
		double lo = 0, hi = 1;
		t = x;
		for (int i = 0; i < 24; i++) {
			double value = bezierAxis(t, x1, x2);
			if (Math.abs(value - x) < 1e-6) {
				break;
			}
			if (value < x) {
				lo = t;
			} else {
				hi = t;
			}
			t = (lo + hi) / 2;
		}
		return bezierAxis(t, y1, y2);
	}

	private static double bezierAxis(double t, double a, double b) {
		double u = 1 - t;
		return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
	}

	private static double bezierAxisSlope(double t, double a, double b) {
		double u = 1 - t;
		return 3 * u * u * a + 6 * u * t * (b - a) + 3 * t * t * (1 - b);
	}

	private static double smoothstep(double edge0, double edge1, double x) {
		double t = clamp01((x - edge0) / (edge1 - edge0));
		return t * t * (3 - 2 * t);
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	private static int channel(float v) {
		int i = Math.round(v * 255f);
		return i < 0 ? 0 : (i > 255 ? 255 : i);
	}

	/** {@code rgb} (no alpha) plus an alpha in 0..1, as ARGB. */
	public static int withAlpha(int rgb, float alpha) {
		int a = Math.round((float) clamp01(alpha) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Scales an ARGB colour's RGB toward black by {@code factor}, leaving its alpha alone. */
	public static int scaleRgb(int argb, float factor) {
		if (factor >= 1f) {
			return argb;
		}
		float k = (float) clamp01(factor);
		return (argb & 0xFF000000)
			| (channel(((argb >> 16) & 0xFF) / 255f * k) << 16)
			| (channel(((argb >> 8) & 0xFF) / 255f * k) << 8)
			| channel((argb & 0xFF) / 255f * k);
	}

	/** Scales an ARGB colour's existing alpha by {@code factor}. */
	public static int scaleAlpha(int argb, float factor) {
		int a = Math.round(((argb >>> 24) & 0xFF) * (float) clamp01(factor));
		return (Math.min(255, Math.max(0, a)) << 24) | (argb & 0xFFFFFF);
	}
}
