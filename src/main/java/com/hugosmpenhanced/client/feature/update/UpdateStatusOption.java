package com.hugosmpenhanced.client.feature.update;

import com.mojang.blaze3d.platform.InputConstants;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The settings row with the running version, what the last check found and a button to act on it. */
public class UpdateStatusOption extends SettingsOption {
	private static final int GAP = 8;

	public UpdateStatusOption() {
		super("Neue Versionen", "");
	}

	@Override
	protected String description() {
		ModUpdates.Release release = ModUpdates.available();
		return switch (ModUpdates.status()) {
			case OFF -> "Suche ist aus";
			case IDLE, CHECKING -> "Prüft gerade auf GitHub…";
			case UP_TO_DATE -> "Aktuell. Prüft beim Start und alle 6 Stunden.";
			case FAILED -> "Prüfen fehlgeschlagen: " + (ModUpdates.lastError() != null ? ModUpdates.lastError() : "unbekannt");
			case AVAILABLE -> UpdateInstaller.isReady(release)
				? "Version " + release.version() + " wird beim Beenden des Spiels installiert"
				: "Version " + release.version() + " ist da";
		};
	}

	private static int statusColor() {
		return switch (ModUpdates.status()) {
			case UP_TO_DATE -> Theme.GREEN;
			case AVAILABLE -> Theme.GOLD;
			case FAILED -> Theme.RED;
			default -> Theme.FAINT;
		};
	}

	private static String buttonText() {
		return ModUpdates.status() == ModUpdates.Status.AVAILABLE ? "Ansehen" : "Jetzt prüfen";
	}

	private static boolean buttonActive() {
		ModUpdates.Status status = ModUpdates.status();
		return status != ModUpdates.Status.OFF && status != ModUpdates.Status.CHECKING;
	}

	private static int buttonWidth(Font font) {
		return Math.max(font.width("Ansehen"), font.width("Jetzt prüfen")) + 14;
	}

	@Override
	protected int controlWidth(Font font) {
		return buttonWidth(font) + GAP;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		int w = buttonWidth(font);
		int left = right - w;
		boolean active = buttonActive();
		boolean over = active && this.inside(mouseX, mouseY, left, centerY - 7, w, 15);
		if (active) {
			Theme.panel(graphics, left, centerY - 7, w, 15, over ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
		} else {
			Theme.panel(graphics, left, centerY - 7, w, 15, 0xFF161616, 0xFF2E2E2E);
		}
		String text = buttonText();
		graphics.text(font, text, left + (w - font.width(text)) / 2, centerY - 3, active ? Theme.WHITE : Theme.FAINT, false);
		// status light left of the button: green current, gold update, red error
		graphics.fill(left - GAP, centerY - 2, left - GAP + 4, centerY + 2, statusColor());
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		Font font = Minecraft.getInstance().font;
		int w = buttonWidth(font);
		int right = this.x + this.width - PAD;
		int centerY = this.y + this.height / 2;
		if (button != InputConstants.MOUSE_BUTTON_LEFT || !buttonActive() || !this.inside(mouseX, mouseY, right - w, centerY - 7, w, 15)) {
			return false;
		}
		ModUpdates.Release release = ModUpdates.available();
		if (release != null) {
			Minecraft client = Minecraft.getInstance();
			client.setScreenAndShow(new UpdateScreen(client.gui.screen(), release));
		} else {
			ModUpdates.check();
		}
		return true;
	}
}
