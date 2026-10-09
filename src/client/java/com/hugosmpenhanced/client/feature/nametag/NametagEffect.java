package com.hugosmpenhanced.client.feature.nametag;

/**
 * One selectable nametag style effect, with the exact timing parameters from {@code docs/SPEC.md}
 * / {@code docs/effects.json} attached to each constant.
 *
 * <p>The model's whole point is that every effect owns <em>exactly one</em> property — a transform,
 * a colour, an opacity, a filter or a stroke — so two effects never write the same value and
 * arbitrary combinations compose without any runtime conflict resolution. That is why only the
 * {@link Group#COLOR} group is mutually exclusive: a glyph can physically only carry one colour.
 *
 * <p>{@link Scope#WORD} effects transform or filter the whole name as one unit; {@link Scope#LETTER}
 * effects run per glyph with a {@link #staggerSeconds()} phase offset, so the effect travels along
 * the word left to right.
 */
public enum NametagEffect {
	// --- motion: any subset, all active at once ---
	SHAKING(Group.MOTION, Scope.WORD, "Shaking", 0.55, 0.0),
	WAVING(Group.MOTION, Scope.LETTER, "Waving", 1.8, 0.13),
	/**
	 * A single short hop that runs along the letters one after another. Deviation from the spec,
	 * which drove opacity here; it now shares translateY with {@link #WAVING} and the two simply
	 * add, since summing translations needs no conflict resolution.
	 */
	ITERATING(Group.MOTION, Scope.LETTER, "Iterating", 1.6, 0.10),
	FLIPPING(Group.MOTION, Scope.WORD, "Flipping", 2.4, 0.0),
	SKEWING(Group.MOTION, Scope.WORD, "Skewing", 1.6, 0.0),
	GROWING(Group.MOTION, Scope.WORD, "Growing", 1.4, 0.0),

	// --- colour: at most one; turning one on turns the others off ---
	GRADIENT(Group.COLOR, Scope.LETTER, "Gradient", 3.2, 0.15),
	RAINBOW(Group.COLOR, Scope.LETTER, "Rainbow", 3.0, 0.18),
	METALLIC(Group.COLOR, Scope.LETTER, "Metallic", 2.6, 0.13),
	FIRE(Group.COLOR, Scope.LETTER, "Fire", 1.4, 0.09),

	// --- overlay: any subset, all active at once ---
	/**
	 * Breathes the whole word down to black and back. Deviation from the spec, which faded opacity:
	 * this darkens the colour instead, so the name stays solid and never shows what is behind it.
	 * Like {@link #SHIMMER} it multiplies the colour, so the two compose without fighting.
	 */
	FADE(Group.OVERLAY, Scope.WORD, "Fade", 1.9, 0.0),
	SHIMMER(Group.OVERLAY, Scope.LETTER, "Shimmer", 2.6, 0.07),
	CHROMATIC(Group.OVERLAY, Scope.WORD, "Chromatic", 1.6, 0.0),
	/** Deliberately static — a stroke, not an animation, hence period 0. */
	OUTLINE(Group.OVERLAY, Scope.WORD, "Outline", 0.0, 0.0),
	GLOWING(Group.OVERLAY, Scope.WORD, "Glowing", 2.2, 0.0);

	private final Group group;
	private final Scope scope;
	private final String displayName;
	private final double periodSeconds;
	private final double staggerSeconds;

	NametagEffect(Group group, Scope scope, String displayName, double periodSeconds, double staggerSeconds) {
		this.group = group;
		this.scope = scope;
		this.displayName = displayName;
		this.periodSeconds = periodSeconds;
		this.staggerSeconds = staggerSeconds;
	}

	public Group group() {
		return group;
	}

	public Scope scope() {
		return scope;
	}

	public String displayName() {
		return displayName;
	}

	/** Length of one loop in seconds, or 0 for a static effect. */
	public double periodSeconds() {
		return periodSeconds;
	}

	/** Per-letter phase step for {@link Scope#LETTER} effects; 0 for word-scope ones. */
	public double staggerSeconds() {
		return staggerSeconds;
	}

	public enum Group {
		MOTION,
		COLOR,
		OVERLAY
	}

	public enum Scope {
		/** The whole nametag moves or is filtered as one unit. */
		WORD,
		/** Each glyph animates individually, phase-offset by {@link #staggerSeconds()}. */
		LETTER
	}
}
