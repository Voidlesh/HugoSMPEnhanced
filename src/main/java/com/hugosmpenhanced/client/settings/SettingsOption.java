package com.hugosmpenhanced.client.settings;

import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * One row of the settings menu: a label, an optional description under it and a control on the
 * right (or below, for lists). Rows remember where they were last drawn so clicks can be routed
 * back to them; the screen always renders before it receives input.
 */
public abstract class SettingsOption {
	protected static final int PAD = 8;
	private static final int RESET_SIZE = 5;

	protected final String label;
	protected final String description;
	private String keywords = "";

	protected int x;
	protected int y;
	protected int width;
	protected int height;
	protected boolean hovered;

	protected SettingsOption(String label, String description) {
		this.label = label;
		this.description = description;
	}

	/** Extra words the search should find this row by. */
	public SettingsOption keywords(String keywords) {
		this.keywords = keywords;
		return this;
	}

	public String label() {
		return this.label;
	}

	/** Where the row was last drawn: x, y, width, height (for tests). */
	public int[] bounds() {
		return new int[] {this.x, this.y, this.width, this.height};
	}

	public boolean matches(String query) {
		String q = query.toLowerCase(Locale.ROOT);
		return this.label.toLowerCase(Locale.ROOT).contains(q)
			|| (this.description() != null && this.description().toLowerCase(Locale.ROOT).contains(q))
			|| this.keywords.toLowerCase(Locale.ROOT).contains(q);
	}

	/** The grey text under the label; rows whose text changes override this. */
	protected String description() {
		return this.description;
	}

	/** Width the control takes on the right of the row. */
	protected abstract int controlWidth(Font font);

	protected abstract void renderControl(GuiGraphics graphics, Font font, int right, int centerY, int mouseX, int mouseY);

	/** Height of extra content under the text (chip lists), 0 for plain rows. */
	protected int belowHeight(Font font, int width) {
		return 0;
	}

	protected void renderBelow(GuiGraphics graphics, Font font, int left, int top, int width, int mouseX, int mouseY) {
	}

	private int textWidth(Font font, int width) {
		return Math.max(40, width - PAD * 2 - this.controlWidth(font) - 14);
	}

	private List<FormattedCharSequence> descriptionLines(Font font, int width) {
		String text = this.description();
		return text == null ? List.of() : font.split(Component.literal(text), this.textWidth(font, width));
	}

	public int height(Font font, int width) {
		int lines = this.descriptionLines(font, width).size();
		int text = lines == 0 ? 22 : 16 + lines * 10;
		int below = this.belowHeight(font, width);
		return below > 0 ? text - (lines == 0 ? 6 : 2) + below + 6 : text;
	}

	public final void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY, boolean allowHover) {
		Font font = Minecraft.getInstance().font;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = this.height(font, width);
		this.hovered = allowHover && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + this.height;
		if (this.hovered) {
			graphics.fill(x + 1, y, x + width - 1, y + this.height - 1, Theme.ROW_HOVER);
		}
		List<FormattedCharSequence> lines = this.descriptionLines(font, width);
		int below = this.belowHeight(font, width);
		int labelY = lines.isEmpty() && below == 0 ? y + 7 : y + 4;
		String shown = font.plainSubstrByWidth(this.label, this.textWidth(font, width));
		graphics.drawString(font, shown, x + PAD, labelY, Theme.TEXT);
		int lineY = labelY + 10;
		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, x + PAD, lineY, Theme.MUTED, false);
			lineY += 10;
		}
		int right = x + width - PAD;
		int controlCenter = below > 0 ? labelY + 4 : y + this.height / 2;
		this.renderControl(graphics, font, right, controlCenter, mouseX, mouseY);
		if (below > 0) {
			this.renderBelow(graphics, font, x + PAD, lineY + 2, width - PAD * 2, mouseX, mouseY);
		}
		if (this.hovered && this.hasDefault() && !this.isDefault()) {
			int[] reset = this.resetBox(font);
			int color = this.inside(mouseX, mouseY, reset[0] - 2, reset[1] - 2, RESET_SIZE + 4, RESET_SIZE + 4) ? Theme.WHITE : Theme.MUTED;
			Theme.glyph(graphics, Theme.RESET, reset[0], reset[1], color);
		}
	}

	private int[] resetBox(Font font) {
		int controlWidth = this.controlWidth(font);
		int resetX = this.x + this.width - PAD - controlWidth - (controlWidth > 0 ? 12 : 6);
		int centerY = this.belowHeight(font, this.width) > 0 ? this.y + 8 : this.y + this.height / 2;
		return new int[] {resetX, centerY - 2};
	}

	/** A tooltip for the row under the mouse, or null. Drawn by the screen, so it lands where the mouse is. */
	public Component tooltip() {
		return null;
	}

	protected boolean inside(double mouseX, double mouseY, int left, int top, int w, int h) {
		return mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < top + h;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.inside(mouseX, mouseY, this.x, this.y, this.width, this.height)) {
			return false;
		}
		if (this.hasDefault() && !this.isDefault()) {
			int[] reset = this.resetBox(Minecraft.getInstance().font);
			if (this.inside(mouseX, mouseY, reset[0] - 2, reset[1] - 2, RESET_SIZE + 4, RESET_SIZE + 4)) {
				this.reset();
				this.saveChange();
				return true;
			}
		}
		return this.clickControl(mouseX, mouseY, button);
	}

	protected boolean clickControl(double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY) {
		return false;
	}

	public void mouseReleased() {
	}

	/** Whether this row currently takes keyboard input (key binding, list entry). */
	public boolean capturing() {
		return false;
	}

	public void stopCapturing() {
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	/** A mouse button pressed anywhere while this row captures input; true when the row took it. */
	public boolean captureMouse(int button) {
		return false;
	}

	public boolean hasDefault() {
		return false;
	}

	public boolean isDefault() {
		return true;
	}

	public void reset() {
	}

	protected static void changed() {
		ModConfigManager.save();
	}

	/** Saves after this row was reset; rows bound elsewhere than the mod config override it. */
	protected void saveChange() {
		ModConfigManager.save();
	}

	/** Rows that show one of Minecraft's own options; "reset everything" leaves those alone. */
	public boolean minecraftOption() {
		return false;
	}
}
