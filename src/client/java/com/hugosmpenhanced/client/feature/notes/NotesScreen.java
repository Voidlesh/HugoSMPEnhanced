package com.hugosmpenhanced.client.feature.notes;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.hud.HudEditorScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The notepad: the list of notes on the left, the selected note's title and text on the right.
 * Every change goes straight into the config, which is saved when the screen closes. A pinned note
 * is also shown on the HUD (see {@link NotesHud}).
 */
public class NotesScreen extends Screen implements StyledScreen {
	public static final int MAX_NOTES = 20;
	private static final int LIST_WIDTH = 110;
	private static final int GAP = 6;
	private static final int ROW = 20;

	private static int selectedIndex;

	private final Screen parent;
	private boolean deleteArmed;

	public NotesScreen(Screen parent) {
		super(Component.literal("Notizblock"));
		this.parent = parent;
	}

	private static List<Note> notes() {
		return ModConfigManager.CONFIG.notes;
	}

	@Override
	protected void init() {
		List<Note> notes = notes();
		if (notes.isEmpty()) {
			notes.add(new Note());
		}
		selectedIndex = Math.max(0, Math.min(selectedIndex, notes.size() - 1));
		Note note = notes.get(selectedIndex);

		int totalWidth = Math.min(this.width - 20, 420);
		int left = (this.width - totalWidth) / 2;
		int top = 22;
		int bottom = this.height - 8;
		int editorLeft = left + LIST_WIDTH + GAP;
		int editorWidth = totalWidth - LIST_WIDTH - GAP;

		// Note list, then "+" below it.
		int y = top;
		int maxRows = Math.max(1, (bottom - top - ROW - 4) / ROW);
		for (int i = 0; i < Math.min(notes.size(), maxRows); i++) {
			final int index = i;
			Note entry = notes.get(i);
			String label = (entry.pinned ? "★ " : "") + entry.displayTitle();
			label = this.font.plainSubstrByWidth(label, LIST_WIDTH - 10);
			Button button = Button.builder(Component.literal(i == selectedIndex ? "» " + label : label), b -> this.select(index))
				.bounds(left, y, LIST_WIDTH, ROW).build();
			this.addRenderableWidget(button);
			y += ROW;
		}
		Button add = this.addRenderableWidget(Button.builder(Component.literal("+ Neue Notiz"), b -> this.addNote())
			.bounds(left, y + 4, LIST_WIDTH, ROW).build());
		add.active = notes.size() < Math.min(MAX_NOTES, maxRows);

		// Editor
		EditBox title = new EditBox(this.font, editorLeft, top, editorWidth, ROW, Component.literal("Titel"));
		title.setMaxLength(48);
		title.setValue(note.title);
		title.setHint(Component.literal("Titel, z. B. Einkaufsliste"));
		title.setResponder(value -> note.title = value);
		this.addRenderableWidget(title);

		int textTop = top + ROW + 4;
		int buttonsY = bottom - ROW;
		MultiLineEditBox text = MultiLineEditBox.builder()
			.setX(editorLeft)
			.setY(textTop)
			.setPlaceholder(Component.literal("Hier schreiben …"))
			// 12 px below the box stay free for its character counter.
			.build(this.font, editorWidth, buttonsY - 14 - textTop, Component.literal("Notiz"));
		text.setCharacterLimit(4000);
		text.setValue(note.text);
		text.setValueListener(value -> note.text = value);
		this.addRenderableWidget(text);

		int quarter = (editorWidth - 3 * 4) / 4;
		Button pin = this.addRenderableWidget(Button.builder(
				Component.literal(note.pinned ? "Angeheftet ✔" : "Anheften"), b -> {
					note.pinned = !note.pinned;
					this.rebuildWidgets();
				})
			.bounds(editorLeft, buttonsY, quarter, ROW).build());
		pin.setTooltip(Tooltip.create(Component.literal("Angeheftete Notizen bleiben beim Spielen sichtbar.")));
		Button move = this.addRenderableWidget(Button.builder(Component.literal("Verschieben"),
				b -> this.minecraft.setScreen(new HudEditorScreen(this)))
			.bounds(editorLeft + quarter + 4, buttonsY, quarter, ROW).build());
		move.setTooltip(Tooltip.create(Component.literal("Legt fest, wo angeheftete Notizen auf dem Bildschirm stehen.")));
		this.addRenderableWidget(Button.builder(Component.literal(this.deleteArmed ? "Wirklich?" : "Löschen"), b -> this.delete())
			.bounds(editorLeft + 2 * (quarter + 4), buttonsY, quarter, ROW).build());
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
			.bounds(editorLeft + 3 * (quarter + 4), buttonsY, editorWidth - 3 * (quarter + 4), ROW).build());

		this.setInitialFocus(note.title.isBlank() && note.text.isBlank() ? title : text);
	}

	private void select(int index) {
		selectedIndex = index;
		this.deleteArmed = false;
		this.rebuildWidgets();
	}

	private void addNote() {
		notes().add(new Note());
		this.select(notes().size() - 1);
	}

	private void delete() {
		if (!this.deleteArmed) {
			this.deleteArmed = true;
			this.rebuildWidgets();
			return;
		}
		notes().remove(selectedIndex);
		this.select(Math.max(0, selectedIndex - 1));
	}

	@Override
	public void onClose() {
		// Drop notes that were never written in, so opening the notepad leaves nothing behind.
		notes().removeIf(note -> note.title.isBlank() && note.text.isBlank() && !note.pinned);
		ModConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, "— NOTIZBLOCK —", this.width / 2, 8, 0xFFAAAAAA);
	}
}
