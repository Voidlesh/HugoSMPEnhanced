package com.hugosmpenhanced.client.feature.pay;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.calculator.ChatCalculator;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * While typing {@code /pay Name Betrag}: the name turns red when HugoGuard lists the player as a
 * scammer, and a box above the input says so (with the reports and the label HugoGuard shows).
 * Paying a scammer takes Enter twice. For everyone else the box shows who gets how much and the
 * last payment with that player.
 */
public final class PayWarning {
	public static final int NAME_RED = 0xFFFF4040;
	private static final int BOX = 0xF0101010;
	private static final int BOX_RED = 0xF02A0A0A;

	/** What the box shows for the current input. */
	public record Info(PayCommand pay, long amount, HugoGuard.Entry scammer, HugoGuard.Entry trusted, PayLog.Entry last) {
	}

	/** The input that was blocked once; Enter on exactly this input goes through. */
	private static String armed;

	private PayWarning() {
	}

	public static Info info(String input) {
		if (!ModConfigManager.CONFIG.payWarning && !ModConfigManager.CONFIG.payPreview) {
			return null;
		}
		PayCommand pay = PayCommand.parse(input);
		if (pay == null) {
			return null;
		}
		long amount = pay.amount();
		ChatCalculator.Result sum = ModConfigManager.CONFIG.chatCalculatorEnabled ? ChatCalculator.find(input) : null;
		if (sum != null && sum.start() >= pay.nameEnd()) {
			amount = Money.parse(sum.plain());
		}
		HugoGuard.Entry scammer = ModConfigManager.CONFIG.payWarning ? HugoGuard.scammer(pay.name()) : null;
		HugoGuard.Entry trusted = ModConfigManager.CONFIG.hugoGuardTrusted ? HugoGuard.trusted(pay.name()) : null;
		PayLog.Entry last = ModConfigManager.CONFIG.payLogEnabled ? PayLog.last(pay.name()) : null;
		if (scammer == null && !ModConfigManager.CONFIG.payPreview) {
			return null;
		}
		return new Info(pay, amount, scammer, trusted, last);
	}

	/** Recolours the typed scammer name in the formatted input; the rest keeps the command colours. */
	public static FormattedCharSequence colorName(FormattedCharSequence original, String text, int offset, String fullValue) {
		if (!ModConfigManager.CONFIG.payWarning) {
			return original;
		}
		PayCommand pay = PayCommand.parse(fullValue);
		if (pay == null || HugoGuard.scammer(pay.name()) == null) {
			return original;
		}
		FormattedCharSequence base = original != null ? original : FormattedCharSequence.forward(text, Style.EMPTY);
		int start = pay.nameStart();
		int end = pay.nameEnd();
		return sink -> {
			int[] position = {offset};
			return base.accept((index, style, codePoint) -> {
				int at = position[0];
				position[0] += Character.charCount(codePoint);
				Style shown = at >= start && at < end ? style.withColor(NAME_RED).withUnderlined(true) : style;
				return sink.accept(index, shown, codePoint);
			});
		};
	}

	/**
	 * Enter in the chat: true blocks the first Enter on a payment to a scammer. A second Enter on the
	 * same input goes through.
	 */
	public static boolean blockEnter(String input) {
		if (!ModConfigManager.CONFIG.payWarning || !ModConfigManager.CONFIG.payConfirmScammer) {
			return false;
		}
		PayCommand pay = PayCommand.parse(input);
		if (pay == null || pay.amountText() == null || HugoGuard.scammer(pay.name()) == null) {
			return false;
		}
		if (input.equals(armed)) {
			armed = null;
			return false;
		}
		armed = input;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f));
		return true;
	}

	/** Forgets a blocked Enter once the input changes. */
	public static boolean armedFor(String input) {
		if (armed != null && !armed.equals(input)) {
			armed = null;
		}
		return armed != null;
	}

	// ------------------------------------------------------------------ box

	/** Draws the box with its bottom edge at {@code bottom}, right-aligned to {@code right}. */
	public static void render(GuiGraphicsExtractor graphics, Font font, Info info, String input, int right, int bottom, int maxWidth) {
		List<Line> lines = new ArrayList<>();
		String name = info.pay().name();
		boolean scam = info.scammer() != null;
		if (scam) {
			HugoGuard.Entry entry = info.scammer();
			String reports = entry.reports() == 1 ? "1 Meldung" : entry.reports() + " Meldungen";
			lines.add(new Line("⚠ " + entry.username() + " ist als Scammer gemeldet", NAME_RED));
			String label = entry.label() != null && !entry.label().isBlank() ? entry.label() + " · " : "";
			lines.add(new Line(label + reports + " bei HugoGuard", 0xFFFF9A9A));
		}
		if (ModConfigManager.CONFIG.payPreview || scam) {
			StringBuilder to = new StringBuilder();
			if (info.amount() > 0) {
				to.append("Zahlung an ").append(name).append(": ").append(Money.format(info.amount()));
			} else {
				to.append("Zahlung an ").append(name);
			}
			if (!scam && info.trusted() != null) {
				to.append("  ✔ vertrauenswürdig");
			}
			lines.add(new Line(to.toString(), scam ? Theme.TEXT : info.trusted() != null ? 0xFF7CE38B : Theme.TEXT));
			PayLog.Entry last = info.last();
			if (last != null) {
				lines.add(new Line("Zuletzt " + Money.format(last.amount()) + (last.incoming() ? " erhalten, " : " gesendet, ")
					+ PayLog.ago(last.time()), Theme.MUTED));
			}
		}
		if (scam && ModConfigManager.CONFIG.payConfirmScammer && info.pay().amountText() != null) {
			boolean armedNow = armedFor(input);
			lines.add(new Line(armedNow ? "Nochmal Enter, um trotzdem zu zahlen" : "Enter zweimal drücken, um zu zahlen",
				armedNow ? Theme.GOLD : Theme.MUTED));
		}
		if (lines.isEmpty()) {
			return;
		}
		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, font.width(line.text()));
		}
		width = Math.min(width + 10, maxWidth);
		int height = lines.size() * 10 + 5;
		int x = right - width;
		int y = bottom - height;
		graphics.fill(x, y, x + width, y + height, scam ? BOX_RED : BOX);
		int border = scam ? NAME_RED : 0xFF555555;
		graphics.fill(x, y, x + width, y + 1, border);
		graphics.fill(x, y + height - 1, x + width, y + height, border);
		graphics.fill(x, y, x + 1, y + height, border);
		graphics.fill(x + width - 1, y, x + width, y + height, border);
		if (scam) {
			graphics.fill(x + 1, y + 1, x + 3, y + height - 1, NAME_RED);
		}
		int ty = y + 3;
		for (Line line : lines) {
			FormattedText cut = font.substrByWidth(FormattedText.of(line.text()), width - 10);
			graphics.text(font, cut.getString(), x + 6, ty, line.color(), false);
			ty += 10;
		}
	}

	private record Line(String text, int color) {
	}
}
