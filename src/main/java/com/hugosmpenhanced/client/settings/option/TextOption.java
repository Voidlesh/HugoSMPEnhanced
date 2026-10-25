package com.hugosmpenhanced.client.settings.option;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? end
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.settings.Theme;

//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * One line of text in a full-width field under the label. A click starts editing, Enter or a
 * click elsewhere keeps the text, Escape throws the edit away.
 */
public class TextOption extends ValueOption<String> {
	private static final int H = 15;

	private final String hint;
	private final int maxLength;
	private StringBuilder input;
	private int[] field;

	public TextOption(String label, String description, String hint, int maxLength, Function<ModConfig, String> getter,
		BiConsumer<ModConfig, String> setter) {
		super(label, description, getter, setter);
		this.hint = hint;
		this.maxLength = maxLength;
	}

	@Override
	protected int controlWidth(Font font) {
		return 0;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
	}

	@Override
	protected int belowHeight(Font font, int width) {
		return H;
	}

	@Override
	protected void renderBelow(GuiGraphicsExtractor graphics, Font font, int left, int top, int width, int mouseX, int mouseY) {
		this.field = new int[] {left, top, width};
		boolean editing = this.input != null;
		boolean over = this.inside(mouseX, mouseY, left, top, width, H);
		Theme.panel(graphics, left, top, width, H, Theme.FIELD, editing ? Theme.GOLD : over ? 0xFF7A7A7A : Theme.FIELD_BORDER);
		String text = editing ? this.input.toString() : this.get();
		int room = width - 10;
		if (text.isEmpty() && !editing) {
			graphics.text(font, font.plainSubstrByWidth(this.hint == null ? "" : this.hint, room), left + 5, top + 4, Theme.FAINT, false);
			return;
		}
		// While typing the end stays visible, otherwise the start.
		String shown = editing ? font.plainSubstrByWidth(text, room, true) : font.plainSubstrByWidth(text, room);
		if (!editing && shown.length() < text.length()) {
			shown = font.plainSubstrByWidth(text, room - font.width("...")) + "...";
		}
		graphics.text(font, shown, left + 5, top + 4, Theme.WHITE, false);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = left + 5 + font.width(shown);
			graphics.fill(cx, top + 3, cx + 1, top + H - 3, Theme.WHITE);
		}
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		//? if modern
		if (this.field == null || button != InputConstants.MOUSE_BUTTON_LEFT || !this.inside(mouseX, mouseY, this.field[0], this.field[1], this.field[2], H)) {
		//? else
		if (this.field == null || button != 0 || !this.inside(mouseX, mouseY, this.field[0], this.field[1], this.field[2], H)) {
		//? end
			return false;
		}
		if (this.input == null) {
			this.input = new StringBuilder(this.get());
		}
		return true;
	}

	@Override
	public boolean capturing() {
		return this.input != null;
	}

	@Override
	public void stopCapturing() {
		this.commit();
	}

	private void commit() {
		if (this.input == null) {
			return;
		}
		String text = this.input.toString().strip();
		this.input = null;
		if (!text.equals(this.get())) {
			this.set(text);
			changed();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.input == null) {
			return false;
		}
		boolean control = (event.modifiers() & InputConstants.MOD_CONTROL) != 0;
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> this.commit();
			case InputConstants.KEY_ESCAPE -> this.input = null;
			case InputConstants.KEY_BACKSPACE -> {
				if (control) {
					// Whole word, like in chat.
					int end = this.input.length();
					int start = end;
					while (start > 0 && this.input.charAt(start - 1) == ' ') {
						start--;
					}
					while (start > 0 && this.input.charAt(start - 1) != ' ') {
						start--;
					}
					this.input.delete(start, end);
				} else if (!this.input.isEmpty()) {
					this.input.setLength(this.input.length() - 1);
				}
			}
			default -> {
				if (event.key() == InputConstants.KEY_V && control) {
					this.append(Minecraft.getInstance().keyboardHandler.getClipboard().replace("\n", " "));
				}
			}
		}
		return true;
	}

	private void append(String text) {
		for (char c : text.toCharArray()) {
			if (this.input.length() < this.maxLength && c >= ' ' && c != '§') {
				this.input.append(c);
			}
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.input == null) {
			return false;
		}
		if (event.isAllowedChatCharacter()) {
			this.append(event.codepointAsString());
		}
		return true;
	}
}
