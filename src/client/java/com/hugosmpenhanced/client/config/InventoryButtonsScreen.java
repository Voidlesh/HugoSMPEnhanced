package com.hugosmpenhanced.client.config;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.feature.inventorybuttons.IconButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonPosition;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryIcons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Editor for the inventory buttons. Every change is written to the config immediately and saved
 * when the screen closes; the inventory picks the new buttons up the next time it is opened.
 */
public class InventoryButtonsScreen extends Screen {
	public static final int MAX_BUTTONS = 12;
	private static final int CONTROL_WIDTH = 300;
	private static final int HALF_WIDTH = (CONTROL_WIDTH - 4) / 2;
	private static final int GRID_COLUMNS = 15;
	private static final int GRID_ROWS = 2;

	private final Screen parent;
	private int selected;
	private final List<IconButton> grid = new ArrayList<>();
	private List<Item> results = List.of();
	private IconButton iconPreview;
	private int headerY;

	public InventoryButtonsScreen(Screen parent) {
		super(Component.literal("Inventar-Buttons"));
		this.parent = parent;
	}

	private List<InventoryButton> buttons() {
		return ModConfigManager.CONFIG.inventoryButtons;
	}

	@Override
	protected void init() {
		List<InventoryButton> buttons = this.buttons();
		this.selected = Math.max(0, Math.min(this.selected, buttons.size() - 1));
		this.grid.clear();

		int left = this.width / 2 - CONTROL_WIDTH / 2;
		int right = left + HALF_WIDTH + 4;
		int y = Math.max(28, this.height / 2 - 96);
		this.headerY = y;

		Button prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.select(this.selected - 1))
			.bounds(left, y, 20, 20).build());
		Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.select(this.selected + 1))
			.bounds(left + 148, y, 20, 20).build());
		Button add = this.addRenderableWidget(Button.builder(Component.literal("+ Neu"), b -> this.addButton())
			.bounds(left + 172, y, 62, 20).build());
		Button remove = this.addRenderableWidget(Button.builder(Component.literal("Löschen"), b -> this.removeButton())
			.bounds(left + 238, y, 62, 20).build());
		prev.active = this.selected > 0;
		next.active = this.selected < buttons.size() - 1;
		add.active = buttons.size() < MAX_BUTTONS;
		remove.active = !buttons.isEmpty();
		y += 28;

		this.addRenderableWidget(CycleButton.onOffBuilder(ModConfigManager.CONFIG.inventoryButtonsEnabled)
			.create(left, y + 72 + GRID_ROWS * 20 + 24, HALF_WIDTH, 20, Component.literal("Im Inventar anzeigen"),
				(button, value) -> ModConfigManager.CONFIG.inventoryButtonsEnabled = value));
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
			.bounds(right, y + 72 + GRID_ROWS * 20 + 24, HALF_WIDTH, 20).build());

		if (buttons.isEmpty()) {
			return;
		}
		InventoryButton entry = buttons.get(this.selected);

		EditBox name = new EditBox(this.font, left, y, CONTROL_WIDTH, 20, Component.literal("Name"));
		name.setMaxLength(64);
		name.setValue(entry.name);
		name.setHint(Component.literal("Name (Tooltip), z. B. Home"));
		name.setResponder(value -> entry.name = value);
		this.addRenderableWidget(name);
		this.setInitialFocus(name);
		y += 24;

		EditBox action = new EditBox(this.font, left, y, CONTROL_WIDTH, 20, Component.literal("Aktion"));
		action.setMaxLength(256);
		action.setValue(entry.action);
		action.setHint(Component.literal("/home oder eine Chatnachricht"));
		action.setResponder(value -> entry.action = value);
		this.addRenderableWidget(action);
		y += 24;

		this.addRenderableWidget(CycleButton.<InventoryButtonPosition>builder(
				value -> Component.literal(value.displayName()), entry.position)
			.withValues(InventoryButtonPosition.values())
			.create(left, y, HALF_WIDTH, 20, Component.literal("Position"), (button, value) -> entry.position = value));

		EditBox icon = new EditBox(this.font, right, y, HALF_WIDTH - 24, 20, Component.literal("Icon"));
		icon.setMaxLength(128);
		icon.setValue(entry.icon);
		icon.setHint(Component.literal("Item suchen"));
		this.addRenderableWidget(icon);
		this.iconPreview = this.addRenderableWidget(new IconButton(InventoryIcons.stack(entry.icon),
			Component.literal("Icon"), b -> { }));
		this.iconPreview.setPosition(right + HALF_WIDTH - 20, y);
		y += 24;

		for (int i = 0; i < GRID_COLUMNS * GRID_ROWS; i++) {
			final int slot = i;
			IconButton cell = new IconButton(ItemStack.EMPTY, Component.literal("Item"), b -> {
				if (slot < this.results.size()) {
					Item item = this.results.get(slot);
					entry.icon = InventoryIcons.id(item);
					this.iconPreview.setIcon(new ItemStack(item));
				}
			});
			cell.setPosition(left + (i % GRID_COLUMNS) * 20, y + (i / GRID_COLUMNS) * 20);
			this.grid.add(this.addRenderableWidget(cell));
		}

		icon.setResponder(value -> {
			Item item = InventoryIcons.item(value);
			if (item != null) {
				entry.icon = InventoryIcons.id(item);
			}
			this.iconPreview.setIcon(InventoryIcons.stack(entry.icon));
			this.updateGrid(value);
		});
		this.updateGrid(entry.icon.equals(new InventoryButton().icon) ? "" : entry.icon);
	}

	private void updateGrid(String query) {
		this.results = InventoryIcons.search(query, GRID_COLUMNS * GRID_ROWS);
		for (int i = 0; i < this.grid.size(); i++) {
			IconButton cell = this.grid.get(i);
			boolean used = i < this.results.size();
			cell.visible = used;
			if (used) {
				ItemStack stack = new ItemStack(this.results.get(i));
				cell.setIcon(stack);
				cell.setTooltip(Tooltip.create(Component.literal(InventoryIcons.id(this.results.get(i)))));
			}
		}
	}

	private void select(int index) {
		this.selected = index;
		this.rebuildWidgets();
	}

	private void addButton() {
		if (this.buttons().size() >= MAX_BUTTONS) {
			return;
		}
		this.buttons().add(new InventoryButton());
		this.select(this.buttons().size() - 1);
	}

	private void removeButton() {
		if (this.buttons().isEmpty()) {
			return;
		}
		this.buttons().remove(this.selected);
		this.select(this.selected - 1);
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, "— INVENTAR-BUTTONS —", this.width / 2, this.headerY - 16, 0xFFAAAAAA);

		int left = this.width / 2 - CONTROL_WIDTH / 2;
		List<InventoryButton> buttons = this.buttons();
		String label = buttons.isEmpty() ? "Keine Buttons" : "Button " + (this.selected + 1) + " / " + buttons.size();
		graphics.drawCenteredString(this.font, label, left + 84, this.headerY + 6, 0xFFFFFFFF);

		if (buttons.isEmpty()) {
			graphics.drawCenteredString(this.font, "Klick auf \"+ Neu\", um einen Button anzulegen.",
				this.width / 2, this.headerY + 60, 0xFF888888);
		} else {
			graphics.drawCenteredString(this.font, "Mit / am Anfang: Command, sonst Chatnachricht",
				this.width / 2, this.headerY + 28 + 72 + GRID_ROWS * 20 + 7, 0xFF888888);
		}
	}
}
