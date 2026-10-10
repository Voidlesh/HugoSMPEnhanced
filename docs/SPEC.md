# Nametag effects — implementation spec

Nineteen animated styles for a player nametag, plus the rules for how they combine.
`effects.json` and `preview.html` describe the original fifteen effects of version 1
(open the preview in any browser, click the cards to combine styles); where this
document differs from them, this document and the Java code win. See *Version 2* at
the end for what changed and why.

This spec is renderer-agnostic. It describes *what* each effect does to a glyph over
time, not how to draw it, so it maps onto CSS, a Minecraft text renderer, a shader,
or a canvas the same way.

## Core model

Every effect owns **exactly one property**. That is the whole reason arbitrary
combinations work: two effects never write the same value, so nothing has to be
resolved at runtime.

Effects apply at one of two scopes:

- **word** — the whole nametag moves or is filtered as one unit
- **letter** — each glyph is animated individually, with a phase offset so the
  effect travels along the word

Word-scope transforms compose by nesting, outermost first: `shake → flip → skew →
grow`. Letter-scope transforms apply inside that, per glyph. In a renderer without a
DOM, multiply the matrices in that order and apply the per-letter offset last.

## Time and units

`t` runs `0 → 1` across one period. Every curve is built so that the value at `t=1`
equals the value at `t=0`, which means no effect seams at the loop point — the
jitter and flicker curves use only harmonics that complete whole cycles.

All distances are in **em**, where `1em` is the rendered text height. Keeping
distances relative is what lets the same numbers drive a 24px preview card and a
48px title without retuning.

## Stagger (letter-scope effects only)

```
phaseOffsetSeconds(i) = (n - 1 - i) * staggerSeconds
```

with `i` the letter index and `n` the letter count. The offset *advances* the
letter's phase, so letter 0 leads and the effect sweeps **left to right**. In CSS
this is a negative `animation-delay`; elsewhere it is `t = ((elapsed + offset) /
period) mod 1`.

## The effects

| Effect | Group | Scope | Property | Period | Stagger |
|---|---|---|---|---|---|
| Shaking | motion | word | transform | 0.55s | — |
| Waving | motion | letter | transform | 1.8s | 0.13s |
| Iterating | motion | letter | transform | 1.6s | 0.10s |
| Bouncing | motion | letter | transform | 1.25s | 0.07s |
| Swinging | motion | letter | transform | 1.7s | 0.11s |
| Flipping | motion | word | transform | 2.4s | — |
| Skewing | motion | word | transform | 1.6s | — |
| Growing | motion | word | transform | 1.4s | — |
| Gradient | color | field | color | 3.2s | — |
| Rainbow | color | field | color | 3.0s | — |
| Aurora | color | field | color | 7.0s | — |
| Metallic | color | field | color | 2.6s | — |
| Fire | color | field | color | — | — |
| Fade | overlay | word | brightness | 1.9s | — |
| Shimmer | overlay | field | color | 2.6s | — |
| Chromatic | overlay | word | filter | 1.6s | — |
| Glitch | overlay | word | geometry | 1.15s | — |
| Outline | overlay | word | stroke | static | — |
| Glowing | overlay | word | filter | 2.2s | — |

### Motion

**Shaking** — three stacked harmonics, so it reads as nervous jitter rather than a
visible loop.

```
translateX = 0.085em * (0.60*sin(2pi*3t) + 0.30*sin(2pi*5t + 1.7) + 0.20*sin(2pi*7t + 0.6))
translateY = 0.022em * sin(2pi*5t + 2.1)
rotateZ    = 1.5deg  * (0.45*sin(2pi*3t + 0.9) + 0.20*sin(2pi*5t))
```

**Waving** — `translateY = -0.15em * sin(2pi*t)`, staggered, so a shallow wave runs
through the word.

**Iterating** — a bright head travelling along the letters. It drives *opacity*, not
colour, which is what lets it sit on top of any colour style.

```
d       = min(t, 1-t)
opacity = 0.26 + 0.74 * exp(-(d/0.085)^2)
```

**Flipping** — `rotateX` from 0° to 360° across the first 52% of the period, then
holds. Perspective `40em`, easing `cubic-bezier(0.62, 0, 0.38, 1)`. The hold is what
makes it a deliberate flip instead of a constant tumble.

**Skewing** — `skewX = 14deg * sin(2pi*t)`.

**Growing** — `scale = 1.14 + 0.14*sin(2pi*t - pi/2)`, i.e. 1.00 → 1.28 → 1.00.

**Bouncing** — each letter jumps under gravity, scaled about its bottom centre so it
squashes into the ground rather than shrinking towards its middle. Every phase starts
and ends at scale 1, so the cycle has no seams.

