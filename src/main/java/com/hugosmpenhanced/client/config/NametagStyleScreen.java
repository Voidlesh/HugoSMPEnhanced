package com.hugosmpenhanced.client.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.hugosmpenhanced.client.feature.nametag.NametagEffect;
import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametag;

import com.hugosmpenhanced.client.gui.StyledScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Picker for the nametag style effects, modeled after NoRisk Client's "Nametag Style" screen: a large
 * live preview of the own nametag at the top shows every active effect together, the way it looks
 * above the player's head, and below it every button previews its own effect in its label. Any
 * number of effects can be toggled on at once; only the colour group is mutually exclusive, since a
 * glyph can carry exactly one colour. The buttons are grouped as in {@code docs/SPEC.md}: motion,
 * colour, overlay.
 *
 * <p>The grid adapts to the window: as many columns as fit, and the preview gives up height first
 * when the screen is short, so the screen stays usable at large GUI scales.
 */
public class NametagStyleScreen extends Screen implements StyledScreen {
	private static final int MIN_CELL_WIDTH = 62;
	private static final int MAX_CELL_WIDTH = 120;
	private static final int MAX_COLUMNS = 6;
	private static final int CELL_HEIGHT = 20;
	private static final int CELL_GAP = 3;
	private static final int LABEL_HEIGHT = 11;
	private static final int PREVIEW_TOP = 19;
	private static final int MIN_PREVIEW_HEIGHT = 34;
	private static final int MAX_PREVIEW_HEIGHT = 76;

	private final Screen parent;
	private final List<GroupLabel> labels = new ArrayList<>();
	private int previewLeft;
	private int previewWidth;
	private int previewHeight;

	protected NametagStyleScreen(Screen parent) {
		super(Component.literal("Nametag-Stil"));
		this.parent = parent;
	}

	public static NametagStyleScreen build(Screen parent) {
		return new NametagStyleScreen(parent);
	}

	private record GroupLabel(String text, int x, int y) {
	}

	@Override
	protected void init() {
		this.labels.clear();
		int available = Math.min(this.width - 20, MAX_COLUMNS * (MAX_CELL_WIDTH + CELL_GAP) - CELL_GAP);
		int columns = Math.max(3, Math.min(MAX_COLUMNS, (available + CELL_GAP) / (MIN_CELL_WIDTH + CELL_GAP)));
		int cellWidth = Math.min(MAX_CELL_WIDTH, (available - (columns - 1) * CELL_GAP) / columns);
		int gridWidth = columns * cellWidth + (columns - 1) * CELL_GAP;
		int gridLeft = this.width / 2 - gridWidth / 2;

		int gridHeight = 0;
		for (NametagEffect.Group group : NametagEffect.Group.values()) {
			int rows = (count(group) + columns - 1) / columns;
			gridHeight += LABEL_HEIGHT + rows * (CELL_HEIGHT + CELL_GAP);
		}
		int fixed = PREVIEW_TOP + 6 + gridHeight + 3 + 20 + 6;
		this.previewHeight = Math.max(MIN_PREVIEW_HEIGHT, Math.min(MAX_PREVIEW_HEIGHT, this.height - fixed));
		this.previewWidth = gridWidth;
		this.previewLeft = gridLeft;

		int y = PREVIEW_TOP + this.previewHeight + 6;
		for (NametagEffect.Group group : NametagEffect.Group.values()) {
			this.labels.add(new GroupLabel(groupLabel(group), gridLeft + 1, y + 1));
			y += LABEL_HEIGHT;
			int index = 0;
			for (NametagEffect effect : NametagEffect.values()) {
				if (effect.group() != group) {
					continue;
				}
				int x = gridLeft + (index % columns) * (cellWidth + CELL_GAP);
				int rowY = y + (index / columns) * (CELL_HEIGHT + CELL_GAP);
				this.addRenderableWidget(new EffectButton(x, rowY, cellWidth, CELL_HEIGHT, effect));
				index++;
			}
			y += (index + columns - 1) / columns * (CELL_HEIGHT + CELL_GAP);
		}

		this.addRenderableWidget(
			Button.builder(Component.literal("Fertig"), button -> this.onClose()).bounds(this.width / 2 - 75, y + 3, 150, 20).build()
		);
	}

	private static int count(NametagEffect.Group group) {
		int count = 0;
		for (NametagEffect effect : NametagEffect.values()) {
			if (effect.group() == group) {
				count++;
			}
		}
		return count;
	}

	private static String groupLabel(NametagEffect.Group group) {
		return switch (group) {
			case MOTION -> "Bewegung";
			case COLOR -> "Farbe · nur eine gleichzeitig";
			case OVERLAY -> "Overlay";
		};
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.centeredText(this.font, "— NAMETAG STYLE —", this.width / 2, 6, 0xFFAAAAAA);
		NametagPreview.render(graphics, this.font, this.previewLeft, PREVIEW_TOP, this.previewWidth, this.previewHeight,
			SecondNametag.isActive());
		for (GroupLabel label : this.labels) {
			graphics.text(this.font, label.text(), label.x(), label.y(), 0xFF9A9A9A);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			this.extractDefaultSprite(graphics);

			boolean active = ModConfigManager.CONFIG.nametagEffects.contains(this.effect);
			if (active) {
				graphics.outline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xFFFFFFFF);
			}

			String label = this.effect.displayName();
			int labelWidth = NametagStyleScreen.this.font.width(label);
			float x = this.getX() + (this.getWidth() - labelWidth) / 2f;
			float y = this.getY() + (this.getHeight() - 8) / 2f;
			// Each label previews its own effect, clipped to the button so motion effects such as
			// Bouncing or Growing cannot spill onto the neighbouring buttons.
			graphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getRight() - 1, this.getBottom() - 1);
			NametagStyleRenderer.renderName2D(
				graphics, NametagStyleScreen.this.font, label, x, y, active ? 0xFFFFFFFF : 0xFF9A9A9A, true, EnumSet.of(this.effect)
			);
			graphics.disableScissor();
		}
	}
}
