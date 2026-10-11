package com.hugosmpenhanced.client.feature.sell;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * The AFK farm's status pill: a coloured dot, the state and the session's sales and income. It only
 * shows while the AFK farm runs or is paused, and for a few seconds after it stopped with the reason.
 * While the farm waits with the sell menu filling up, a thin bar under it shows how full the menu is.
 */
public final class SellHud {
	private static final int RUN = 0xFF5ADC78;
	private static final int PAUSE = 0xFFF0BE3C;
	private static final int STOP = 0xFFE65050;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int MUTED = 0xFF9A9A9A;
	private static final int GOLD = 0xFFFFC83D;
	private static final int SEP = 0xFF606060;

	private record Part(String text, int color) {
	}

	private SellHud() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hugosmpenhanced", "sell_assistant"),
			(graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		SellHudPosition position = ModConfigManager.CONFIG.sellHudPosition;
		if (position == SellHudPosition.OFF || client.options.hideGui || client.getDebugOverlay().showDebugScreen()) {
			return;
		}
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
		} else {
			return;
		}
		draw(graphics, client.font, parts, dot, position);
	}

	private static void draw(GuiGraphics graphics, Font font, List<Part> parts, int dot, SellHudPosition position) {
		String separator = "  ·  ";
		int width = 18 + 6;
		for (int i = 0; i < parts.size(); i++) {
			width += font.width(parts.get(i).text()) + (i > 0 ? font.width(separator) : 0);
		}
		int fill = SellEngine.afkRunning() && SellEngine.state() == SellEngine.State.WAITING ? SellEngine.fillPercent() : -1;
		int height = fill >= 0 ? 20 : 15;
		int screenWidth = graphics.guiWidth();
		int x = switch (position) {
			case TOP_LEFT -> 4;
			case TOP_RIGHT -> screenWidth - width - 4;
			default -> (screenWidth - width) / 2;
		};
		int y = 4;
		graphics.fill(x + 1, y, x + width - 1, y + height, 0xD80E0E0E);
		graphics.fill(x, y + 1, x + width, y + height - 1, 0xD80E0E0E);
		graphics.fill(x + 1, y, x + width - 1, y + 1, 0xFF464646);
		graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, 0xFF464646);
		graphics.fill(x, y + 1, x + 1, y + height - 1, 0xFF464646);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xFF464646);
		graphics.fill(x + 6, y + 5, x + 10, y + 9, dot);
		int tx = x + 15;
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				graphics.drawString(font, separator, tx, y + 4, SEP, false);
				tx += font.width(separator);
			}
			Part part = parts.get(i);
			graphics.drawString(font, part.text(), tx, y + 4, part.color(), true);
			tx += font.width(part.text());
		}
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
