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

/** Draws pinned notes in the top-left corner of the HUD, one box per note. */
public final class NotesHud {
	private static final int WIDTH = 140;
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
		if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = minecraft.font;
		int y = 4;
		for (Note note : ModConfigManager.CONFIG.notes) {
			if (!note.pinned) {
				continue;
			}
			List<FormattedCharSequence> lines = font.split(Component.literal(note.text), WIDTH - 2 * PADDING);
			int shown = Math.min(lines.size(), MAX_LINES);
			int height = PADDING + 10 + shown * 9 + PADDING;
			graphics.fill(4, y, 4 + WIDTH, y + height, BACKGROUND);
			graphics.drawString(font, font.plainSubstrByWidth(note.displayTitle(), WIDTH - 2 * PADDING), 4 + PADDING, y + PADDING, TITLE);
			for (int i = 0; i < shown; i++) {
				graphics.drawString(font, lines.get(i), 4 + PADDING, y + PADDING + 10 + i * 9, TEXT);
			}
			y += height + 4;
			if (y > graphics.guiHeight() / 2) {
				break;
			}
		}
	}
}
