package com.hugosmpenhanced.client.settings.option;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Several related on/off switches as a row of labels: green = on. */
public class PillsOption extends SettingsOption {
	private static final ModConfig DEFAULTS = new ModConfig();
	private static final int H = 13;
	private static final int GAP = 4;

	private record Pill(String name, Function<ModConfig, Boolean> getter, BiConsumer<ModConfig, Boolean> setter) {
	}

	private final List<Pill> pills = new ArrayList<>();
	private int[] belowOrigin;

	public PillsOption(String label, String description) {
		super(label, description);
	}

	public PillsOption pill(String name, Function<ModConfig, Boolean> getter, BiConsumer<ModConfig, Boolean> setter) {
		this.pills.add(new Pill(name, getter, setter));
		return this;
	}

	@Override
	public boolean matches(String query) {
		if (super.matches(query)) {
			return true;
		}
		for (Pill pill : this.pills) {
			if (pill.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected int controlWidth(Font font) {
		return 0;
	}

	@Override
	protected void renderControl(GuiGraphics graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
	}

	/** Positions of the pills for a given width, as x/y offsets and widths. */
	private List<int[]> layout(Font font, int width) {
		List<int[]> boxes = new ArrayList<>();
		int px = 0;
		int py = 0;
		for (Pill pill : this.pills) {
			int w = font.width(pill.name()) + 10;
			if (px > 0 && px + w > width) {
				px = 0;
				py += H + GAP;
			}
			boxes.add(new int[] {px, py, w});
			px += w + GAP;
		}
		return boxes;
	}

	@Override
	protected int belowHeight(Font font, int width) {
		List<int[]> boxes = this.layout(font, width - PAD * 2);
		return boxes.isEmpty() ? 0 : boxes.get(boxes.size() - 1)[1] + H;
	}

	@Override
	protected void renderBelow(GuiGraphics graphics, Font font, int left, int top, int width, int mouseX, int mouseY) {
		this.belowOrigin = new int[] {left, top};
		List<int[]> boxes = this.layout(font, width);
		for (int i = 0; i < this.pills.size(); i++) {
			Pill pill = this.pills.get(i);
			int[] box = boxes.get(i);
			int bx = left + box[0];
			int by = top + box[1];
			boolean on = pill.getter().apply(ModConfigManager.CONFIG);
			boolean over = this.inside(mouseX, mouseY, bx, by, box[2], H);
			if (on) {
				Theme.panel(graphics, bx, by, box[2], H, Theme.GREEN, over ? Theme.WHITE : Theme.GREEN_EDGE);
			} else {
				Theme.panel(graphics, bx, by, box[2], H, 0xFF161616, over ? 0xFF9A9A9A : Theme.CONTROL_BORDER);
			}
			graphics.drawString(font, pill.name(), bx + 5, by + 3, on ? Theme.WHITE : Theme.MUTED, on);
		}
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		if (this.belowOrigin == null || button != 0) {
			return false;
		}
		Font font = Minecraft.getInstance().font;
		List<int[]> boxes = this.layout(font, this.width - PAD * 2);
		for (int i = 0; i < this.pills.size(); i++) {
			int[] box = boxes.get(i);
			if (this.inside(mouseX, mouseY, this.belowOrigin[0] + box[0], this.belowOrigin[1] + box[1], box[2], H)) {
				Pill pill = this.pills.get(i);
				pill.setter().accept(ModConfigManager.CONFIG, !pill.getter().apply(ModConfigManager.CONFIG));
				changed();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hasDefault() {
		return true;
	}

	@Override
	public boolean isDefault() {
		for (Pill pill : this.pills) {
			if (!pill.getter().apply(ModConfigManager.CONFIG).equals(pill.getter().apply(DEFAULTS))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void reset() {
		for (Pill pill : this.pills) {
			pill.setter().accept(ModConfigManager.CONFIG, pill.getter().apply(DEFAULTS));
		}
	}
}
