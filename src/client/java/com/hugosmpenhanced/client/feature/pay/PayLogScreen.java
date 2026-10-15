package com.hugosmpenhanced.client.feature.pay;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.gui.PanelScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** {@code /hse zahlungen}: every payment in and out, filterable, with totals per player. */
public class PayLogScreen extends PanelScreen {
	private static final int IN = 0xFF5ADC78;
	private static final int OUT = 0xFFFF7070;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");
	private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

	private boolean confirmClear;
	private String note = "";

	public PayLogScreen(Screen parent) {
		super(Component.literal("Zahlungen"), parent, 0xFF5ADC78, 380);
		this.tabs(List.of("Alle", "Erhalten", "Gesendet", "Pro Spieler"), tab -> { });
		this.searchable("Spieler suchen…");
		this.footer("Als CSV speichern", this::export);
		this.footer(() -> this.confirmClear ? "Wirklich leeren?" : "Log leeren", () -> {
			if (this.confirmClear) {
				PayLog.clear();
				this.note = "Log geleert";
			}
			this.confirmClear = !this.confirmClear;
		});
	}

	@Override
	protected String subtitle() {
		return "Alle Zahlungen auf HugoSMP, auch nach einem Neustart";
	}

	@Override
	protected String status() {
		return this.note.isEmpty() ? PayLog.entries().size() + " Einträge" : this.note;
	}

	@Override
	protected int summaryHeight() {
		return 30;
	}

	@Override
	protected void renderSummary(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
		long todayIn = 0;
		long todayOut = 0;
		long weekIn = 0;
		long weekOut = 0;
		LocalDate today = LocalDate.now();
		long weekStart = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		for (PayLog.Entry entry : PayLog.entries()) {
			if (!entry.confirmed()) {
				continue;
			}
			if (entry.time() >= weekStart) {
				if (entry.incoming()) {
					weekIn += entry.amount();
				} else {
					weekOut += entry.amount();
				}
			}
			if (entry.time() >= dayStart) {
				if (entry.incoming()) {
					todayIn += entry.amount();
				} else {
					todayOut += entry.amount();
				}
			}
		}
		int column = width / 3;
		this.figure(graphics, x, y, "Heute", todayIn, todayOut);
		this.figure(graphics, x + column, y, "Letzte 7 Tage", weekIn, weekOut);
		long net = weekIn - weekOut;
		graphics.drawString(this.font, "Saldo 7 Tage", x + 2 * column, y + 2, Theme.MUTED, false);
		graphics.drawString(this.font, (net >= 0 ? "+" : "−") + Money.format(Math.abs(net)), x + 2 * column, y + 13, net >= 0 ? IN : OUT, false);
	}

	private void figure(GuiGraphics graphics, int x, int y, String label, long in, long out) {
		graphics.drawString(this.font, label, x, y + 2, Theme.MUTED, false);
		String received = "+" + Money.format(in);
		graphics.drawString(this.font, received, x, y + 13, IN, false);
		graphics.drawString(this.font, "−" + Money.format(out), x + this.font.width(received) + 6, y + 13, OUT, false);
	}

	@Override
	protected String emptyText() {
		if (!this.query.isEmpty()) {
			return "Keine Zahlung mit \"" + this.query + "\".";
		}
		return PayLog.entries().isEmpty() ? "Noch keine Zahlung. Sobald du auf HugoSMP zahlst oder Geld bekommst, steht es hier."
			: "Nichts in diesem Filter.";
	}

