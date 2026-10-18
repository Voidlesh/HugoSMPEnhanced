package com.hugosmpenhanced.client.feature.money;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hugosmpenhanced.client.feature.pay.PayLog;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.gui.PanelScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** {@code /hse geld}: balance over time and where the money came from and went to. */
public class MoneyScreen extends PanelScreen {
	private static final int IN = 0xFF5ADC78;
	private static final int OUT = 0xFFFF7070;
	private static final int LINE = 0xFFFFC83D;
	private static final int GRAPH_H = 62;

	private boolean confirmClear;

	public MoneyScreen(Screen parent) {
		super(Component.literal("Geld-Tracker"), parent, 0xFFFFC83D, 400);
		this.tabs(List.of("Sitzung", "Heute", "7 Tage", "30 Tage"), tab -> { });
		this.footer("Sitzung neu", MoneyTracker::resetSession);
		this.footer(() -> this.confirmClear ? "Wirklich löschen?" : "Verlauf löschen", () -> {
			if (this.confirmClear) {
				MoneyTracker.clearHistory();
			}
			this.confirmClear = !this.confirmClear;
		});
	}

	@Override
	protected String subtitle() {
		return "Kontostand aus dem Scoreboard, Quellen aus Zahlungen und Menüs";
	}

	@Override
	protected String status() {
		if (!MoneyTracker.active()) {
			return "Nur auf HugoSMP";
		}
		return MoneyTracker.balance() < 0 ? "Kein Kontostand im Scoreboard" : "Kontostand " + Money.compact(MoneyTracker.balance());
	}

	@Override
	protected int statusColor() {
		return MoneyTracker.balance() < 0 ? Theme.RED_TEXT : Theme.GOLD;
	}

	private MoneyTracker.Book book() {
		return switch (this.tab) {
			case 1 -> MoneyTracker.days(1);
			case 2 -> MoneyTracker.days(7);
			case 3 -> MoneyTracker.days(30);
			default -> MoneyTracker.session();
		};
	}

	private long since() {
		long now = System.currentTimeMillis();
		return switch (this.tab) {
			case 1 -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
			case 2 -> now - 7L * 86_400_000L;
			case 3 -> now - 30L * 86_400_000L;
			default -> now - Math.max(MoneyTracker.sessionMillis(), 60_000);
		};
	}

	@Override
	protected int summaryHeight() {
		return GRAPH_H + 8;
	}

	@Override
	protected void renderSummary(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
		MoneyTracker.Book book = this.book();
		int figures = 120;
		int graphWidth = width - figures - 8;
		this.graph(graphics, x, y, graphWidth, GRAPH_H, mouseX, mouseY);
		int fx = x + graphWidth + 10;
		long in = book.totalIncome();
		long out = book.totalSpent();
		this.figure(graphics, fx, y, "Einnahmen", "+" + Money.compact(in), IN);
		this.figure(graphics, fx, y + 16, "Ausgaben", "−" + Money.compact(out), OUT);
		long net = in - out;
		this.figure(graphics, fx, y + 32, "Netto", MoneyHud.sign(net), net >= 0 ? IN : OUT);
		if (this.tab == 0) {
			long perHour = MoneyTracker.perHour();
			this.figure(graphics, fx, y + 48, "Pro Stunde", perHour == 0 ? "–" : MoneyHud.sign(perHour), Theme.TEXT_DIM);
		} else {
			long days = this.tab == 1 ? 1 : this.tab == 2 ? 7 : 30;
			this.figure(graphics, fx, y + 48, "Pro Tag", MoneyHud.sign(net / days), Theme.TEXT_DIM);
		}
	}

	private void figure(GuiGraphics graphics, int x, int y, String label, String value, int color) {
		graphics.drawString(this.font, label, x, y + 3, Theme.MUTED, false);
		right(graphics, this.font, value, x + 118, y + 3, color);
	}

