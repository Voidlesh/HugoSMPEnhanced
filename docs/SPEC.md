# Nametag effects — implementation spec

Fifteen animated styles for a player nametag, plus the rules for how they combine.
`effects.json` holds every number in machine-readable form; `preview.html` is a
runnable reference (open it in any browser, click the cards to combine styles).

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
| Iterating | motion | letter | opacity | 1.6s | 0.10s |
| Flipping | motion | word | transform | 2.4s | — |
| Skewing | motion | word | transform | 1.6s | — |
| Growing | motion | word | transform | 1.4s | — |
| Gradient | color | letter | color | 3.2s | 0.15s |
| Rainbow | color | letter | color | 3.0s | 0.18s |
| Metallic | color | letter | color | 2.6s | 0.13s |
| Fire | color | letter | color | 1.4s | 0.09s |
| Fade | overlay | word | opacity | 1.9s | — |
| Shimmer | overlay | letter | filter | 2.6s | 0.07s |
| Chromatic | overlay | word | filter | 1.6s | — |
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

### Colour — pick exactly one

A glyph can only carry one colour, so this group is mutually exclusive: selecting one
clears the others. All four animate the glyph colour per letter with a stagger, the
way a Minecraft plugin colours text per character.

**Gradient** — violet to pink, interpolated in HSL so the midpoint stays vivid
instead of going muddy the way an RGB blend does.

```
k = (1 - cos(2pi*t)) / 2
hsl(258 + 72k, 84 + 4k, 66 - 4k)
```

**Rainbow** — the full hue circle, `hsl(360t, 96%, 63%)`. With the stagger this is a
chroma wave travelling along the word.

**Metallic** — a broad sheen with one hard highlight pass.

```
d     = min(t, 1-t)
sheen = exp(-(d/0.11)^2)
base  = (1 - cos(2pi*t)) / 2
hsl(228, 9 + 6*(1 - sheen), min(44 + 16*base + 40*sheen, 100))
```

**Fire** — stacked harmonics give an organic flicker that still loops cleanly.

```
f = clamp01(0.50 + 0.30*sin(2pi*t) + 0.14*sin(4pi*t + 1.1) + 0.06*sin(6pi*t + 2.3))
hsl(4 + 44f, 100, 46 + 20f)
```

### Overlays — stack freely

**Fade** — `opacity = 0.59 - 0.41*cos(2pi*t)`, ranging 0.18 to 1.00.

**Shimmer** — a specular pass that *multiplies* whatever colour is underneath rather
than replacing it. The dip on either side of the peak is what gives the highlight a
crisp edge, and the falling saturation is what makes it read as light rather than as
a brightness pulse — real highlights run toward white.

```
d          = min(t, 1-t)
g          = exp(-(d/0.045)^2)
dim        = 0.13 * exp(-((d - 0.105)/0.05)^2)
brightness = 1 - dim + 1.65*g
saturate   = 1 - 0.40*g
contrast   = 1 + 0.30*g
whiteGlow  = blur (0.015 + 0.17*g)em at alpha 0.90*g
```

**Chromatic** — an RGB split that follows the glyph outline (a drop-shadow on the
alpha shape, not a rectangular offset).

```
x = 0.085em * sin(2pi*t)
y = 0.022em * sin(4pi*t)
shadowA = (-x, -y) in #ff3b6b
shadowB = (+x, +y) in #3bd6ff
```

**Outline** — deliberately static: a `0.055em` stroke in `#e8e8ee`. With no colour
style active the fill goes transparent, giving hollow text. Over a colour style the
fill stays coloured and the stroke sits around it.

**Glowing** — two layered halos whose radius and alpha breathe together.

```
k     = (1 - cos(2pi*t)) / 2
inner = blur (0.10 + 0.22k)em, rgba(255,255,255, 0.45 + 0.45k)
outer = blur (0.26 + 0.70k)em, rgba(168,150,255, 0.18 + 0.42k)
```

## Selection rules

- **motion** — any subset, all active at once
- **color** — at most one; turning one on turns the others off
- **overlay** — any subset, all active at once

Fill colour resolves by this priority, highest first:

1. an active colour style (animated per letter)
2. Outline → transparent fill
3. Glowing → solid white fill
4. base `#e8e8ee`

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
