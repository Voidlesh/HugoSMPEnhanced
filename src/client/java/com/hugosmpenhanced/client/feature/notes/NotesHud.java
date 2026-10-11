package com.hugosmpenhanced.client.feature.notes;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws pinned notes on the HUD, one box per note, stacked downwards from the position set in
 * {@link NotesHudPositionScreen} (top left by default).
 */
public final class NotesHud {
	public static final int WIDTH = 140;
	private static final int MAX_LINES = 12;
	private static final int PADDING = 4;
	private static final int BACKGROUND = 0x90000000;
	private static final int TITLE = 0xFFFFD54F;
	private static final int TEXT = 0xFFE0E0E0;

	private NotesHud() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hugosmpenhanced", "pinned_notes"),
			(graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()
			|| minecraft.screen instanceof NotesHudPositionScreen) {
			return;
		}
		List<Note> pinned = ModConfigManager.CONFIG.notes.stream().filter(note -> note.pinned).toList();
		int[] position = position(graphics.guiWidth(), graphics.guiHeight(), height(minecraft.font, pinned));
		draw(graphics, minecraft.font, pinned, position[0], position[1]);
	}

	/** The configured position, kept on screen for a block of the given height. */
	public static int[] position(int screenWidth, int screenHeight, int height) {
		int x = Math.max(0, Math.min(ModConfigManager.CONFIG.notesHudX, screenWidth - WIDTH));
		int y = Math.max(0, Math.min(ModConfigManager.CONFIG.notesHudY, screenHeight - Math.min(height, screenHeight)));
		return new int[] {x, y};
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
		int limit = graphics.guiHeight();
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
			if (y > limit) {
				break;
			}
		}
	}
}
