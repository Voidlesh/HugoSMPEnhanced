package com.hugosmpenhanced.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws every {@link HudElement} where the player put it. Elements the player never moved keep
 * their default spot, but slide out of the way of one another, so two defaults never overlap.
 */
public final class HudManager {
	private static final List<HudElement> ELEMENTS = new ArrayList<>();

	/** Screen rectangle of an element in GUI pixels (scaled size). */
	public record Box(int x, int y, int width, int height) {
		boolean overlaps(Box other) {
			return this.x < other.x + other.width && other.x < this.x + this.width
				&& this.y < other.y + other.height && other.y < this.y + this.height;
		}

		public boolean contains(double px, double py) {
			return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
		}
	}

	private HudManager() {
	}

	public static void add(HudElement element) {
		ELEMENTS.add(element);
	}

	public static List<HudElement> elements() {
		return ELEMENTS;
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hugosmpenhanced", "hud"), (graphics, deltaTracker) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.gui.hud.isHidden() || client.getDebugOverlay().showDebugScreen() || client.gui.screen() instanceof HudEditorScreen) {
				return;
			}
			HudMigration.runIfNeeded(graphics.guiWidth(), graphics.guiHeight());
			renderAll(graphics, client.font, false);
		});
	}

	/** The saved position, or the default when the player never moved it. */
	public static HudPos pos(HudElement element) {
		HudPos saved = ModConfigManager.CONFIG.hudPositions.get(element.id());
		return saved != null ? saved : element.defaultPos();
	}

	public static boolean moved(HudElement element) {
		return ModConfigManager.CONFIG.hudPositions.containsKey(element.id());
	}

	public static void setPos(HudElement element, HudPos pos) {
		ModConfigManager.CONFIG.hudPositions.put(element.id(), pos);
	}

	public static void reset(HudElement element) {
		ModConfigManager.CONFIG.hudPositions.remove(element.id());
	}

	/**
	 * Where each shown element lands this frame, in drawing order. With {@code preview} (the editor)
	 * every enabled element counts, with example content where it has nothing to show.
	 */
	public static List<Placed> layout(Font font, int screenWidth, int screenHeight, boolean preview) {
		List<Placed> placed = new ArrayList<>();
		// Moved elements first: they stay exactly where the player put them.
		for (int pass = 0; pass < 2; pass++) {
			for (HudElement element : ELEMENTS) {
				if (moved(element) != (pass == 0) || !element.enabled() || !preview && !element.visible()) {
					continue;
				}
				boolean examples = preview && !element.visible();
				HudPos pos = pos(element);
				float scale = Math.max(0.5f, Math.min(2.5f, pos.scale));
				int width = Math.round(element.width(font, examples) * scale);
				int height = Math.round(element.height(font, examples) * scale);
				if (width <= 0 || height <= 0) {
					continue;
				}
				Box box = new Box(pos.left(screenWidth, width), pos.top(screenHeight, height), width, height);
				if (pass == 1) {
					box = makeRoom(box, placed, screenHeight, pos.y > 0.5);
				}
				placed.add(new Placed(element, box, scale, examples));
			}
		}
		return placed;
	}

	/** One element and where it goes. */
	public record Placed(HudElement element, Box box, float scale, boolean examples) {
	}

	/** Slides a default-placed box down (or up near the bottom) past the boxes already placed. */
	private static Box makeRoom(Box box, List<Placed> placed, int screenHeight, boolean upwards) {
		Box current = box;
		for (int tries = 0; tries < 12; tries++) {
			Box hit = null;
			for (Placed other : placed) {
				if (other.box().overlaps(current)) {
					hit = other.box();
					break;
				}
			}
			if (hit == null) {
				return current;
			}
			int y = upwards ? hit.y() - current.height() - 2 : hit.y() + hit.height() + 2;
			if (y < 0 || y + current.height() > screenHeight) {
				return current;
			}
			current = new Box(current.x(), y, current.width(), current.height());
		}
		return current;
	}

	public static void renderAll(GuiGraphicsExtractor graphics, Font font, boolean preview) {
		for (Placed placed : layout(font, graphics.guiWidth(), graphics.guiHeight(), preview)) {
			draw(graphics, font, placed);
		}
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Placed placed) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(placed.box().x(), placed.box().y());
		graphics.pose().scale(placed.scale(), placed.scale());
		placed.element().render(graphics, font, placed.examples());
		graphics.pose().popMatrix();
	}
}
