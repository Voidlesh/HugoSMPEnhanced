package com.hugosmpenhanced.client.settings.option;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.settings.Theme;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A list of words or short texts shown as chips under the label. The cross removes an entry, the
 * plus turns into a small input: Enter adds, Escape cancels.
 */
public class StringListOption extends ValueOption<List<String>> {
	private static final int H = 13;
	private static final int GAP = 4;
	private static final int MAX_LENGTH = 64;

	private final String emptyText;
	private StringBuilder input;
	private int[] origin;

	public StringListOption(String label, String description, String emptyText, Function<ModConfig, List<String>> getter,
		BiConsumer<ModConfig, List<String>> setter) {
		super(label, description, getter, setter);
		this.emptyText = emptyText;
	}

	@Override
	public boolean matches(String query) {
		if (super.matches(query)) {
			return true;
		}
		for (String entry : this.get()) {
			if (entry.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<String> copy(List<String> value) {
		return new ArrayList<>(value);
	}

	@Override
	protected int controlWidth(Font font) {
		return 0;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
	}

	private String chipText(Font font, String entry, int width) {
		int max = Math.max(30, width - 20);
		return font.width(entry) <= max ? entry : font.plainSubstrByWidth(entry, max - font.width("...")) + "...";
	}

	/** Boxes for every chip and, last, the plus or the input field: x, y, width. */
	private List<int[]> layout(Font font, int width) {
		List<int[]> boxes = new ArrayList<>();
		int px = 0;
		int py = 0;
		List<String> entries = this.get();
		for (int i = 0; i <= entries.size(); i++) {
			int w;
			if (i < entries.size()) {
				w = font.width(this.chipText(font, entries.get(i), width)) + 17;
			} else if (this.input != null) {
				w = Math.min(width, Math.max(90, font.width(this.input.toString()) + 14));
			} else {
				w = 14;
			}
			if (px > 0 && px + w > width) {
				px = 0;
				py += H + GAP;
			}
			boxes.add(new int[] {px, py, w});
			px += w + GAP;
		}
		return boxes;
	}

	@Override
	protected int belowHeight(Font font, int width) {
		List<int[]> boxes = this.layout(font, width - PAD * 2);
		return boxes.get(boxes.size() - 1)[1] + H;
	}

	@Override
	protected void renderBelow(GuiGraphicsExtractor graphics, Font font, int left, int top, int width, int mouseX, int mouseY) {
		this.origin = new int[] {left, top};
		List<String> entries = this.get();
		List<int[]> boxes = this.layout(font, width);
		for (int i = 0; i < entries.size(); i++) {
			int[] box = boxes.get(i);
			int bx = left + box[0];
			int by = top + box[1];
			Theme.panel(graphics, bx, by, box[2], H, Theme.CHIP, Theme.CHIP_BORDER);
			graphics.text(font, this.chipText(font, entries.get(i), width), bx + 4, by + 3, Theme.WHITE, false);
			boolean overCross = this.inside(mouseX, mouseY, bx + box[2] - 11, by, 11, H);
			Theme.glyph(graphics, Theme.CROSS, bx + box[2] - 9, by + 4, overCross ? 0xFFFF4040 : Theme.RED_TEXT);
		}
		int[] last = boxes.get(boxes.size() - 1);
		int bx = left + last[0];
		int by = top + last[1];
		if (this.input != null) {
			Theme.panel(graphics, bx, by, last[2], H, Theme.FIELD, Theme.GOLD);
			String text = this.input.toString();
			String shown = font.plainSubstrByWidth(text, last[2] - 10, true);
			graphics.text(font, shown, bx + 4, by + 3, Theme.WHITE, false);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = bx + 4 + font.width(shown);
				graphics.fill(cx, by + 2, cx + 1, by + H - 2, Theme.WHITE);
			}
		} else {
			boolean over = this.inside(mouseX, mouseY, bx, by, last[2], H);
			Theme.panel(graphics, bx, by, last[2], H, 0xFF1E1E1E, over ? Theme.GOLD : 0xFF7A7A7A);
			Theme.glyph(graphics, Theme.PLUS, bx + 5, by + 4, Theme.WHITE);
			if (entries.isEmpty() && this.emptyText != null) {
				graphics.text(font, this.emptyText, bx + last[2] + 6, by + 3, Theme.FAINT, false);
			}
		}
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		if (this.origin == null || button != 0) {
			return false;
		}
		Font font = Minecraft.getInstance().font;
		List<String> entries = this.get();
		List<int[]> boxes = this.layout(font, this.width - PAD * 2);
		for (int i = 0; i < entries.size(); i++) {
			int[] box = boxes.get(i);
			int bx = this.origin[0] + box[0];
			int by = this.origin[1] + box[1];
			if (this.inside(mouseX, mouseY, bx + box[2] - 11, by, 11, H)) {
				entries.remove(i);
				changed();
				return true;
			}
		}
		int[] last = boxes.get(boxes.size() - 1);
		if (this.inside(mouseX, mouseY, this.origin[0] + last[0], this.origin[1] + last[1], last[2], H)) {
			if (this.input == null) {
				this.input = new StringBuilder();
			}
			return true;
		}
		return false;
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
		if (!text.isEmpty() && !this.get().contains(text)) {
			this.get().add(text);
			changed();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.input == null) {
			return false;
		}
		switch (event.key()) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> this.commit();
			case InputConstants.KEY_ESCAPE -> this.input = null;
			case InputConstants.KEY_BACKSPACE -> {
				if (!this.input.isEmpty()) {
					this.input.setLength(this.input.length() - 1);
				}
			}
			default -> {
				if (event.key() == InputConstants.KEY_V && (event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
					String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replace("\n", " ");
					this.append(clip);
				}
			}
		}
		return true;
	}

	private void append(String text) {
		for (char c : text.toCharArray()) {
			if (this.input.length() < MAX_LENGTH && c >= ' ') {
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