```
t in [0, 0.50)    u = t/0.5;  lift = 0.42em * 4u(1-u)
                  stretch = 0.13 * (1-2u)^2 * smoothstep(0, .1, u) * (1 - smoothstep(.9, 1, u))
                  scaleY = 1 + stretch;  scaleX = 1 - 0.55*stretch
t in [0.50, 0.72) w = (t-0.5)/0.22;  squash = sin(pi*w) * (1 - 0.4w)
                  scaleY = 1 - 0.22*squash;  scaleX = 1 + 0.14*squash
t in [0.86, 1)    crouch = sin(pi*(t-0.86)/0.14)
                  scaleY = 1 - 0.08*crouch;  scaleX = 1 + 0.05*crouch
```

**Swinging** — each letter swings like a pendulum hung from the top of its cell. The
small third harmonic makes the turn at each end linger, like a real hanging sign.

```
rotate = 10.5deg * sin(2pi*t) + 1.2deg * sin(6pi*t)
```

### Colour — pick exactly one

A glyph can only carry one colour style, so this group is mutually exclusive:
selecting one clears the others.

The colour styles are **fields** over the name, not a colour per letter. A field is a
function `color(x, v)` of the position inside the styled span, with `x` in font
pixels from its left edge and `v` from 0 at the top of the glyph cell to 1 at its
bottom (8 font pixels). The renderer samples it at every texel corner of every glyph,
so colour runs smoothly through the inside of each letter. Distances are in font
pixels rather than relative to the name's width, so long and short names get the same
stripe density.

Palettes are interpolated in **Oklab** and baked into 1024-entry lookup tables.
Oklab keeps the midpoint of a blend as vivid and as bright as its ends, where an sRGB
blend from violet to pink sags through a duller purple.

`relief(c, v)` is a light top-to-bottom shading used by the flat styles: up to 8%
toward white along the top edge, up to 8% darker along the bottom.

**Gradient** — a cyclic violet → fuchsia → pink → lilac palette, flowing left to
right, one palette cycle per 44px: `relief(palette(x/44 - time/3.2), v)`.

**Rainbow** — Oklch at constant lightness `L = 0.74`, chroma `0.19` (reduced only
where sRGB cannot reach it), one hue cycle per 60px:
`relief(oklch(0.74, 0.19, 360 * (x/60 - time/3.0)), v)`. Holding lightness constant
is what stops the word from throbbing as the hue travels; an HSL sweep makes yellow
and cyan glare and blue go dark.

**Aurora** — slow curtains of teal, green, cyan, indigo, violet and sky blue, driven
by noise and brightest along the top:

```
d = time / 7
n = fbm(x*0.05 - 1.4d, 0.6d + 3.7)
c = palette(1.7n + x/140 - 0.35d)
color = mixWhite(c, 0.20 * (1-v)^2)
```

**Metallic** — chrome is a reflection, not a tint. The base is a vertical ramp:
bright sky at the top, a hard dark horizon just above the middle, darker ground
brightening again toward the bottom edge.

```
v:     0.00     0.22     0.40     0.47     0.60     0.82     1.00
     #fbfcfe  #e2e8f0  #b8c2d1  #4b5567  #6f7a8d  #b4becb  #e6ebf1
```

In the first 55% of each 2.6s period a narrow diagonal highlight sweeps across the
whole span (`smootherstep` easing), then the metal rests:

```
pos   = -12 + (spanWidth + 24) * smootherstep(cycle / 0.55)
d     = x + 6*(0.5 - v) - pos
color = mixWhite(chrome(v), 0.95*exp(-(d/1.3)^2) + 0.30*exp(-(d/4.5)^2))
```

**Fire** — heat rises from the bottom of each glyph, broken up by noise that scrolls
upwards, so flame tongues lick up through the letters:

```
n     = fbm(x*0.23, 1.5v + 1.9*time)
heat  = clamp01(1.08 - 1.12*(1-v) + 0.95*(n - 0.5))
color = heatRamp(heat) * (0.93 + 0.07*fbm(5.3*time, 11.7))
```

`heatRamp`: `#a3141b` 0.00, `#e2361f` 0.28, `#f97316` 0.52, `#fbbf24` 0.74, `#fde68a`
0.90, `#fffbeb` 1.00. The cold end stays a bright red so the name stays readable.

`fbm` is two octaves of value noise with quintic smoothing, in 0..1:
`0.65*noise(x, y) + 0.35*noise(2.03x + 17.1, 2.03y - 4.3)`.

### Overlays — stack freely

**Fade** — `brightness = 0.5 - 0.5*cos(2pi*t)`: the whole word, halos and stroke
included, darkens to black and back. Darkening instead of fading keeps the name solid,
so it never shows what is behind it and also works where it is drawn over vanilla text.

