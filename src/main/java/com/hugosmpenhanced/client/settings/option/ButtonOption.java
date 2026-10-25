package com.hugosmpenhanced.client.settings.option;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? end
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A gold button that opens a sub page or runs an action. Dangerous actions are red and need a
 * second click within a few seconds.
 */
public class ButtonOption extends SettingsOption {
	private static final long CONFIRM_MS = 3000;

	private final String text;
	private final Runnable action;
	private final boolean danger;
	private final boolean subPage;
	private long confirmUntil;
	private long doneUntil;

	private ButtonOption(String label, String description, String text, Runnable action, boolean danger, boolean subPage) {
		super(label, description);
		this.text = text;
		this.action = action;
		this.danger = danger;
		this.subPage = subPage;
	}

	/** Opens another screen; shown with a chevron. */
	public static ButtonOption page(String label, String description, String text, Runnable open) {
		return new ButtonOption(label, description, text, open, false, true);
	}

	public static ButtonOption action(String label, String description, String text, Runnable action) {
		return new ButtonOption(label, description, text, action, false, false);
	}

	/** Red, asks "Sicher?" first. */
	public static ButtonOption danger(String label, String description, String text, Runnable action) {
		return new ButtonOption(label, description, text, action, true, false);
	}

	private String shownText() {
		long now = System.currentTimeMillis();
		if (now < this.doneUntil) {
			return "Erledigt";
		}
		return now < this.confirmUntil ? "Sicher?" : this.text;
	}

	@Override
	protected int controlWidth(Font font) {
		int widest = font.width(this.text);
		if (this.danger) {
			widest = Math.max(widest, Math.max(font.width("Sicher?"), font.width("Erledigt")));
		}
		return widest + (this.subPage ? 22 : 14);
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		int w = this.controlWidth(font);
		int left = right - w;
		boolean over = this.inside(mouseX, mouseY, left, centerY - 7, w, 15);
		if (this.danger) {
			Theme.panel(graphics, left, centerY - 7, w, 15, over ? 0x8C8C1E1E : Theme.RED_FILL, Theme.RED);
		} else {
			Theme.panel(graphics, left, centerY - 7, w, 15, over ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
		}
		graphics.text(font, this.shownText(), left + 7, centerY - 3, Theme.WHITE);
		if (this.subPage) {
			Theme.glyph(graphics, Theme.CHEVRON, right - 10, centerY - 2, Theme.WHITE);
		}
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		//? if modern
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
		//? else
		if (button != 0) {
		//? end
			return false;
		}
		if (this.danger) {
			long now = System.currentTimeMillis();
			if (now >= this.confirmUntil) {
				this.confirmUntil = now + CONFIRM_MS;
				return true;
			}
			this.confirmUntil = 0;
			this.doneUntil = now + 1500;
		}
		this.action.run();
		return true;
	}
}
