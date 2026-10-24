package com.hugosmpenhanced.client.feature.aliases;

import org.lwjgl.glfw.GLFW;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Creates, changes or deletes one chat alias. {@code alias} is null for a new one. Saving checks the
 * name (unique, short) and the text (fits into one chat message) before the button is enabled.
 */
public class ChatAliasEditScreen extends Screen implements StyledScreen {
	private static final int PAD = 10;
	private static final int FIELD_H = 18;
	private static final long CONFIRM_MS = 3000;

	private final Screen parent;
	private final ChatAlias alias;
	private EditBox nameField;
	private EditBox textField;
	private Button save;
	private Button delete;
	private String error = "";
	private long confirmUntil;

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;

	public ChatAliasEditScreen(Screen parent, ChatAlias alias) {
		super(Component.literal(alias == null ? "Neues Kürzel" : "Kürzel bearbeiten"));
		this.parent = parent;
		this.alias = alias;
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(340, this.width - 16);
		this.panelHeight = 160;
		this.left = (this.width - this.panelWidth) / 2;
		this.top = (this.height - this.panelHeight) / 2;
		int x = this.left + PAD;
		int fieldWidth = this.panelWidth - 2 * PAD;

		this.nameField = new EditBox(this.font, x, this.top + 56, fieldWidth, FIELD_H, Component.literal("Kürzel"));
		this.nameField.setMaxLength(ChatAliases.MAX_NAME);
		this.nameField.setHint(Component.literal("z. B. werbung1").withColor(Theme.FAINT));
		this.nameField.setValue(this.alias == null ? "" : this.alias.name);
		this.nameField.setResponder(value -> this.refresh());
		this.addRenderableWidget(this.nameField);

		this.textField = new EditBox(this.font, x, this.top + 92, fieldWidth, FIELD_H, Component.literal("Text"));
		this.textField.setMaxLength(1024);
		this.textField.setHint(Component.literal("Der Text, der gesendet wird").withColor(Theme.FAINT));
		this.textField.setValue(this.alias == null ? "" : this.alias.text);
		this.textField.setResponder(value -> this.refresh());
		this.addRenderableWidget(this.textField);

		int buttonY = this.top + this.panelHeight - 28;
		int gap = 6;
		int buttonWidth = (fieldWidth - 2 * gap) / 3;
		this.save = this.addRenderableWidget(Button.builder(Component.literal("Speichern"), b -> this.save())
			.bounds(x, buttonY, buttonWidth, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> this.onClose())
			.bounds(x + buttonWidth + gap, buttonY, buttonWidth, 20).build());
		this.delete = this.addRenderableWidget(Button.builder(Component.literal("Löschen"), b -> this.deleteOrConfirm())
			.bounds(x + 2 * (buttonWidth + gap), buttonY, buttonWidth, 20).build());
		this.delete.visible = this.alias != null;
		this.setInitialFocus(this.nameField);
		this.refresh();
	}

	/** Re-checks both fields after every change and says what is wrong, if anything. */
	private void refresh() {
		String nameError = ChatAliases.nameError(this.nameField.getValue(), this.alias);
		String textError = ChatAliases.textError(this.textField.getValue());
		this.error = nameError != null && !this.nameField.getValue().isBlank() ? nameError
			: textError != null && !this.textField.getValue().isBlank() ? textError : "";
		this.save.active = nameError == null && textError == null;
	}

	private void save() {
		if (!this.save.active) {
			return;
		}
		String name = this.nameField.getValue().trim();
		String text = this.textField.getValue().trim();
		if (this.alias == null) {
			ModConfigManager.CONFIG.chatAliases.add(new ChatAlias(name, text));
		} else {
			this.alias.name = name;
			this.alias.text = text;
		}
		ModConfigManager.save();
		this.onClose();
	}

	private void deleteOrConfirm() {
		if (System.currentTimeMillis() > this.confirmUntil) {
			this.confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
			return;
		}
		ModConfigManager.CONFIG.chatAliases.remove(this.alias);
		ModConfigManager.save();
		this.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && this.save.active) {
			this.save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(this.left, this.top, this.left + this.panelWidth, this.top + this.panelHeight, Theme.FRAME);
		HugoStyle.drawOutline(graphics, this.left, this.top, this.panelWidth, this.panelHeight, Theme.FRAME_BORDER);
		graphics.fill(this.left + 1, this.top + 1, this.left + this.panelWidth - 1, this.top + 3, Theme.GOLD);

		Font font = this.font;
		this.delete.setMessage(Component.literal(System.currentTimeMillis() < this.confirmUntil ? "Sicher?" : "Löschen"));
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.left + PAD, this.top + 9);
		graphics.pose().scale(1.5f, 1.5f);
		graphics.drawString(font, this.alias == null ? "Neues Kürzel" : "Kürzel bearbeiten", 0, 0, Theme.WHITE, false);
		graphics.pose().popMatrix();
		graphics.drawString(font, "Schreib das Kürzel allein in den Chat, dann sendet Enter den Text.",
			this.left + PAD, this.top + 24, Theme.MUTED, false);
		graphics.drawString(font, "Kürzel", this.left + PAD, this.top + 44, Theme.TEXT_DIM, false);
		graphics.drawString(font, "Text", this.left + PAD, this.top + 80, Theme.TEXT_DIM, false);

		String status = this.error.isEmpty() ? this.textField.getValue().trim().length() + " / " + ChatAliases.MAX_TEXT + " Zeichen" : this.error;
		graphics.drawString(font, status, this.left + PAD, this.top + 118, this.error.isEmpty() ? Theme.MUTED : Theme.RED_TEXT, false);

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(this.parent);
	}
}
