package com.hugosmpenhanced.client.feature.notes;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the pinned notes to where they should sit on the HUD. */
public class NotesHudPositionScreen extends Screen implements StyledScreen {
	private final Screen parent;
	private boolean dragging;
	private double grabX;
	private double grabY;

	public NotesHudPositionScreen(Screen parent) {
		super(Component.literal("Notizen verschieben"));
		this.parent = parent;
	}

	/** The pinned notes, or a sample note when none is pinned so there is something to drag. */
	private static List<Note> shown() {
		List<Note> pinned = ModConfigManager.CONFIG.notes.stream().filter(note -> note.pinned).toList();
		if (!pinned.isEmpty()) {
			return pinned;
		}
		Note sample = new Note();
		sample.title = "Beispiel";
		sample.text = "Hier erscheinen angeheftete Notizen.";
		return List.of(sample);
	}

	private int[] bounds() {
		int height = NotesHud.height(this.font, shown());
		int[] position = NotesHud.position(this.width, this.height, height);
		return new int[] {position[0], position[1], NotesHud.WIDTH, height};
	}

	@Override
	protected void init() {
		int y = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
			ModConfigManager.CONFIG.notesHudX = 4;
			ModConfigManager.CONFIG.notesHudY = 4;
		}).bounds(this.width / 2 - 104, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
			.bounds(this.width / 2 + 4, y, 100, 20).build());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int[] box = this.bounds();
		if (event.button() == 0 && event.x() >= box[0] && event.x() < box[0] + box[2] && event.y() >= box[1] && event.y() < box[1] + box[3]) {
			this.dragging = true;
			this.grabX = event.x() - box[0];
			this.grabY = event.y() - box[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.dragging) {
			int[] box = this.bounds();
			ModConfigManager.CONFIG.notesHudX = (int) Math.max(0, Math.min(event.x() - this.grabX, this.width - box[2]));
			ModConfigManager.CONFIG.notesHudY = (int) Math.max(0, Math.min(event.y() - this.grabY, this.height - box[3]));
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int[] box = this.bounds();
		NotesHud.draw(graphics, this.font, shown(), box[0], box[1]);
		boolean hovered = mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3];
		HugoStyle.drawOutline(graphics, box[0] - 1, box[1] - 1, box[2] + 2, box[3] + 2,
			this.dragging || hovered ? 0xFFFFFFFF : 0xFF888888);
		graphics.drawCenteredString(this.font, "Ziehe die Notizen mit der Maus an ihren Platz.", this.width / 2, this.height - 42, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
