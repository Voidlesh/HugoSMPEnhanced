package com.hugosmpenhanced.client.hud;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? end
import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

/**
 * Every HUD element of the mod in one place: drag to move (snaps to the edges, the middle and
 * the other elements), mouse wheel to resize, right click to put it back. The chips at the top
 * turn elements on and off; elements with nothing to show right now draw example content.
 */
public class HudEditorScreen extends Screen implements StyledScreen {
	private static final int SNAP = 4;
	private static final int GUIDE = 0xA0FFC83D;
	private static final int CHIP_H = 14;

	private final Screen parent;
	private HudElement dragging;
	private int grabX;
	private int grabY;
	private int guideX = -1;
	private int guideY = -1;
	private final List<Chip> chips = new ArrayList<>();
	private int resetAllX;
	private int doneX;
	private int buttonsY;
	private boolean confirmReset;

	private record Chip(HudElement element, int x, int y, int width) {
	}

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD anordnen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		HudMigration.runIfNeeded(this.width, this.height);
		this.layoutChips();
	}

	private void layoutChips() {
		this.chips.clear();
		List<HudElement> elements = HudManager.elements();
		int total = 0;
		for (HudElement element : elements) {
			total += this.font.width(element.label()) + 18 + 4;
		}
		int x = Math.max(4, (this.width - total + 4) / 2);
		int y = 4;
		for (HudElement element : elements) {
			int width = this.font.width(element.label()) + 18;
			if (x + width > this.width - 4) {
				x = 4;
				y += CHIP_H + 3;
			}
			this.chips.add(new Chip(element, x, y, width));
			x += width + 4;
		}
		this.buttonsY = this.height - 22;
		this.resetAllX = this.width / 2 - 104;
		this.doneX = this.width / 2 + 4;
	}

	private List<HudManager.Placed> layout() {
		return HudManager.layout(this.font, this.width, this.height, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0x60000000);
		// Thin centre lines help lining things up.
		graphics.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x18FFFFFF);
		graphics.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x18FFFFFF);

		HudManager.Placed hovered = null;
		for (HudManager.Placed placed : this.layout()) {
			HudManager.draw(graphics, this.font, placed);
			HudManager.Box box = placed.box();
			boolean over = placed.element() == this.dragging || this.dragging == null && box.contains(mouseX, mouseY);
			if (over) {
				hovered = placed;
			}
			HugoStyle.drawOutline(graphics, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, over ? Theme.GOLD : 0x80FFFFFF);
		}
		if (this.guideX >= 0) {
			graphics.fill(this.guideX, 0, this.guideX + 1, this.height, GUIDE);
		}
		if (this.guideY >= 0) {
			graphics.fill(0, this.guideY, this.width, this.guideY + 1, GUIDE);
		}
		if (hovered != null) {
			this.renderLabel(graphics, hovered);
		}
		this.renderChips(graphics, mouseX, mouseY);
		this.renderButtons(graphics, mouseX, mouseY);
		String help = "Ziehen: verschieben  ·  Mausrad: Größe  ·  Rechtsklick: zurücksetzen";
		graphics.centeredText(this.font, help, this.width / 2, this.buttonsY - 13, 0xFFB0B0B0);
	}

	private void renderLabel(GuiGraphicsExtractor graphics, HudManager.Placed placed) {
		HudManager.Box box = placed.box();
		String text = placed.element().label() + "  " + Math.round(placed.scale() * 100) + " %"
			+ (placed.examples() ? "  (Beispiel)" : "");
		int width = this.font.width(text) + 6;
		int x = Math.max(2, Math.min(box.x(), this.width - width - 2));
		int y = box.y() - 13 >= 0 ? box.y() - 13 : box.y() + box.height() + 2;
		graphics.fill(x, y, x + width, y + 11, 0xE0101010);
		graphics.text(this.font, text, x + 3, y + 2, Theme.GOLD, false);
	}

	private void renderChips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		for (Chip chip : this.chips) {
			boolean on = chip.element().enabled();
			boolean over = mouseX >= chip.x() && mouseX < chip.x() + chip.width() && mouseY >= chip.y() && mouseY < chip.y() + CHIP_H;
			graphics.fill(chip.x(), chip.y(), chip.x() + chip.width(), chip.y() + CHIP_H, over ? 0xF0303030 : 0xF0181818);
			HugoStyle.drawOutline(graphics, chip.x(), chip.y(), chip.width(), CHIP_H, on ? Theme.GREEN : Theme.CHIP_BORDER);
			graphics.fill(chip.x() + 5, chip.y() + 5, chip.x() + 9, chip.y() + 9, on ? Theme.GREEN : Theme.FAINT);
			graphics.text(this.font, chip.element().label(), chip.x() + 13, chip.y() + 3, on ? Theme.TEXT : Theme.MUTED, false);
		}
	}

	private void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.button(graphics, this.resetAllX, this.confirmReset ? "Wirklich alles?" : "Alles zurücksetzen", mouseX, mouseY,
			this.confirmReset ? Theme.RED : Theme.CHIP_BORDER);
		this.button(graphics, this.doneX, "Fertig", mouseX, mouseY, Theme.GOLD);
	}

	private void button(GuiGraphicsExtractor graphics, int x, String text, int mouseX, int mouseY, int border) {
		boolean over = mouseX >= x && mouseX < x + 100 && mouseY >= this.buttonsY && mouseY < this.buttonsY + 18;
		graphics.fill(x, this.buttonsY, x + 100, this.buttonsY + 18, over ? 0xF0303030 : 0xF0181818);
		HugoStyle.drawOutline(graphics, x, this.buttonsY, 100, 18, border);
		graphics.centeredText(this.font, text, x + 50, this.buttonsY + 5, Theme.TEXT);
	}

	private static boolean in(double x, double y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		for (Chip chip : this.chips) {
			if (in(x, y, chip.x(), chip.y(), chip.width(), CHIP_H)) {
				chip.element().setEnabled(!chip.element().enabled());
				ModConfigManager.save();
				return true;
			}
		}
		if (in(x, y, this.doneX, this.buttonsY, 100, 18)) {
			this.onClose();
			return true;
		}
		if (in(x, y, this.resetAllX, this.buttonsY, 100, 18)) {
			if (this.confirmReset) {
				ModConfigManager.CONFIG.hudPositions.clear();
				ModConfigManager.save();
			}
			this.confirmReset = !this.confirmReset;
			return true;
		}
		this.confirmReset = false;
		List<HudManager.Placed> placed = this.layout();
		for (int i = placed.size() - 1; i >= 0; i--) {
			HudManager.Placed one = placed.get(i);
			if (!one.box().contains(x, y)) {
				continue;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				HudManager.reset(one.element());
				ModConfigManager.save();
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				this.dragging = one.element();
				this.grabX = (int) x - one.box().x();
				this.grabY = (int) y - one.box().y();
				// Pin it where it is now, so it stops sliding around other defaults.
				HudPos pos = HudManager.pos(one.element()).copy();
				pos.place(one.box().x(), one.box().y(), this.width, this.height, one.box().width(), one.box().height());
				HudManager.setPos(one.element(), pos);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.dragging == null) {
			return super.mouseDragged(event, dragX, dragY);
		}
		HudManager.Placed self = null;
		List<HudManager.Box> others = new ArrayList<>();
		for (HudManager.Placed placed : this.layout()) {
			if (placed.element() == this.dragging) {
				self = placed;
			} else {
				others.add(placed.box());
			}
		}
		if (self == null) {
			return true;
		}
		int width = self.box().width();
		int height = self.box().height();
		int left = (int) event.x() - this.grabX;
		int top = (int) event.y() - this.grabY;
		boolean free = event.hasShiftDown();
		this.guideX = -1;
		this.guideY = -1;
		if (!free) {
			int[] snappedX = snap(left, width, this.width, others, true);
			int[] snappedY = snap(top, height, this.height, others, false);
			left = snappedX[0];
			this.guideX = snappedX[1];
			top = snappedY[0];
			this.guideY = snappedY[1];
		}
		left = Math.max(HudPos.MARGIN, Math.min(left, this.width - HudPos.MARGIN - width));
		top = Math.max(HudPos.MARGIN, Math.min(top, this.height - HudPos.MARGIN - height));
		HudPos pos = HudManager.pos(this.dragging).copy();
		pos.place(left, top, this.width, this.height, width, height);
		HudManager.setPos(this.dragging, pos);
		return true;
	}

	/**
	 * Snaps one axis to the screen edges, the screen middle and the other elements' edges and
	 * middles. Returns {new start, guide line or -1}.
	 */
	private static int[] snap(int start, int size, int screen, List<HudManager.Box> others, boolean horizontal) {
		List<int[]> targets = new ArrayList<>();
		// {line, offset of that line inside the dragged box}
		targets.add(new int[] {HudPos.MARGIN, 0});
		targets.add(new int[] {screen - HudPos.MARGIN, size});
		targets.add(new int[] {screen / 2, size / 2});
		for (HudManager.Box box : others) {
			int a = horizontal ? box.x() : box.y();
			int length = horizontal ? box.width() : box.height();
			int b = a + length;
			targets.add(new int[] {a, 0});
			targets.add(new int[] {b, size});
			targets.add(new int[] {b + 2, 0});
			targets.add(new int[] {a - 2, size});
			targets.add(new int[] {a, size});
			targets.add(new int[] {b, 0});
			targets.add(new int[] {a + length / 2, size / 2});
		}
		int best = SNAP + 1;
		int[] result = {start, -1};
		for (int[] target : targets) {
			int candidate = target[0] - target[1];
			int distance = Math.abs(candidate - start);
			if (distance < best) {
				best = distance;
				result = new int[] {candidate, target[0]};
			}
		}
		return result;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging != null) {
			this.dragging = null;
			this.guideX = -1;
			this.guideY = -1;
			ModConfigManager.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		List<HudManager.Placed> placed = this.layout();
		for (int i = placed.size() - 1; i >= 0; i--) {
			HudManager.Placed one = placed.get(i);
			if (one.box().contains(mouseX, mouseY)) {
				HudPos pos = HudManager.pos(one.element()).copy();
				pos.scale = Math.round(Math.max(0.5f, Math.min(2.5f, pos.scale + (scrollY > 0 ? 0.1f : -0.1f))) * 10) / 10f;
				HudManager.setPos(one.element(), pos);
				ModConfigManager.save();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// The world stays visible, so the HUD can be placed against the real game view.
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