	@Override
	protected List<? extends Row> rows() {
		List<PayLog.Entry> all = PayLog.entries();
		if (this.tab == 3) {
			return this.perPlayer(all);
		}
		List<Row> rows = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0; i--) {
			PayLog.Entry entry = all.get(i);
			if (this.tab == 1 && !entry.incoming() || this.tab == 2 && entry.incoming() || !this.matches(entry.name())) {
				continue;
			}
			rows.add(new PaymentRow(entry));
		}
		return rows;
	}

	private List<Row> perPlayer(List<PayLog.Entry> all) {
		Map<String, long[]> sums = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (PayLog.Entry entry : all) {
			if (!entry.confirmed() || !this.matches(entry.name())) {
				continue;
			}
			String key = entry.name().toLowerCase(Locale.ROOT);
			names.put(key, entry.name());
			long[] sum = sums.computeIfAbsent(key, k -> new long[4]);
			sum[entry.incoming() ? 0 : 1] += entry.amount();
			sum[2]++;
			sum[3] = Math.max(sum[3], entry.time());
		}
		List<Row> rows = new ArrayList<>();
		sums.entrySet().stream().sorted(Comparator.comparingLong(e -> -(e.getValue()[0] + e.getValue()[1])))
			.forEach(e -> rows.add(new PlayerRow(names.get(e.getKey()), e.getValue())));
		return rows;
	}

	private static int nameColor(String name) {
		if (HugoGuard.scammer(name) != null) {
			return PayWarning.NAME_RED;
		}
		return HugoGuard.trusted(name) != null ? 0xFF7CE38B : Theme.TEXT;
	}

	private static List<Component> guardLines(String name) {
		List<Component> lines = new ArrayList<>();
		HugoGuard.Entry scammer = HugoGuard.scammer(name);
		if (scammer != null) {
			lines.add(Component.literal("⚠ Bei HugoGuard als Scammer gemeldet (" + scammer.reports() + ")").withColor(PayWarning.NAME_RED));
		} else if (HugoGuard.trusted(name) != null) {
			lines.add(Component.literal("✔ Bei HugoGuard vertrauenswürdig").withColor(0xFF7CE38B));
		}
		return lines;
	}

	private final class PaymentRow implements Row {
		private final PayLog.Entry entry;

		PaymentRow(PayLog.Entry entry) {
			this.entry = entry;
		}

		@Override
		public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean hovered) {
			int ty = y + (height - 8) / 2;
			boolean sure = this.entry.confirmed();
			graphics.drawString(font, TIME.format(Instant.ofEpochMilli(this.entry.time()).atZone(ZoneId.systemDefault())), x + 4, ty, Theme.MUTED, false);
			int arrowX = x + 72;
			graphics.drawString(font, this.entry.incoming() ? "⬇" : "⬆", arrowX, ty, sure ? (this.entry.incoming() ? IN : OUT) : Theme.FAINT, false);
			String verb = this.entry.incoming() ? "von " : "an ";
			graphics.drawString(font, verb, arrowX + 12, ty, Theme.MUTED, false);
			int nameX = arrowX + 12 + font.width(verb);
			clipped(graphics, font, this.entry.name(), nameX, ty, width - (nameX - x) - 110, sure ? nameColor(this.entry.name()) : Theme.FAINT);
			String amount = (this.entry.incoming() ? "+" : "−") + Money.format(this.entry.amount()) + (sure ? "" : " ?");
			right(graphics, font, amount, x + width - 6, ty, sure ? (this.entry.incoming() ? IN : OUT) : Theme.FAINT);
		}

		@Override
		public List<Component> tooltip() {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal((this.entry.incoming() ? "Von " : "An ") + this.entry.name()).withColor(Theme.WHITE));
			lines.add(Component.literal(FULL.format(Instant.ofEpochMilli(this.entry.time()).atZone(ZoneId.systemDefault()))).withColor(Theme.MUTED));
			if (!this.entry.confirmed()) {
				lines.add(Component.literal("Keine Bestätigung vom Server erkannt.").withColor(Theme.GOLD));
				lines.add(Component.literal("Nur der getippte Befehl ist bekannt.").withColor(Theme.MUTED));
			}
			lines.addAll(guardLines(this.entry.name()));
			return lines;
		}
	}

	private static final class PlayerRow implements Row {
		private final String name;
		private final long[] sum;

		PlayerRow(String name, long[] sum) {
			this.name = name;
			this.sum = sum;
		}

		@Override
		public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean hovered) {
			int ty = y + (height - 8) / 2;
			clipped(graphics, font, this.name, x + 4, ty, 110, nameColor(this.name));
			graphics.drawString(font, this.sum[2] + "×", x + 120, ty, Theme.MUTED, false);
			graphics.drawString(font, "+" + Money.format(this.sum[0]), x + 150, ty, IN, false);
			graphics.drawString(font, "−" + Money.format(this.sum[1]), x + 150 + (width - 150) / 3, ty, OUT, false);
			long net = this.sum[0] - this.sum[1];
			right(graphics, font, (net >= 0 ? "+" : "−") + Money.format(Math.abs(net)), x + width - 6, ty, net >= 0 ? IN : OUT);
		}

		@Override
		public List<Component> tooltip() {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(this.name).withColor(Theme.WHITE));
			lines.add(Component.literal(this.sum[2] + " Zahlungen, zuletzt " + PayLog.ago(this.sum[3])).withColor(Theme.MUTED));
			lines.addAll(guardLines(this.name));
			return lines;
		}
	}

	private void export() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced").resolve("zahlungen.csv");
		StringBuilder csv = new StringBuilder("Zeit;Richtung;Spieler;Betrag;Bestätigt\n");
		for (PayLog.Entry entry : PayLog.entries()) {
			csv.append(FULL.format(Instant.ofEpochMilli(entry.time()).atZone(ZoneId.systemDefault()))).append(';')
				.append(entry.incoming() ? "erhalten" : "gesendet").append(';').append(entry.name()).append(';')
				.append(entry.amount()).append(';').append(entry.confirmed() ? "ja" : "nein").append('\n');
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, csv, StandardCharsets.UTF_8);
			this.note = "Gespeichert: zahlungen.csv";
			Util.getPlatform().openFile(file.getParent().toFile());
		} catch (Exception e) {
			this.note = "Speichern fehlgeschlagen";
		}
	}
}
