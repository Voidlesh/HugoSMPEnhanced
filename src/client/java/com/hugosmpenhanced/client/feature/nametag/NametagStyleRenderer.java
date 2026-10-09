package com.hugosmpenhanced.client.feature.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.duck.GuiRenderStateAccess;
import com.hugosmpenhanced.client.feature.nametag.NametagEffectEngine.Chromatic;
import com.hugosmpenhanced.client.feature.nametag.NametagEffectEngine.Glow;
import com.hugosmpenhanced.client.feature.nametag.NametagEffectEngine.Shimmer;
import com.hugosmpenhanced.client.feature.nametag.NametagEffectEngine.WordTransform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Renders a name with the user's configured {@link NametagEffect}s applied, following the effect
 * model in {@code docs/SPEC.md}: {@link NametagEffect.Scope#WORD} effects transform or filter the
 * whole name once, as one unit, and {@link NametagEffect.Scope#LETTER} effects run per glyph inside
 * that with a phase stagger. All the timing and amplitude maths lives in {@link NametagEffectEngine};
 * this class only turns it into draw calls.
 *
 * <h2>Only the name is touched</h2>
 * A server rank prefix/suffix must come out pixel-identical to vanilla. So the component is cut in
 * exactly three pieces — everything before the name, the name, everything after — and the two outer
 * pieces are handed to vanilla as <em>single</em> draws of a component that still carries every
 * original run's style. Cutting per run instead would mean advancing the cursor per run, and a run's
 * width has to be measured with its style applied: bold glyphs are a pixel wider each. Measuring the
 * plain string, as this used to, makes every following run start too far left, which is what made a
 * multi-run (e.g. per-letter coloured) prefix render as overlapping, garbled glyphs.
 *
 * <p>Because {@code Font#width} returns whole pixels, the two outer pieces also land on integer
 * positions, so nothing is nudged by rounding either.
 *
 * <h2>Composition</h2>
 * Word transforms are pushed around the whole name in the spec's nesting order (shake, flip, skew,
 * grow, outermost first), pivoting on the centre of the name span rather than on individual glyphs
 * — that is the difference between the word leaning/growing as one label and each letter wobbling
 * on its own. Per glyph, inside that, come the letter-scope vertical offset, the letter colour and
 * the shimmer pass.
 *
 * <h2>Layering per glyph</h2>
 * Halos and offset copies go down first and the fill last: glow halos, the chromatic split copies,
 * the outline stroke, then the fill. That ordering is what makes the outline read as a stroke
 * sitting around a coloured fill instead of covering it. Shimmer and Fade contribute no layer at
 * all — they only multiply the colour.
 *
 * <h2>Fill priority</h2>
 * An active colour style wins, then Shimmer, then Outline's transparent fill (hollow text), then
 * Glowing's solid white fill, then the colour the glyph already had. That last step stands in for
 * the spec's flat {@code #e8e8ee} base: in game the "base" is whatever colour the server gave the
 * text, so a rank colour survives when no colour style is selected. Shimmer ranks above Outline and
 * Glowing because it multiplies a fill rather than supplying one — see {@link #resolveFill}.
 *
 * <h2>Where it applies</h2>
 * Mixins intercept the vanilla name-drawing calls in the tab list, the scoreboard sidebar, chat and
 * the in-world nametag. Chat is the one case that cannot re-lay-out the line, because vanilla has
 * already drawn it and link/hover detection is computed against that draw; there the name is
 * overlaid in place, so only the effects that leave the glyph where it is are applied — no motion.
 * Fade works there too now that it darkens rather than fades, since a dark glyph still covers the
 * one underneath.
 */
public final class NametagStyleRenderer {
	/**
	 * 1em, in font pixels. The spec expresses every distance in em (= the rendered text height) so
	 * one set of numbers drives both a crisp GUI label and a nametag shrunk by distance above a
	 * player's head; {@code lineHeight} is that height for Minecraft's font.
	 */
	private static float em(Font font) {
		return font.lineHeight;
	}

	// A blurred drop-shadow is approximated by redrawing the glyph around a ring. Two 8-point rings
	// offset by 22.5 degrees from each other cover the circle far more evenly than one does, and the
	// per-sample alpha stays low because the samples overlap and accumulate toward the glyph edge.
	private static final float[] RING_A_X = {1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f, 0f, 0.7071f};
	private static final float[] RING_A_Y = {0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f};
	private static final float[] RING_B_X = {0.9239f, 0.3827f, -0.3827f, -0.9239f, -0.9239f, -0.3827f, 0.3827f, 0.9239f};
	private static final float[] RING_B_Y = {0.3827f, 0.9239f, 0.9239f, 0.3827f, -0.3827f, -0.9239f, -0.9239f, -0.3827f};
	private static final float HALO_OUTER_SAMPLE_ALPHA = 0.22f;
	private static final float HALO_INNER_SAMPLE_ALPHA = 0.30f;

	// The outline is a stroke, not a bleed, so it gets its own 8 neighbours — the four sides and the
	// four corners, each displaced by exactly the stroke width on each axis — rather than the halo
	// rings' unit circle. Diagonal samples at radius*0.7071 would land between pixels, and the
	// font's sub-pixel sampling turns that into a soft, glowing edge, which is exactly what an
	// outline must not look like. Whole steps per axis keep every copy grid-aligned (see
	// {@link #guiStrokeWidth}), so the stroke stops dead at its edge.
	private static final float[] OUTLINE_X = {-1f, 1f, 0f, 0f, -1f, 1f, -1f, 1f};
	private static final float[] OUTLINE_Y = {0f, 0f, -1f, 1f, -1f, -1f, 1f, 1f};

	/** Below this a halo or offset copy is invisible anyway, so the draws are skipped entirely. */
	private static final float ALPHA_EPSILON = 0.004f;
	/** Halo radius, in font pixels, under which the second sample ring stops adding anything. */
	private static final float TIGHT_HALO_RADIUS = 1.4f;

	private NametagStyleRenderer() {
	}

	public static boolean hasAnyEffect() {
		return !ModConfigManager.CONFIG.nametagEffects.isEmpty();
	}

	/** Whether {@code text} contains the local player's raw username, i.e. it should be styled. */
	public static boolean matchesLocalName(String text) {
		return text != null && !text.isEmpty() && localPlayerName() != null && text.contains(localPlayerName());
	}

	public static Set<NametagEffect> activeEffects() {
		return ModConfigManager.CONFIG.nametagEffects;
	}

	private static String localPlayerName() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		String name = player.getGameProfile().name();
		return name.isEmpty() ? null : name;
	}

	/**
	 * One glyph of the styled span.
	 *
	 * @param sequence the glyph with its server style but <em>without</em> its colour, so the
	 *                 effect colour passed at draw time is the one that applies — a style's own
	 *                 colour would otherwise override it and the colour effects would do nothing
	 * @param baseColor the colour the glyph would have had if no colour style were active
	 * @param advance   the glyph's width with its style applied, which is a pixel wider than the
	 *                  plain glyph when the run is bold
	 */
	private record Glyph(FormattedCharSequence sequence, int baseColor, float advance) {
	}

	/** The component cut into the part before the name, the name itself, and the part after. */
	private record SplitName(MutableComponent before, List<Glyph> glyphs, MutableComponent after, float nameWidth) {
	}

	/**
	 * Which families of effect a call site can apply. Chat can only use the ones that leave the
	 * glyph where it is, because it draws on top of a line vanilla has already rendered.
	 */
	private record SpanOptions(boolean wordTransform, boolean letterMotion) {
		static final SpanOptions FULL = new SpanOptions(true, true);
		static final SpanOptions STATIONARY = new SpanOptions(false, false);
	}

	/** Draws one glyph at a local offset; the two implementations are the 2D and 3D back ends. */
	@FunctionalInterface
	private interface GlyphSink {
		void draw(Glyph glyph, float x, float y, int argb, boolean dropShadow);
	}

	// ---------------------------------------------------------------- splitting ----

	/**
	 * Cuts the component at the name. Each outer piece keeps every original run as its own styled
	 * sibling, so drawing it is one call that lays out exactly as vanilla would have.
	 */
	private static SplitName split(Font font, List<NametagTextUtil.TextRun> runs, int matchStart, int matchEnd, int fallbackColor) {
		MutableComponent before = null;
		MutableComponent after = null;
		List<Glyph> glyphs = new ArrayList<>();
		float nameWidth = 0;
		int pos = 0;

		for (NametagTextUtil.TextRun run : runs) {
			String text = run.text();
			int runStart = pos;
			int runEnd = pos + text.length();
			pos = runEnd;

			if (runEnd <= matchStart) {
				before = append(before, text, run.style());
				continue;
			}
			if (runStart >= matchEnd) {
				after = append(after, text, run.style());
				continue;
			}

			int overlapStart = Math.max(matchStart, runStart);
			int overlapEnd = Math.min(matchEnd, runEnd);
			if (runStart < overlapStart) {
				before = append(before, text.substring(0, overlapStart - runStart), run.style());
			}

			String matched = text.substring(overlapStart - runStart, overlapEnd - runStart);
			int base = NametagTextUtil.styleColor(run.style(), fallbackColor);
			Style uncoloured = run.style().withColor((TextColor) null);
			for (int k = 0; k < matched.length(); k++) {
				Glyph glyph = glyph(font, String.valueOf(matched.charAt(k)), uncoloured, base);
				glyphs.add(glyph);
				nameWidth += glyph.advance();
			}

			if (runEnd > overlapEnd) {
				after = append(after, text.substring(overlapEnd - runStart), run.style());
			}
		}

		return new SplitName(before, glyphs, after, nameWidth);
	}

	private static Glyph glyph(Font font, String ch, Style uncoloured, int baseColor) {
		FormattedCharSequence sequence = Component.literal(ch).setStyle(uncoloured).getVisualOrderText();
		return new Glyph(sequence, baseColor, font.width(sequence));
	}

	private static MutableComponent append(MutableComponent target, String text, Style style) {
		if (text.isEmpty()) {
			return target;
		}
		Component piece = Component.literal(text).setStyle(style);
		// An empty parent carries Style.EMPTY, so each sibling keeps exactly the style it was sent
		// with rather than inheriting anything from the wrapper.
		return target == null ? Component.empty().append(piece) : target.append(piece);
	}

	// ---- Plain-string rendering: used for the style picker screen's own labels/preview, where ----
	// ---- the whole string is meant to be styled and there's no surrounding text to preserve.  ----

	public static void renderName2D(GuiGraphics graphics, Font font, String text, float x, float y, int fallbackColor, boolean dropShadow, Set<NametagEffect> effects) {
		List<Glyph> glyphs = new ArrayList<>(text.length());
		for (int i = 0; i < text.length(); i++) {
			glyphs.add(glyph(font, String.valueOf(text.charAt(i)), Style.EMPTY, fallbackColor));
		}
		drawSpan2D(graphics, font, glyphs, x, y, effects, NametagEffectEngine.nowSeconds(), 1f, dropShadow, SpanOptions.FULL);
	}

	// ---- Component rendering: used by the tab list/scoreboard/nametag mixins. ----

	public static void renderComponent2D(GuiGraphics graphics, Font font, Component component, float x, float y, int fallbackColor, boolean dropShadow) {
		String name = localPlayerName();
		List<NametagTextUtil.TextRun> runs = NametagTextUtil.runs(component);
		String full = concatRuns(runs);
		int matchStart = name != null ? full.indexOf(name) : -1;
		if (matchStart < 0) {
			graphics.drawString(font, component, Math.round(x), Math.round(y), fallbackColor, dropShadow);
			return;
		}

		SplitName split = split(font, runs, matchStart, matchStart + name.length(), fallbackColor);
		float cursor = x;
		if (split.before() != null) {
			graphics.drawString(font, split.before(), Math.round(cursor), Math.round(y), fallbackColor, dropShadow);
			cursor += font.width(split.before());
		}
		if (!split.glyphs().isEmpty()) {
			drawSpan2D(graphics, font, split.glyphs(), cursor, y, activeEffects(), NametagEffectEngine.nowSeconds(), 1f, dropShadow, SpanOptions.FULL);
			cursor += split.nameWidth();
		}
		if (split.after() != null) {
			graphics.drawString(font, split.after(), Math.round(cursor), Math.round(y), fallbackColor, dropShadow);
		}
	}

	private static String concatRuns(List<NametagTextUtil.TextRun> runs) {
		StringBuilder builder = new StringBuilder();
		for (NametagTextUtil.TextRun run : runs) {
			builder.append(run.text());
		}
		return builder.toString();
	}

	/**
	 * Draws only the local player's name where it occurs inside an already-wrapped chat line, on
	 * top of the line vanilla already rendered underneath (so link/hover interactions on the rest
	 * of the line, computed against the untouched vanilla text, keep working normally).
	 */
	public static void overlayChatLine(GuiGraphics graphics, Font font, FormattedCharSequence sequence, float y, float opacity, boolean dropShadow) {
		String name = localPlayerName();
		if (name == null) {
			return;
		}
		float lineAlpha = Math.max(0f, Math.min(1f, opacity));
		List<NametagTextUtil.CharSample> samples = NametagTextUtil.flatten(sequence);
		Set<NametagEffect> effects = activeEffects();
		double now = NametagEffectEngine.nowSeconds();
		float cursor = 0;
		int n = samples.size();
		int i = 0;

		// Anything that isn't the name is skipped entirely — not redrawn — since it's already on
		// screen from vanilla's own pass; the cursor still has to advance across it with the style
		// applied, or the name would be drawn in the wrong place.
		while (i < n) {
			int matchLen = NametagTextUtil.matchLenAt(samples, i, name);
			if (matchLen == 0) {
				NametagTextUtil.CharSample sample = samples.get(i);
				cursor += font.width(Component.literal(String.valueOf(sample.ch())).setStyle(sample.style()).getVisualOrderText());
				i++;
				continue;
			}

			List<Glyph> glyphs = new ArrayList<>(matchLen);
			float spanStartX = cursor;
			for (int k = 0; k < matchLen; k++) {
				NametagTextUtil.CharSample sample = samples.get(i + k);
				int base = NametagTextUtil.styleColor(sample.style(), 0xFFFFFFFF);
				Glyph glyph = glyph(font, String.valueOf(sample.ch()), sample.style().withColor((TextColor) null), base);
				glyphs.add(glyph);
				cursor += glyph.advance();
			}
			drawSpan2D(graphics, font, glyphs, spanStartX, y, effects, now, lineAlpha, dropShadow, SpanOptions.STATIONARY);
			i += matchLen;
		}
	}

	// ---------------------------------------------------------------- 2D span ----

	/**
	 * Draws a styled span in the GUI by submitting glyph quads straight into the current render
	 * layer, instead of going through {@code GuiGraphics.drawString} once per copy.
	 *
	 * <p>This is what keeps the effects stable. {@code drawString} submits a text element, and the
	 * render state pushes any element whose bounds intersect an existing one onto a new layer; a
	 * styled glyph is drawn dozens of times over itself, so that path built a layer tree hundreds
	 * deep every frame and — because element bounds are whole-pixel rectangles while the effect
	 * offsets are not — re-decided the intersections, and therefore the draw order, from frame to
	 * frame. That was the flicker. Here every copy lands in one layer in exactly the order it is
	 * emitted, and the order never changes. A node keeps its glyphs in a separate list from its
	 * elements and draws them after, so the text still sits above any background in that layer.
	 *
	 * <p>It is also the higher-quality path: {@link Font#prepareText} takes float coordinates, so a
	 * copy sits at its true sub-pixel position rather than at an integer one nudged by a matrix, and
	 * the whole span shares a single pose snapshot instead of pushing and popping per copy.
	 */
	private static void drawSpan2D(
		GuiGraphics graphics, Font font, List<Glyph> glyphs, float startX, float y,
		Set<NametagEffect> effects, double now, float spanAlpha, boolean dropShadow, SpanOptions options
	) {
		float unit = em(font);
		Matrix3x2fStack pose = graphics.pose();
		WordTransform word = options.wordTransform()
			? NametagEffectEngine.wordTransform(effects, now)
			: WordTransform.IDENTITY;

		// One immutable snapshot of the full transform, shared by every quad of the span. The
		// per-copy offset rides in prepareText's float x/y, so no copy needs a matrix of its own.
		pose.pushMatrix();
		if (!word.isIdentity()) {
			applyWordTransform2D(pose, word, startX + spanWidth(glyphs) / 2f, y + unit / 2f, unit);
		}
		Matrix3x2f spanPose = new Matrix3x2f(pose);
		pose.popMatrix();

		GuiRenderState renderState = ((GuiRenderStateAccess) graphics).hugosmpenhanced$renderState();
		Font.GlyphVisitor visitor = new Font.GlyphVisitor() {
			@Override
			public void acceptGlyph(TextRenderable.Styled glyph) {
				renderState.submitGlyphToCurrentLayer(new GlyphRenderState(spanPose, glyph, null));
			}

			@Override
			public void acceptEffect(TextRenderable effect) {
				renderState.submitGlyphToCurrentLayer(new GlyphRenderState(spanPose, effect, null));
			}
		};

		GlyphSink sink = (glyph, gx, gy, argb, shadow) -> {
			if ((argb >>> 24) == 0) {
				return;
			}
			font.prepareText(glyph.sequence(), gx, gy, argb, shadow, false, 0).visit(visitor);
		};
		drawSpanGlyphs(glyphs, startX, y, unit, guiStrokeWidth(unit), effects, now, spanAlpha, dropShadow, options, sink);
	}

	/** Applies the word transform around {@code (pivotX, pivotY)}, outermost effect first. */
	private static void applyWordTransform2D(Matrix3x2fStack pose, WordTransform word, float pivotX, float pivotY, float unit) {
		pose.translate(pivotX + word.shakeDxEm() * unit, pivotY + word.shakeDyEm() * unit);
		if (word.shakeRotationRad() != 0f) {
			pose.rotate(word.shakeRotationRad());
		}
		if (word.flipScaleY() != 1f) {
			pose.scale(1f, word.flipScaleY());
		}
		if (word.skewShearX() != 0f) {
			pose.shearX(word.skewShearX());
		}
		if (word.growScale() != 1f) {
			pose.scale(word.growScale(), word.growScale());
		}
		pose.translate(-pivotX, -pivotY);
	}

	// ---------------------------------------------------------------- 3D span ----

	/** The in-world nametag, cut the same three ways as {@link #renderComponent2D}. */
	public static void renderComponent3D(
		Font font, Component component, float x, float y, boolean dropShadow, Matrix4f pose, MultiBufferSource buffer,
		Font.DisplayMode mode, int backgroundColor, int lightCoords, int fallbackColor
	) {
		String name = localPlayerName();
		List<NametagTextUtil.TextRun> runs = NametagTextUtil.runs(component);
		String full = concatRuns(runs);
		int matchStart = name != null ? full.indexOf(name) : -1;
		if (matchStart < 0) {
			font.drawInBatch(component, x, y, fallbackColor, dropShadow, pose, buffer, mode, backgroundColor, lightCoords);
			return;
		}

		SplitName split = split(font, runs, matchStart, matchStart + name.length(), fallbackColor);
		float cursor = x;
		if (split.before() != null) {
			font.drawInBatch(split.before(), cursor, y, fallbackColor, dropShadow, pose, buffer, mode, backgroundColor, lightCoords);
			cursor += font.width(split.before());
		}
		if (!split.glyphs().isEmpty()) {
			drawSpan3D(font, split.glyphs(), cursor, y, dropShadow, pose, buffer, mode, backgroundColor, lightCoords);
			cursor += split.nameWidth();
		}
		if (split.after() != null) {
			font.drawInBatch(split.after(), cursor, y, fallbackColor, dropShadow, pose, buffer, mode, backgroundColor, lightCoords);
		}
	}

	private static void drawSpan3D(
		Font font, List<Glyph> glyphs, float startX, float y, boolean dropShadow,
		Matrix4f pose, MultiBufferSource buffer, Font.DisplayMode mode, int backgroundColor, int lightCoords
	) {
		Set<NametagEffect> effects = activeEffects();
		double now = NametagEffectEngine.nowSeconds();
		float unit = em(font);
		WordTransform word = NametagEffectEngine.wordTransform(effects, now);
		Matrix4f wordPose = word.isIdentity()
			? pose
			: wordPose3D(pose, word, startX + spanWidth(glyphs) / 2f, y + unit / 2f, unit);

		GlyphSink sink = (glyph, gx, gy, argb, shadow) -> {
			if ((argb >>> 24) == 0) {
				return;
			}
			font.drawInBatch(glyph.sequence(), gx, gy, argb, shadow, wordPose, buffer, mode, backgroundColor, lightCoords);
		};
		// No pixel grid to snap to out here: the nametag carries its own world scale and is shrunk
		// further by distance, so the spec's hairline is used as-is rather than rounded to a whole
		// font pixel, which at this size would read as a thick border.
		float strokeWidth = NametagEffectEngine.OUTLINE_WIDTH_EM * unit;
		drawSpanGlyphs(glyphs, startX, y, unit, strokeWidth, effects, now, 1f, dropShadow, SpanOptions.FULL, sink);
	}

	private static Matrix4f wordPose3D(Matrix4f pose, WordTransform word, float pivotX, float pivotY, float unit) {
		Matrix4f out = new Matrix4f(pose);
		out.translate(pivotX + word.shakeDxEm() * unit, pivotY + word.shakeDyEm() * unit, 0f);
		if (word.shakeRotationRad() != 0f) {
			out.rotateZ(word.shakeRotationRad());
		}
		if (word.flipScaleY() != 1f) {
			out.scale(1f, word.flipScaleY(), 1f);
		}
		if (word.skewShearX() != 0f) {
			// True x-shear (x' = x + skew * y) via the matrix's raw column-1/row-0 element, matching
			// Matrix3x2fStack#shearX's behavior in the 2D renderer exactly instead of approximating.
			out.mul(new Matrix4f().m10(word.skewShearX()));
		}
		if (word.growScale() != 1f) {
			out.scale(word.growScale(), word.growScale(), 1f);
		}
		out.translate(-pivotX, -pivotY, 0f);
		return out;
	}

	// ---------------------------------------------------------------- shared per-glyph pass ----

	/**
	 * The renderer-agnostic half: walks the span applying every letter-scope effect and emitting the
	 * halo/split/stroke/fill layers through {@code sink}. The word transform is already in place on
	 * whatever matrix {@code sink} draws into.
	 */
	private static void drawSpanGlyphs(
		List<Glyph> glyphs, float startX, float y, float unit, float strokeWidth,
		Set<NametagEffect> effects, double now, float spanAlpha, boolean dropShadow,
		SpanOptions options, GlyphSink sink
	) {
		int n = glyphs.size();
		NametagEffect colorEffect = NametagEffectEngine.colorEffect(effects);
		boolean outline = effects.contains(NametagEffect.OUTLINE);
		Glow glow = NametagEffectEngine.glow(effects, now);
		Chromatic chromatic = NametagEffectEngine.chromatic(effects, now);
		// Fade darkens rather than fades, so it multiplies every colour the span emits — halos and
		// stroke included, otherwise they would keep glowing around a word that has gone black.
		float brightness = NametagEffectEngine.wordBrightness(effects, now);

		float cursor = startX;
		for (int i = 0; i < n; i++) {
			Glyph glyph = glyphs.get(i);
			float gx = cursor;
			float gy = y + (options.letterMotion() ? NametagEffectEngine.letterOffsetYEm(effects, i, n, now) * unit : 0f);

			Shimmer shimmer = NametagEffectEngine.shimmer(effects, i, n, now);
			int fill = NametagEffectEngine.scaleRgb(
				NametagEffectEngine.applyShimmer(
					resolveFill(colorEffect, outline, glow != null, shimmer != null, glyph.baseColor(), i, n, now), shimmer
				),
				brightness
			);

			if (glow != null) {
				drawHalo(sink, glyph, gx, gy, glow.outerRadiusEm() * unit, Glow.OUTER_COLOR, glow.outerAlpha() * spanAlpha, brightness);
				drawHalo(sink, glyph, gx, gy, glow.innerRadiusEm() * unit, Glow.INNER_COLOR, glow.innerAlpha() * spanAlpha, brightness);
			}
			if (chromatic != null) {
				float cxOffset = chromatic.xEm() * unit;
				float cyOffset = chromatic.yEm() * unit;
				int splitAlpha = Math.round(Chromatic.ALPHA * spanAlpha * 255f);
				if (splitAlpha > 0) {
					sink.draw(glyph, gx - cxOffset, gy - cyOffset,
						NametagEffectEngine.scaleRgb((splitAlpha << 24) | Chromatic.COLOR_A, brightness), false);
					sink.draw(glyph, gx + cxOffset, gy + cyOffset,
						NametagEffectEngine.scaleRgb((splitAlpha << 24) | Chromatic.COLOR_B, brightness), false);
				}
			}
			if (outline) {
				int stroke = NametagEffectEngine.scaleRgb(
					NametagEffectEngine.scaleAlpha(NametagEffectEngine.OUTLINE_COLOR, spanAlpha), brightness
				);
				drawRing(sink, glyph, gx, gy, strokeWidth, OUTLINE_X, OUTLINE_Y, stroke);
			}

			int fillArgb = NametagEffectEngine.scaleAlpha(fill, spanAlpha);
			if ((fillArgb >>> 24) != 0) {
				sink.draw(glyph, gx, gy, fillArgb, dropShadow);
			}

			cursor += glyph.advance();
		}
	}

	/**
	 * Fill priority, highest first: an active colour style, then Shimmer, then Outline's
	 * transparent fill (hollow text), then Glowing's solid white fill, then the colour the glyph
	 * already had.
	 *
	 * <p>Shimmer sits above Outline and Glowing because it is a multiplier, not a colour: it needs
	 * a fill to act on. Under the spec's order it was silently cancelled by both — Outline left no
	 * fill to brighten, and Glowing's flat white was already at the ceiling, so the highlight pass
	 * had nowhere to go. Ranking it higher means the glyph keeps a fill for the sweep to travel
	 * across, and Outline's stroke and Glowing's halo still draw around it as usual.
	 */
	private static int resolveFill(
		NametagEffect colorEffect, boolean outline, boolean glowing, boolean shimmering,
		int baseColor, int index, int total, double now
	) {
		if (colorEffect != null) {
			return NametagEffectEngine.letterColor(colorEffect, index, total, now, baseColor);
		}
		if (shimmering) {
			return baseColor;
		}
		if (outline) {
			return 0;
		}
		if (glowing) {
			return NametagEffectEngine.GLOW_FILL_COLOR;
		}
		return baseColor;
	}

	/** Approximates one blurred drop-shadow halo of the given radius. */
	private static void drawHalo(GlyphSink sink, Glyph glyph, float x, float y, float radius, int rgb, float alpha, float brightness) {
		if (alpha <= ALPHA_EPSILON || radius <= 0.05f) {
			return;
		}
		int tinted = NametagEffectEngine.scaleRgb(0xFF000000 | rgb, brightness) & 0xFFFFFF;
		if (radius < TIGHT_HALO_RADIUS) {
			// Under about a pixel the two rings land on the same texels, so the second one only
			// darkens the first instead of filling in the circle — one ring at their combined alpha
			// is both cheaper and closer to the real bleed.
			drawRing(sink, glyph, x, y, radius, RING_A_X, RING_A_Y,
				NametagEffectEngine.withAlpha(tinted, alpha * (HALO_OUTER_SAMPLE_ALPHA + HALO_INNER_SAMPLE_ALPHA)));
			return;
		}
		drawRing(sink, glyph, x, y, radius, RING_A_X, RING_A_Y, NametagEffectEngine.withAlpha(tinted, alpha * HALO_OUTER_SAMPLE_ALPHA));
		drawRing(sink, glyph, x, y, radius * 0.5f, RING_B_X, RING_B_Y, NametagEffectEngine.withAlpha(tinted, alpha * HALO_INNER_SAMPLE_ALPHA));
	}

	private static void drawRing(GlyphSink sink, Glyph glyph, float x, float y, float radius, float[] unitX, float[] unitY, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		for (int i = 0; i < unitX.length; i++) {
			sink.draw(glyph, x + unitX[i] * radius, y + unitY[i] * radius, argb, false);
		}
	}

	/**
	 * The outline stroke width in font pixels, for the GUI.
	 *
	 * <p>The spec's 0.055em is a hairline — about half a font pixel at Minecraft's 9px text height —
	 * so rounding it up to a whole font pixel would draw it at twice its intended weight. Instead it
	 * is rounded to a whole <em>screen</em> pixel and converted back: at GUI scale 2 that lands on
	 * exactly 0.055em, and at every scale it is both the thinnest stroke the display can show and
	 * still perfectly texel-aligned, so the edge stays hard.
	 *
	 * <p>A word-scope scale (Growing) on top of this is not accounted for, so the stroke keeps its
	 * screen weight while the text grows rather than scaling with it — which is how a stroke should
	 * behave anyway.
	 */
	private static float guiStrokeWidth(float unit) {
		double guiScale = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
		double screenPixels = Math.max(1.0, Math.round(NametagEffectEngine.OUTLINE_WIDTH_EM * unit * guiScale));
		return (float) (screenPixels / guiScale);
	}

	private static float spanWidth(List<Glyph> glyphs) {
		float width = 0;
		for (Glyph glyph : glyphs) {
			width += glyph.advance();
		}
		return width;
	}
}
