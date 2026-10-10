package com.hugosmpenhanced.client.config;

import java.util.Set;

import com.hugosmpenhanced.client.feature.nametag.NametagEffect;
import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametag;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametagPosition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Editor for the second nametag with a live preview: every change to the text, position, size or
 * background is written to the config immediately and shows up in the preview on the next frame.
 * The preview reproduces the in-world layout from {@code SecondNametagRenderMixin} in nametag
 * pixels, just drawn flat and enlarged.
 */
public class SecondNametagScreen extends Screen {
	private static final int CONTROL_WIDTH = 300;
	private static final int HALF_WIDTH = (CONTROL_WIDTH - 4) / 2;
	private static final int PREVIEW_TOP = 32;
	private static final int PREVIEW_HEIGHT = 96;
	/** GUI pixels per nametag pixel in the preview. */
	private static final float PREVIEW_SCALE = 2.0F;
	/** Space between two stacked nametag lines, in nametag pixels (vanilla's 1.15 line pitch). */
	private static final float LINE_GAP = 9.0F * 1.15F - 10.0F;
	/** Vanilla's default nametag background: black at the "text background opacity" of 25 %. */
	private static final int NAMETAG_BACKGROUND = 0x40000000;

	private final Screen parent;

	protected SecondNametagScreen(Screen parent) {
		super(Component.literal("Zweiter Nametag"));
		this.parent = parent;
	}

	public static SecondNametagScreen build(Screen parent) {
		return new SecondNametagScreen(parent);
	}

