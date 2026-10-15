package com.hugosmpenhanced.client.feature.notes;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.hud.HudElement;
import com.hugosmpenhanced.client.hud.HudPos;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Pinned notes on the HUD, one box per note, stacked downwards; placed in the HUD editor. */
public final class NotesHud implements HudElement {
	public static final NotesHud INSTANCE = new NotesHud();
	public static final int WIDTH = 140;
	private static final int MAX_LINES = 12;
	private static final int PADDING = 4;
	private static final int BACKGROUND = 0x90000000;
	private static final int TITLE = 0xFFFFD54F;
	private static final int TEXT = 0xFFE0E0E0;

	private NotesHud() {
	}

	@Override
	public String id() {
		return "notes";
	}

	@Override
	public String label() {
		return "Notizen";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.notesHudOn;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.notesHudOn = enabled;
	}

	@Override
	public boolean visible() {
		return !pinned().isEmpty();
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(0, 0);
	}

	private static List<Note> pinned() {
		return ModConfigManager.CONFIG.notes.stream().filter(note -> note.pinned).toList();
	}

	/** The pinned notes, or a sample note in the editor when none is pinned. */
	private static List<Note> shown(boolean preview) {
		if (!preview) {
			return pinned();
		}
		Note sample = new Note();
		sample.title = "Beispiel";
		sample.text = "Hier erscheinen angeheftete Notizen.";
		return List.of(sample);
	}

	@Override
	public int width(Font font, boolean preview) {
		return WIDTH;
	}

	@Override
	public int height(Font font, boolean preview) {
		return height(font, shown(preview));
	}

	@Override
	public void render(GuiGraphics graphics, Font font, boolean preview) {
		draw(graphics, font, shown(preview), 0, 0);
	}

	/** Total height of the boxes for these notes. */
	public static int height(Font font, List<Note> notes) {
		int total = 0;
		for (Note note : notes) {
			total += boxHeight(font, note) + 4;
		}
		return Math.max(0, total - 4);
	}

	private static int boxHeight(Font font, Note note) {
		int shown = Math.min(font.split(Component.literal(note.text), WIDTH - 2 * PADDING).size(), MAX_LINES);
		return PADDING + 10 + shown * 9 + PADDING;
	}

	public static void draw(GuiGraphics graphics, Font font, List<Note> notes, int x, int y) {
		for (Note note : notes) {
			List<FormattedCharSequence> lines = font.split(Component.literal(note.text), WIDTH - 2 * PADDING);
			int shown = Math.min(lines.size(), MAX_LINES);
			int height = PADDING + 10 + shown * 9 + PADDING;
			graphics.fill(x, y, x + WIDTH, y + height, BACKGROUND);
			graphics.drawString(font, font.plainSubstrByWidth(note.displayTitle(), WIDTH - 2 * PADDING), x + PADDING, y + PADDING, TITLE);
			for (int i = 0; i < shown; i++) {
				graphics.drawString(font, lines.get(i), x + PADDING, y + PADDING + 10 + i * 9, TEXT);
			}
			y += height + 4;
		}
	}
}
