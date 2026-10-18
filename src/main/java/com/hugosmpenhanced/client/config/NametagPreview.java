package com.hugosmpenhanced.client.config;

import java.util.Set;

import com.hugosmpenhanced.client.feature.nametag.NametagEffect;
import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametag;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametagPosition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The live nametag preview shared by the style and second-nametag screens: the own name with the
 * active style effects on its nametag plate, plus the second line where it would sit in the world,
 * enlarged over a sky backdrop so the plate, the effects and the background toggle are all visible.
 *
 * <p>The layout is the in-world one from {@code SecondNametagRenderMixin}, in nametag pixels with
 * the name's glyph top at y = 0 and every plate reaching one pixel above its glyphs and down to
 * y = 9, just drawn flat.
 */
final class NametagPreview {
	/** Vanilla's default nametag plate: black at the "text background opacity" of 25 %. */
	static final int NAMETAG_BACKGROUND = 0x40000000;
	/** Space between two stacked nametag lines, in nametag pixels (vanilla's 1.15 line pitch). */
	private static final float LINE_GAP = 9.0F * 1.15F - 10.0F;
	/** Largest preview scale, in GUI pixels per nametag pixel. */
	private static final float MAX_SCALE = 2.0F;

	private NametagPreview() {
	}

	/**
	 * @param showSecond whether to show the second line at all; the caller decides, since the
	 *                   second-nametag editor shows it even while the feature is switched off
	 */
	static void render(GuiGraphics graphics, Font font, int left, int top, int width, int height, boolean showSecond) {
		ModConfig config = ModConfigManager.CONFIG;
		graphics.fillGradient(left, top, left + width, top + height, 0xFF6F9EE8, 0xFFB9D3F5);
		graphics.enableScissor(left, top, left + width, top + height);

		boolean second = showSecond && !config.secondNametagText.isBlank();
		float secondScale = (float) config.secondNametagScale;
		boolean above = config.secondNametagPosition == SecondNametagPosition.ABOVE;

		float secondY = above ? -1.0F - LINE_GAP - 9.0F * secondScale : 9.0F + LINE_GAP + secondScale;
		float blockTop = second && above ? secondY - secondScale : -1.0F;
		float blockBottom = second && !above ? secondY + 9.0F * secondScale : 9.0F;
		// As large as the panel allows, leaving room for motion effects to move the name around;
		// whole and half steps only, so the pixel font stays crisp.
		float fit = (height - 12) / (blockBottom - blockTop);
		float scale = Math.max(1.0F, Math.min(MAX_SCALE, (float) Math.floor(fit * 2) / 2.0F));
		float originY = top + height / 2.0F - (blockTop + blockBottom) / 2.0F * scale;
		float centerX = left + width / 2.0F;

		renderName(graphics, font, centerX, originY, scale);
		if (second) {
			int background = config.secondNametagBackground ? NAMETAG_BACKGROUND : 0;
			renderLine(graphics, font, centerX, originY + secondY * scale, scale * secondScale, SecondNametag.text(), background);
		}

		graphics.disableScissor();
	}

	private static void renderName(GuiGraphics graphics, Font font, float centerX, float y, float scale) {
		String name = previewName();
		Set<NametagEffect> effects = NametagStyleRenderer.activeEffects();
		if (effects.isEmpty()) {
			renderLine(graphics, font, centerX, y, scale, Component.literal(name), NAMETAG_BACKGROUND);
			return;
		}

		// The plate stays put while the effects move the name on it, as it does in the world.
		int width = font.width(name);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - width * scale / 2.0F, y);
		graphics.pose().scale(scale, scale);
		graphics.fill(-1, -1, width + 1, 9, NAMETAG_BACKGROUND);
		NametagStyleRenderer.renderName2D(graphics, font, name, 0, 0, 0xFFFFFFFF, false, effects);
		graphics.pose().popMatrix();
	}

	private static void renderLine(GuiGraphics graphics, Font font, float centerX, float y, float scale, Component text, int background) {
		int width = font.width(text);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - width * scale / 2.0F, y);
		graphics.pose().scale(scale, scale);
		if (background != 0) {
			graphics.fill(-1, -1, width + 1, 9, background);
		}
		graphics.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
		graphics.pose().popMatrix();
	}

	static String previewName() {
		var player = Minecraft.getInstance().player;
		return player != null ? player.getGameProfile().name() : "Voidlesh";
	}
}
