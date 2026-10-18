package com.hugosmpenhanced.client.feature.streamer;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;


import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.calculator.ChatCalculator;
import com.hugosmpenhanced.client.settings.Theme;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * After the control bar was closed, opening the chat shows small buttons at the bottom right,
 * stacked right of the hotbar: "Auktion" (running or ended) brings the control bar back,
 * "Giveaway" opens the giveaway tab. Hidden while a command is typed, where the suggestions and
 * the colour preview need that space, and while the chat calculator shows a result there.
 */
public final class StreamerChatButtons {
	private record Chip(int x, int y, int w, String label, int dot, String tooltip, Runnable action) {
	}

	private static final int H = 14;

	private StreamerChatButtons() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}
			long openedAt = System.currentTimeMillis();
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> render(s, graphics, mouseX, mouseY, openedAt));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(s, event));
		});
	}

	private static List<Chip> chips(Screen screen) {
		List<Chip> chips = new ArrayList<>();
		EditBox input = input(screen);
		if (input != null && (input.getValue().startsWith("/")
			|| ModConfigManager.CONFIG.chatCalculatorEnabled && ChatCalculator.find(input.getValue()) != null)) {
			return chips;
		}
		Font font = Minecraft.getInstance().font;
		long now = System.currentTimeMillis();
		int right = screen.width - 4;
		int y = (input != null ? input.getY() : screen.height - 12) - 2 - H - 2;
		DonationAuction.State state = DonationAuction.state();
		if (state != DonationAuction.State.IDLE) {
			boolean ended = state == DonationAuction.State.ENDED;
			String label = ended ? "Auktion vorbei" : "Auktion  " + StreamerOverlay.clock(Math.max(0, DonationAuction.endsAt() - now));
			int dot = ended ? Theme.GOLD : StreamerUi.alpha(0xFFFF5050, 0.55f + 0.45f * (float) Math.sin(now / 250.0));
			int w = font.width(label) + 20;
			chips.add(new Chip(right - w, y, w, label, dot,
				ended ? "Gewinner, TPA und Zurückzahlen" : "Steuerleiste: Zeit, beenden, abbrechen",
				() -> StreamerScreen.open(false)));
			y -= H + 2;
		}
		if (Giveaway.open()) {
			String label = "Giveaway  " + Giveaway.count();
			int w = font.width(label) + 20;
			chips.add(new Chip(right - w, y, w, label, StreamerUi.GREEN, "Teilnehmer ansehen und Glücksrad drehen",
				() -> StreamerScreen.open(true)));
		}
		return chips;
	}

	private static void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, long openedAt) {
		List<Chip> chips = chips(screen);
		if (chips.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		float appear = StreamerUi.easeOut(StreamerUi.progress(openedAt, 180));
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, Math.round((1 - appear) * 6));
		for (Chip chip : chips) {
			boolean over = StreamerUi.inside(mouseX, mouseY, chip.x, chip.y, chip.w, H);
			Theme.panel(graphics, chip.x, chip.y, chip.w, H, over ? 0xF0303030 : 0xE0101010, over ? Theme.GOLD : 0xFF555555);
			graphics.fill(chip.x + 6, chip.y + 5, chip.x + 10, chip.y + 9, chip.dot);
			graphics.text(font, chip.label, chip.x + 14, chip.y + 3, over ? Theme.WHITE : Theme.TEXT, false);
			if (over) {
				graphics.setTooltipForNextFrame(font, Component.literal(chip.tooltip), mouseX, mouseY);
			}
		}
		graphics.pose().popMatrix();
	}

	/** True when a chip took the click. */
	private static boolean click(Screen screen, MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (Chip chip : chips(screen)) {
			if (StreamerUi.inside(event.x(), event.y(), chip.x, chip.y, chip.w, H)) {
				StreamerFeature.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f);
				Minecraft.getInstance().execute(chip.action);
				return true;
			}
		}
		return false;
	}

	private static EditBox input(Screen screen) {
		for (var child : screen.children()) {
			if (child instanceof EditBox box) {
				return box;
			}
		}
		return null;
	}
}
