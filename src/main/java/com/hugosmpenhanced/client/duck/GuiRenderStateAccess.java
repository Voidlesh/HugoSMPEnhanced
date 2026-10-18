package com.hugosmpenhanced.client.duck;

import net.minecraft.client.gui.render.state.GuiRenderState;

/**
 * Exposes {@code GuiGraphics.guiRenderState}, which is package-private in vanilla.
 *
 * <p>Needed because {@code GuiGraphics.drawString} is the wrong tool for drawing one glyph many
 * times over itself: every call submits a whole text element, and the render state puts any element
 * whose bounds intersect an existing one into a new layer above it. A styled nametag stacks halos,
 * split copies, a stroke and a fill on the same glyph, so going through {@code drawString} builds a
 * layer tree hundreds deep every frame — and since element bounds are whole-pixel rectangles while
 * the effect offsets are not, the intersection test flips between frames and the draw order along
 * with it, which is what made the text flicker. Submitting the glyph quads straight into one layer
 * avoids the tree entirely.
 */
public interface GuiRenderStateAccess {
	GuiRenderState hugosmpenhanced$renderState();
}
