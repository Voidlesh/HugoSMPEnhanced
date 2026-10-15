package com.hugosmpenhanced.client.feature.sell;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;

import com.hugosmpenhanced.client.hud.HudElement;
import com.hugosmpenhanced.client.hud.HudPos;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The AFK farm's status pill: a coloured dot, the state and the session's sales and income. It only
 * shows while the AFK farm runs or is paused, and for a few seconds after it stopped with the reason.
 * While the farm waits with the sell menu filling up, a thin bar under it shows how full the menu is.
 * Placed in the HUD editor.
 */
public final class SellHud implements HudElement {
	public static final SellHud INSTANCE = new SellHud();
	private static final int RUN = 0xFF5ADC78;
	private static final int PAUSE = 0xFFF0BE3C;
	private static final int STOP = 0xFFE65050;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int MUTED = 0xFF9A9A9A;
	private static final int GOLD = 0xFFFFC83D;
	private static final int SEP = 0xFF606060;
	private static final String SEPARATOR = "  ·  ";

	private record Part(String text, int color) {
	}

	private record Content(List<Part> parts, int dot, int fill) {
	}

	private SellHud() {
	}

	@Override
	public String id() {
		return "sell";
	}

	@Override
	public String label() {
		return "AFK-Farm";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.sellHudOn;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.sellHudOn = enabled;
	}

	@Override
	public boolean visible() {
		return content(false) != null;
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(0.5, 0);
	}

	private static Content content(boolean preview) {
		List<Part> parts = new ArrayList<>();
		int dot;
		String stop = SellEngine.recentStop();
		if (SellEngine.afkRunning()) {
			if (SellEngine.state() == SellEngine.State.PAUSED) {
				dot = PAUSE;
				parts.add(new Part("Pausiert", PAUSE));
				parts.add(new Part(SellEngine.pauseReason(), TEXT));
				parts.add(new Part("geht weiter, wenn du 5 s stillstehst", MUTED));
			} else {
				dot = RUN;
				parts.add(new Part("AFK-Farm", 0xFFFFFFFF));
				int sales = SellEngine.sales();
				parts.add(new Part(sales == 1 ? "1 Verkauf" : sales + " Verkäufe", TEXT));
				double income = SellEngine.income();
				if (income >= 0) {
					parts.add(new Part("+" + SellEngine.formatMoney(income), GOLD));
				}
				double perHour = SellEngine.incomePerHour();
				if (perHour >= 0) {
					parts.add(new Part(SellEngine.formatMoney(perHour) + "/h", MUTED));
				}
			}
		} else if (stop != null) {
			dot = STOP;
			parts.add(new Part("Gestoppt", STOP));
			parts.add(new Part(stop, TEXT));
		} else if (preview) {
			dot = RUN;
			parts.add(new Part("AFK-Farm", 0xFFFFFFFF));
			parts.add(new Part("12 Verkäufe", TEXT));
			parts.add(new Part("+$1.240.000", GOLD));
			parts.add(new Part("$410.000/h", MUTED));
		} else {
			return null;
		}
		int fill = SellEngine.afkRunning() && SellEngine.state() == SellEngine.State.WAITING ? SellEngine.fillPercent() : -1;
		return new Content(parts, dot, fill);
	}

	@Override
	public int width(Font font, boolean preview) {
		Content content = content(preview);
		if (content == null) {
			return 0;
		}
		int width = 18 + 6;
		for (int i = 0; i < content.parts().size(); i++) {
			width += font.width(content.parts().get(i).text()) + (i > 0 ? font.width(SEPARATOR) : 0);
		}
		return width;
	}

	@Override
	public int height(Font font, boolean preview) {
		Content content = content(preview);
		return content == null ? 0 : content.fill() >= 0 ? 20 : 15;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, boolean preview) {
		Content content = content(preview);
		if (content == null) {
			return;
		}
		int width = this.width(font, preview);
		int height = this.height(font, preview);
		int x = 0;
		int y = 0;
		graphics.fill(x + 1, y, x + width - 1, y + height, 0xD80E0E0E);
		graphics.fill(x, y + 1, x + width, y + height - 1, 0xD80E0E0E);
		graphics.fill(x + 1, y, x + width - 1, y + 1, 0xFF464646);
		graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, 0xFF464646);
		graphics.fill(x, y + 1, x + 1, y + height - 1, 0xFF464646);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF464646);
		graphics.fill(x + 6, y + 5, x + 10, y + 9, content.dot());
		int tx = x + 15;
		List<Part> parts = content.parts();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				graphics.drawString(font, SEPARATOR, tx, y + 4, SEP, false);
				tx += font.width(SEPARATOR);
			}
			Part part = parts.get(i);
			graphics.drawString(font, part.text(), tx, y + 4, part.color(), true);
			tx += font.width(part.text());
		}
		int fill = content.fill();
		if (fill >= 0) {
			int barLeft = x + 6;
			int barRight = x + width - 6;
			graphics.fill(barLeft, y + 15, barRight, y + 17, 0xFF2D2D2D);
			graphics.fill(barLeft, y + 15, barLeft + (barRight - barLeft) * Math.min(100, fill) / 100, y + 17, GOLD);
			int threshold = barLeft + (barRight - barLeft) * ModConfigManager.CONFIG.sellThreshold / 100;
			graphics.fill(threshold, y + 14, threshold + 1, y + 18, 0xC8FFFFFF);
		}
	}
}