	@Override
	protected void init() {
		ModConfig config = ModConfigManager.CONFIG;
		int left = this.width / 2 - CONTROL_WIDTH / 2;
		int right = left + HALF_WIDTH + 4;
		int y = PREVIEW_TOP + PREVIEW_HEIGHT + 8;

		EditBox text = new EditBox(this.font, left, y, CONTROL_WIDTH, 20, Component.literal("Text"));
		text.setMaxLength(256);
		text.setValue(config.secondNametagText);
		text.setHint(Component.literal("z. B. &b&lHugo&f&#FF5555SMP"));
		text.setResponder(value -> config.secondNametagText = value);
		this.addRenderableWidget(text);
		this.setInitialFocus(text);
		y += 36;

		this.addRenderableWidget(CycleButton.onOffBuilder(config.secondNametagEnabled)
			.create(left, y, HALF_WIDTH, 20, Component.literal("Aktiviert"), (button, value) -> config.secondNametagEnabled = value));
		this.addRenderableWidget(CycleButton.onOffBuilder(config.secondNametagBackground)
			.create(right, y, HALF_WIDTH, 20, Component.literal("Hintergrund"), (button, value) -> config.secondNametagBackground = value));
		y += 24;

		this.addRenderableWidget(CycleButton.<SecondNametagPosition>builder(
				value -> Component.literal(value.displayName()), config.secondNametagPosition)
			.withValues(SecondNametagPosition.values())
			.create(left, y, HALF_WIDTH, 20, Component.literal("Position"), (button, value) -> config.secondNametagPosition = value));
		this.addRenderableWidget(new SizeSlider(right, y, HALF_WIDTH, 20));
		y += 28;

		this.addRenderableWidget(
			Button.builder(Component.literal("Fertig"), button -> this.onClose()).bounds(this.width / 2 - 75, y, 150, 20).build()
		);
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.drawCenteredString(this.font, "— ZWEITER NAMETAG —", this.width / 2, 16, 0xFFAAAAAA);
		renderPreview(graphics);

		int legendY = PREVIEW_TOP + PREVIEW_HEIGHT + 8 + 23;
		graphics.drawCenteredString(this.font, "&0-&f Farbe · &#RRGGBB Hex · &l &o &n &m &k Format · &r Reset",
			this.width / 2, legendY, 0xFF888888);

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/** Draws the own name plus the second line over a sky backdrop, so the background toggle is visible. */
	private void renderPreview(GuiGraphics graphics) {
		ModConfig config = ModConfigManager.CONFIG;
		int left = this.width / 2 - CONTROL_WIDTH / 2;
		graphics.fillGradient(left, PREVIEW_TOP, left + CONTROL_WIDTH, PREVIEW_TOP + PREVIEW_HEIGHT, 0xFF6F9EE8, 0xFFB9D3F5);

		boolean showSecond = !config.secondNametagText.isBlank();
		float scale = (float) config.secondNametagScale;
		boolean above = config.secondNametagPosition == SecondNametagPosition.ABOVE;

		// Layout in nametag pixels, with the name's glyph top at y = 0 and backgrounds reaching one
		// pixel above the glyphs and down to y = 9, exactly like the in-world nametag.
		float secondY = above ? -1.0F - LINE_GAP - 9.0F * scale : 9.0F + LINE_GAP + scale;
		float blockTop = showSecond && above ? secondY - scale : -1.0F;
		float blockBottom = showSecond && !above ? secondY + 9.0F * scale : 9.0F;
		float originY = PREVIEW_TOP + PREVIEW_HEIGHT / 2.0F - (blockTop + blockBottom) / 2.0F * PREVIEW_SCALE;
		float centerX = this.width / 2.0F;

		renderName(graphics, centerX, originY);
		if (showSecond) {
			Component text = SecondNametag.text();
			int background = config.secondNametagBackground ? NAMETAG_BACKGROUND : 0;
			renderLine(graphics, centerX, originY + secondY * PREVIEW_SCALE, PREVIEW_SCALE * scale, text, background);
		} else {
			graphics.drawCenteredString(this.font, "Gib unten einen Text ein", this.width / 2,
				PREVIEW_TOP + PREVIEW_HEIGHT - 14, 0xFF30406A);
		}
	}

	private void renderName(GuiGraphics graphics, float centerX, float y) {
		String name = previewName();
		Set<NametagEffect> effects = NametagStyleRenderer.activeEffects();
		if (effects.isEmpty()) {
			renderLine(graphics, centerX, y, PREVIEW_SCALE, Component.literal(name), NAMETAG_BACKGROUND);
			return;
		}

		int width = this.font.width(name);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, y);
		graphics.pose().scale(PREVIEW_SCALE, PREVIEW_SCALE);
		graphics.fill(-width / 2 - 1, -1, width - width / 2 + 1, 9, NAMETAG_BACKGROUND);
		NametagStyleRenderer.renderName2D(graphics, this.font, name, -width / 2.0F, 0, 0xFFFFFFFF, false, effects);
		graphics.pose().popMatrix();
	}

	private void renderLine(GuiGraphics graphics, float centerX, float y, float scale, Component text, int background) {
		int width = this.font.width(text);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - width * scale / 2.0F, y);
		graphics.pose().scale(scale, scale);
		if (background != 0) {
			graphics.fill(-1, -1, width + 1, 9, background);
		}
		graphics.drawString(this.font, text, 0, 0, 0xFFFFFFFF, false);
		graphics.pose().popMatrix();
	}

	private String previewName() {
		var player = Minecraft.getInstance().player;
		return player != null ? player.getGameProfile().name() : "Voidlesh";
	}

	/** Size from 50 % to 200 % in 5 % steps. */
	private static final class SizeSlider extends AbstractSliderButton {
		private static final double MIN = 0.5;
		private static final double MAX = 2.0;
		private static final double STEP = 0.05;

		private SizeSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), (ModConfigManager.CONFIG.secondNametagScale - MIN) / (MAX - MIN));
			this.updateMessage();
		}

		private double scale() {
			return Math.round((MIN + this.value * (MAX - MIN)) / STEP) * STEP;
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal("Größe: " + Math.round(this.scale() * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			ModConfigManager.CONFIG.secondNametagScale = this.scale();
		}
	}
}