**Shimmer** — a filter on the fill (the active colour field, or the glyph's own colour):
a narrow diagonal band of light with a soft bloom sweeps across the span in the first
60% of each 2.6s period. The name rests slightly dimmed and the band lifts it back to
full brightness and on toward white, the "slide to unlock" look. Only adding light
would do nothing on white text, the usual case, since white cannot get brighter. The
dimming scales with luminance squared, so white gets the full amount while a
saturated colour style keeps its vibrancy.

```
pos   = -10 + (spanWidth + 20) * smootherstep(cycle / 0.6)
d     = x + 4.5*(0.5 - v) - pos
core  = exp(-(d/1.6)^2);  bloom = exp(-(d/5.5)^2)
lift  = min(1, core + 0.55*bloom)
c     = base * (1 - 0.26 * luma(base)^2 * (1 - lift))
color = mixWhite(c, 0.85*core + 0.18*bloom)
```

**Chromatic** — an RGB split that follows the glyph outline (a drop-shadow on the
alpha shape, not a rectangular offset).

```
x = 0.085em * sin(2pi*t)
y = 0.022em * sin(4pi*t)
shadowA = (-x, -y) in #ff3b6b
shadowB = (+x, +y) in #3bd6ff
```

**Glitch** — short digital-glitch bursts at irregular intervals. Each 1.15s window
holds one burst of 7–23% of the window at a random point, half the time followed by a
short echo. During a burst the frame advances in hard steps at 30 per second. A third
of the steps only flash an RGB split (`#ff2e63` / `#08d9d6` at alpha 0.70, offset
0.05–0.12em horizontally); the rest also tear the glyph cell in bands of two to three
pixel rows, each band moved by one (rarely two) whole pixels or left in place. Kept
that small on purpose: the name stays readable through a burst, and whole-pixel steps
keep the torn edges as crisp as the font.

**Outline** — deliberately static: a `0.055em` stroke in `#e8e8ee`. With no colour
style active the fill goes transparent, giving hollow text. Over a colour style the
fill stays coloured and the stroke sits around it.

**Glowing** — two layered halos whose radius and alpha breathe together. Each halo is
approximated by redrawing the glyph on three concentric rings (8, 12 and 16 samples at
a third, two thirds and all of the radius, each rotated against the last), with the
per-sample alpha falling off outwards (0.26, 0.17, 0.10).

```
k     = (1 - cos(2pi*t)) / 2
inner = blur (0.10 + 0.18k)em, rgba(255,255,255, 0.45 + 0.45k)
outer = blur (0.22 + 0.43k)em, rgba(168,150,255, 0.22 + 0.40k)
```

Those colours are for white text. Over a coloured name (a colour style, or a coloured
rank name) the halos take the fill's colour at the glyph centre: the outer halo in that
colour, the inner one lifted 55% toward white. The name then glows like neon instead
of sitting in a lavender cloud. The outer radius peaks at 0.65em (version 1: 0.96em),
because past about half a glyph the ring copies stop blending and show up as ghost
letters.

## Selection rules

- **motion** — any subset, all active at once
- **color** — at most one; turning one on turns the others off
- **overlay** — any subset, all active at once

Fill colour resolves by this priority, highest first:

1. an active colour style (a colour field)
2. Shimmer → the glyph's own colour, which Shimmer then filters
3. Outline → transparent fill
4. Glowing → solid white fill
5. the colour the text already has (in game, whatever the server sent)

Shimmer filters whatever fill wins, so it also applies on top of a colour style.

## Notes for a Minecraft renderer

The colour styles were designed around per-character colouring, so they map directly
onto a renderer that can set a colour per glyph. Drive them from the world clock so
every nametag stays in sync:
`t = ((timeMillis / 1000.0 + phaseOffset) / periodSeconds) mod 1`.

Word-scope transforms belong on the matrix stack around the whole label; letter-scope
ones apply per glyph while advancing the cursor by the glyph's normal width, so the
stagger never changes the text's spacing. Shimmer and Chromatic assume you can tint
or offset an already-rendered glyph; without that, approximate Shimmer by lerping the
glyph colour toward white by `g` and Chromatic by drawing the glyph twice more at
`±(x, y)` in the two colours before the main pass.

## Version 2

What changed against `effects.json` and `preview.html`, and why:

- **Colour styles and Shimmer became fields.** Colouring per letter makes every
  gradient step at letter boundaries. Sampling a field at every texel corner lets it
  run through the letters, gives Metallic a horizon and Fire upward flames, and
  makes the stagger unnecessary.
- **Rendering below the letter.** The renderer cuts each glyph quad into a grid of
  font-pixel cells, interpolating position and texture coordinates across it. With
  nearest-neighbour font textures the cut is invisible, and it gives every texel
  corner its own vertex for the field colour. The same step applies the per-letter
  transforms and Glitch's row tearing.
- **New effects:** Bouncing, Swinging, Aurora and Glitch, modelled on the effect sets
  common in text-animation tools (bounce, swing/pendulum) and game nametag mods
  (glitch, aurora-style gradients).
- **Shimmer** dims the name between highlights, so the band is visible on white text.
- **Fade** darkens instead of fading out, so the name never turns see-through.
- **Glowing** takes the text's colour over coloured names and has a smaller outer halo.
- **Iterating** is a single hop travelling along the letters instead of an opacity
  head, and shares `translateY` with Waving; translations add, so there is no
  conflict.
