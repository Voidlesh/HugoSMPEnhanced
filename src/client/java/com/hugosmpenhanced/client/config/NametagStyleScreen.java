package com.hugosmpenhanced.client.config;

import java.util.EnumSet;
import java.util.Set;

import com.hugosmpenhanced.client.feature.nametag.NametagEffect;
import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom grid screen for picking nametag style effects, modeled after NoRisk Client's
 * "Nametag Style" screen: every button live-previews its own effect in its label, and any number
 * of them can be toggled on at once. Per {@code docs/SPEC.md} the buttons are laid out in their
 * spec group order — motion, then colour, then overlay — and only the colour group is mutually
 * exclusive, since a glyph can carry exactly one colour.
 */
public class NametagStyleScreen extends Screen {
	private static final int COLUMNS = 4;
	private static final int CELL_WIDTH = 130;
	private static final int CELL_HEIGHT = 32;
	private static final int CELL_GAP = 8;
	private static final int GRID_TOP = 74;

	private final Screen parent;

	protected NametagStyleScreen(Screen parent) {
		super(Component.literal("Nametag-Stil"));
		this.parent = parent;
	}

	public static NametagStyleScreen build(Screen parent) {
		return new NametagStyleScreen(parent);
	}

	@Override
	protected void init() {
		int gridWidth = COLUMNS * CELL_WIDTH + (COLUMNS - 1) * CELL_GAP;
		int startX = this.width / 2 - gridWidth / 2;
		NametagEffect[] effects = NametagEffect.values();

		for (int i = 0; i < effects.length; i++) {
			int col = i % COLUMNS;
			int row = i / COLUMNS;
			int x = startX + col * (CELL_WIDTH + CELL_GAP);
			int y = GRID_TOP + row * (CELL_HEIGHT + CELL_GAP);
			this.addRenderableWidget(new EffectButton(x, y, CELL_WIDTH, CELL_HEIGHT, effects[i]));
		}

		int bottomY = GRID_TOP + ((effects.length - 1) / COLUMNS + 1) * (CELL_HEIGHT + CELL_GAP) + 8;
		this.addRenderableWidget(
			Button.builder(Component.literal("Zurück"), button -> this.onClose()).bounds(this.width / 2 - 75, bottomY, 150, 20).build()
		);
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.drawCenteredString(this.font, "— NAMETAG STYLE —", this.width / 2, 16, 0xFFAAAAAA);

		String previewName = previewName();
		Set<NametagEffect> active = NametagStyleRenderer.activeEffects();
		int previewWidth = this.font.width(previewName);
		if (active.isEmpty()) {
			graphics.drawCenteredString(this.font, previewName, this.width / 2, 32, 0xFFFFFFFF);
		} else {
			NametagStyleRenderer.renderName2D(
				graphics, this.font, previewName, this.width / 2f - previewWidth / 2f, 32, 0xFFFFFFFF, true, active
			);
		}
		graphics.drawCenteredString(this.font, "Custom", this.width / 2, 52, 0xFF888888);

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private String previewName() {
		var player = Minecraft.getInstance().player;
		return player != null ? player.getGameProfile().name() : "Voidlesh";
	}

	private static void toggleEffect(NametagEffect effect) {
		Set<NametagEffect> active = ModConfigManager.CONFIG.nametagEffects;
		if (active.contains(effect)) {
			active.remove(effect);
		} else {
			// Only the colour group is mutually exclusive — a glyph can physically carry one
			// colour. Motion and overlay effects each own a property nothing else writes, so any
			// subset of them stacks without conflicting.
			if (effect.group() == NametagEffect.Group.COLOR) {
				active.removeIf(other -> other.group() == NametagEffect.Group.COLOR);
			}
			active.add(effect);
		}
		ModConfigManager.save();
	}

	private final class EffectButton extends Button {
		private final NametagEffect effect;

		private EffectButton(int x, int y, int width, int height, NametagEffect effect) {
			super(x, y, width, height, Component.literal(effect.displayName()), button -> toggleEffect(effect), DEFAULT_NARRATION);
			this.effect = effect;
		}

		@Override
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			this.renderDefaultSprite(graphics);

			boolean active = ModConfigManager.CONFIG.nametagEffects.contains(this.effect);
			if (active) {
				graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xFFFFFFFF);
			}

			String label = this.effect.displayName();
			int labelWidth = NametagStyleScreen.this.font.width(label);
			float x = this.getX() + (this.getWidth() - labelWidth) / 2f;
			float y = this.getY() + (this.getHeight() - 9) / 2f;
			NametagStyleRenderer.renderName2D(
				graphics, NametagStyleScreen.this.font, label, x, y, active ? 0xFFFFFFFF : 0xFF9A9A9A, true, EnumSet.of(this.effect)
			);
		}
	}
}
