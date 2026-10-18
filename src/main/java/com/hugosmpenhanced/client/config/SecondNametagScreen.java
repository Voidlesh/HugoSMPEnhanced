package com.hugosmpenhanced.client.config;

import com.hugosmpenhanced.client.feature.secondnametag.SecondNametagPosition;

import com.hugosmpenhanced.client.gui.StyledScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Editor for the second nametag with a live preview: every change to the text, position, size or
 * background is written to the config immediately and shows up in the preview on the next frame.
 * The preview is the shared {@link NametagPreview}.
 */
public class SecondNametagScreen extends Screen implements StyledScreen {
	private static final int CONTROL_WIDTH = 300;
	private static final int HALF_WIDTH = (CONTROL_WIDTH - 4) / 2;
	private static final int PREVIEW_TOP = 32;
	private static final int PREVIEW_HEIGHT = 96;

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
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.centeredText(this.font, "— ZWEITER NAMETAG —", this.width / 2, 16, 0xFFAAAAAA);
		renderPreview(graphics);

		int legendY = PREVIEW_TOP + PREVIEW_HEIGHT + 8 + 23;
		graphics.centeredText(this.font, "&0-&f Farbe · &#RRGGBB Hex · &l &o &n &m &k Format · &r Reset",
			this.width / 2, legendY, 0xFF888888);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void renderPreview(GuiGraphicsExtractor graphics) {
		int left = this.width / 2 - CONTROL_WIDTH / 2;
		NametagPreview.render(graphics, this.font, left, PREVIEW_TOP, CONTROL_WIDTH, PREVIEW_HEIGHT, true);
		if (ModConfigManager.CONFIG.secondNametagText.isBlank()) {
			graphics.centeredText(this.font, "Gib unten einen Text ein", this.width / 2,
				PREVIEW_TOP + PREVIEW_HEIGHT - 14, 0xFF30406A);
		}
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