	/** Balance over the chosen range as a line, with the value under the mouse. */
	private void graph(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
		graphics.fill(x, y, x + width, y + height, 0x40000000);
		List<long[]> samples = MoneyTracker.samples(this.since());
		if (samples.size() < 2) {
			graphics.drawCenteredString(this.font, "Noch zu wenig Verlauf", x + width / 2, y + height / 2 - 4, Theme.FAINT);
			return;
		}
		long t0 = samples.get(0)[0];
		long t1 = Math.max(t0 + 1, samples.get(samples.size() - 1)[0]);
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long[] sample : samples) {
			min = Math.min(min, sample[1]);
			max = Math.max(max, sample[1]);
		}
		if (max - min < 1_000) {
			max += 500;
			min -= 500;
		}
		int top = y + 10;
		int bottom = y + height - 4;
		int prevX = -1;
		int prevY = -1;
		for (long[] sample : samples) {
			int px = x + 2 + (int) ((sample[0] - t0) * (width - 4) / (t1 - t0));
			int py = bottom - (int) ((sample[1] - min) * (bottom - top) / (max - min));
			if (prevX >= 0) {
				this.segment(graphics, prevX, prevY, px, py);
			}
			prevX = px;
			prevY = py;
		}
		graphics.drawString(this.font, Money.compact(max), x + 3, y + 2, Theme.FAINT, false);
		if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
			long t = t0 + (long) (mouseX - x - 2) * (t1 - t0) / Math.max(1, width - 4);
			long[] best = samples.get(0);
			for (long[] sample : samples) {
				if (Math.abs(sample[0] - t) < Math.abs(best[0] - t)) {
					best = sample;
				}
			}
			graphics.fill(mouseX, y, mouseX + 1, y + height, 0x60FFFFFF);
			String label = Money.compact(best[1]) + "  " + PayLog.ago(best[0]);
			int lx = Math.min(mouseX + 4, x + width - this.font.width(label) - 2);
			graphics.drawString(this.font, label, lx, y + height - 12, Theme.WHITE, true);
		}
	}

	/** A line made of small steps; the GUI has no line primitive. */
	private void segment(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		if (steps == 0) {
			graphics.fill(x0, y0, x0 + 1, y0 + 1, LINE);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x0 + (x1 - x0) * i / steps;
			int py = y0 + (y1 - y0) * i / steps;
			graphics.fill(px, py, px + 1, py + 1, LINE);
		}
	}

	@Override
	protected String emptyText() {
		if (!MoneyTracker.active()) {
			return "Der Geld-Tracker zählt nur auf HugoSMP.";
		}
		return "Noch keine Einnahmen oder Ausgaben in diesem Zeitraum.";
	}

	@Override
	protected List<? extends Row> rows() {
		MoneyTracker.Book book = this.book();
		Set<String> sources = new HashSet<>(book.income.keySet());
		sources.addAll(book.spent.keySet());
		long biggest = 1;
		for (String source : sources) {
			biggest = Math.max(biggest, Math.max(book.income.getOrDefault(source, 0L), book.spent.getOrDefault(source, 0L)));
		}
		long scale = biggest;
		List<SourceRow> rows = new ArrayList<>();
		for (String source : sources) {
			rows.add(new SourceRow(source, book.income.getOrDefault(source, 0L), book.spent.getOrDefault(source, 0L), scale));
		}
		rows.sort((a, b) -> Long.compare(b.in + b.out, a.in + a.out));
		return rows;
	}

	private record SourceRow(String source, long in, long out, long scale) implements Row {
		@Override
		public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean hovered) {
			int ty = y + (height - 8) / 2;
			clipped(graphics, font, this.source, x + 4, ty, 110, Theme.TEXT);
			int barX = x + 120;
			int barWidth = width - 120 - 170;
			if (barWidth > 20) {
				int inW = (int) (barWidth * this.in / this.scale);
				int outW = (int) (barWidth * this.out / this.scale);
				graphics.fill(barX, y + 4, barX + inW, y + 7, IN);
				graphics.fill(barX, y + 9, barX + outW, y + 12, OUT);
			}
			int col = x + width - 170;
			graphics.drawString(font, this.in > 0 ? "+" + Money.compact(this.in) : "", col + 4, ty, IN, false);
			graphics.drawString(font, this.out > 0 ? "−" + Money.compact(this.out) : "", col + 64, ty, OUT, false);
			long net = this.in - this.out;
			right(graphics, font, MoneyHud.sign(net), x + width - 6, ty, net >= 0 ? IN : OUT);
		}

		@Override
		public List<Component> tooltip() {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(this.source).withColor(Theme.WHITE));
			lines.add(Component.literal("Einnahmen " + Money.format(this.in)).withColor(IN));
			lines.add(Component.literal("Ausgaben " + Money.format(this.out)).withColor(OUT));
			String why = switch (this.source) {
				case MoneyTracker.PAY -> "Aus den Zahlungen im Chat, auf den Dollar genau.";
				case MoneyTracker.SELL -> "Verkäufe der AFK-Farm, auf den Dollar genau.";
				case MoneyTracker.OTHER -> "Kontostand hat sich geändert, ohne offenes Menü oder Befehl.";
				case MoneyTracker.OFFLINE -> "Unterschied zum Kontostand beim letzten Mal (z. B. Auktionen).";
				default -> this.source.startsWith("/") ? "Kurz nach diesem Befehl." : "Während dieses Menü offen war.";
			};
			lines.add(Component.literal(why).withColor(Theme.MUTED));
			return lines;
		}
	}
}
