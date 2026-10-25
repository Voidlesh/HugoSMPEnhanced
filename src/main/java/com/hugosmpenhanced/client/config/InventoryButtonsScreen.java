package com.hugosmpenhanced.client.config;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.feature.inventorybuttons.IconButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonPosition;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonSpots;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryIcons;
import com.hugosmpenhanced.client.gui.StyledScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Editor for the inventory buttons, one tab per side. The strip under the tabs shows that side's
 * buttons in order; the selected one is edited below and can be moved earlier or later, which
 * swaps spots with its neighbour. Every change is written to the config immediately and saved when
 * the screen closes.
 */
public class InventoryButtonsScreen extends Screen implements StyledScreen {
	public static final int MAX_BUTTONS = 12;
	private static final int CONTROL_WIDTH = 300;
	private static final int HALF_WIDTH = (CONTROL_WIDTH - 4) / 2;
	private static final int QUARTER_WIDTH = (CONTROL_WIDTH - 12) / 4;
	private static final int GRID_COLUMNS = 15;
	private static final int GRID_ROWS = 2;
	private static final int TOTAL_HEIGHT = 24 * 6 + GRID_ROWS * 20 + 4 + 14 + 20;

	private static InventoryButtonPosition side = InventoryButtonPosition.RIGHT;

	private final Screen parent;
	private InventoryButton selected;
	private final List<IconButton> grid = new ArrayList<>();
	private List<Item> results = List.of();
	private IconButton iconPreview;
	private int headerY;

	public InventoryButtonsScreen(Screen parent) {
		super(Component.literal("Inventar-Buttons"));
		this.parent = parent;
	}

	private static List<InventoryButton> all() {
		return ModConfigManager.CONFIG.inventoryButtons;
	}

	@Override
	protected void init() {
		InventoryButtonSpots.normalize(all());
		List<InventoryButton> onSide = InventoryButtonSpots.onSide(all(), side);
		if (!onSide.contains(this.selected)) {
			this.selected = onSide.isEmpty() ? null : onSide.get(0);
		}
		this.grid.clear();

		int left = this.width / 2 - CONTROL_WIDTH / 2;
		int right = left + HALF_WIDTH + 4;
		int y = Math.max(16, (this.height - TOTAL_HEIGHT) / 2 + 6);
		this.headerY = y;

		for (InventoryButtonPosition tab : InventoryButtonPosition.values()) {
			int count = InventoryButtonSpots.onSide(all(), tab).size();
			String label = tab.displayName() + " (" + count + ")";
			Button button = Button.builder(Component.literal(tab == side ? "» " + label + " «" : label), b -> {
					side = tab;
					this.selected = null;
					this.rebuildWidgets();
				})
				.bounds(tab == InventoryButtonPosition.RIGHT ? left : right, y, HALF_WIDTH, 20).build();
			this.addRenderableWidget(button);
		}
		y += 24;

		int x = left;
		for (InventoryButton entry : onSide) {
			IconButton tile = new IconButton(InventoryIcons.stack(entry.icon), Component.literal(entry.name), b -> {
				this.selected = entry;
				this.rebuildWidgets();
			});
			tile.setPosition(x, y);
			tile.setSelected(entry == this.selected);
			tile.setTooltip(Tooltip.create(Component.literal(entry.name.isBlank() ? "Button" : entry.name)));
			this.addRenderableWidget(tile);
			x += 22;
		}
		IconButton add = new IconButton("+", Component.literal("Neuer Button"), b -> this.addButton());
		add.setPosition(x, y);
		add.setTooltip(Tooltip.create(Component.literal("Neuer Button")));
		add.active = all().size() < MAX_BUTTONS && InventoryButtonSpots.firstFree(all(), side) >= 0;
		this.addRenderableWidget(add);
		y += 24;

		int bottom = this.headerY + TOTAL_HEIGHT - 20;
		this.addRenderableWidget(CycleButton.onOffBuilder(ModConfigManager.CONFIG.inventoryButtonsEnabled)
			.create(left, bottom, HALF_WIDTH, 20, Component.literal("Im Inventar anzeigen"),
				(button, value) -> ModConfigManager.CONFIG.inventoryButtonsEnabled = value));
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
			.bounds(right, bottom, HALF_WIDTH, 20).build());

		if (this.selected == null) {
			return;
		}
		InventoryButton entry = this.selected;
		int index = onSide.indexOf(entry);

		Button earlier = this.addRenderableWidget(Button.builder(Component.literal("◀ Früher"), b -> this.move(-1))
			.bounds(left, y, QUARTER_WIDTH, 20).build());
		Button later = this.addRenderableWidget(Button.builder(Component.literal("Später ▶"), b -> this.move(1))
			.bounds(left + (QUARTER_WIDTH + 4), y, QUARTER_WIDTH, 20).build());
		earlier.active = index > 0;
		later.active = index < onSide.size() - 1;
		InventoryButtonPosition otherSide = side == InventoryButtonPosition.TOP ? InventoryButtonPosition.RIGHT : InventoryButtonPosition.TOP;
		Button switchSide = this.addRenderableWidget(Button.builder(Component.literal("Nach " + otherSide.displayName().toLowerCase()),
				b -> this.switchSide(otherSide))
			.bounds(left + 2 * (QUARTER_WIDTH + 4), y, QUARTER_WIDTH, 20).build());
		switchSide.active = InventoryButtonSpots.firstFree(all(), otherSide) >= 0;
		this.addRenderableWidget(Button.builder(Component.literal("Löschen"), b -> this.removeButton())
			.bounds(left + 3 * (QUARTER_WIDTH + 4), y, CONTROL_WIDTH - 3 * (QUARTER_WIDTH + 4), 20).build());
		y += 24;

		EditBox name = new EditBox(this.font, left, y, CONTROL_WIDTH, 20, Component.literal("Name"));
		name.setMaxLength(64);
		name.setValue(entry.name);
		name.setHint(Component.literal("Name (Tooltip), z. B. Home"));
		name.setResponder(value -> entry.name = value);
		this.addRenderableWidget(name);
		y += 24;

		EditBox action = new EditBox(this.font, left, y, CONTROL_WIDTH, 20, Component.literal("Aktion"));
		action.setMaxLength(256);
		action.setValue(entry.action);
		action.setHint(Component.literal("/home oder eine Chatnachricht"));
		action.setResponder(value -> entry.action = value);
		this.addRenderableWidget(action);
		y += 24;

		EditBox icon = new EditBox(this.font, left, y, CONTROL_WIDTH - 24, 20, Component.literal("Icon"));
		icon.setMaxLength(128);
		icon.setValue(entry.icon);
		icon.setHint(Component.literal("Item suchen"));
		this.addRenderableWidget(icon);
		this.iconPreview = this.addRenderableWidget(new IconButton(InventoryIcons.stack(entry.icon),
			Component.literal("Icon"), b -> { }));
		this.iconPreview.setPosition(left + CONTROL_WIDTH - 20, y);
		y += 24;

		for (int i = 0; i < GRID_COLUMNS * GRID_ROWS; i++) {
			final int slot = i;
			IconButton cell = new IconButton(ItemStack.EMPTY, Component.literal("Item"), b -> {
				if (slot < this.results.size()) {
					entry.icon = InventoryIcons.id(this.results.get(slot));
					this.rebuildWidgets();
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
				cell.setIcon(new ItemStack(this.results.get(i)));
				cell.setTooltip(Tooltip.create(Component.literal(InventoryIcons.id(this.results.get(i)))));
			}
		}
	}

	/** Swaps spots with the previous or next button on the same side. */
	private void move(int direction) {
		List<InventoryButton> onSide = InventoryButtonSpots.onSide(all(), side);
		int index = onSide.indexOf(this.selected);
		int other = index + direction;
		if (index < 0 || other < 0 || other >= onSide.size()) {
			return;
		}
		InventoryButton neighbour = onSide.get(other);
		int slot = this.selected.slot;
		this.selected.slot = neighbour.slot;
		neighbour.slot = slot;
		this.rebuildWidgets();
	}

	private void switchSide(InventoryButtonPosition target) {
		int slot = InventoryButtonSpots.firstFree(all(), target);
		if (slot < 0) {
			return;
		}
		this.selected.position = target;
		this.selected.slot = slot;
		side = target;
		this.rebuildWidgets();
	}

	private void addButton() {
		int slot = InventoryButtonSpots.firstFree(all(), side);
		if (all().size() >= MAX_BUTTONS || slot < 0) {
			return;
		}
		InventoryButton button = new InventoryButton();
		button.position = side;
		button.slot = slot;
		all().add(button);
		this.selected = button;
		this.rebuildWidgets();
	}

	private void removeButton() {
		List<InventoryButton> onSide = InventoryButtonSpots.onSide(all(), side);
		int index = onSide.indexOf(this.selected);
		all().remove(this.selected);
		onSide.remove(this.selected);
		this.selected = onSide.isEmpty() ? null : onSide.get(Math.max(0, Math.min(index, onSide.size() - 1)));
		this.rebuildWidgets();
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, "— INVENTAR-BUTTONS —", this.width / 2, this.headerY - 12, 0xFFAAAAAA);

		int hintY = this.headerY + TOTAL_HEIGHT - 20 - 12;
		if (this.selected == null) {
			graphics.centeredText(this.font, "Noch keine Buttons hier. Klick auf +, um einen anzulegen.",
				this.width / 2, this.headerY + 60, 0xFF888888);
		}
		graphics.centeredText(this.font, "/ am Anfang = Command · Im Inventar ziehen zum Verschieben",
			this.width / 2, hintY, 0xFF888888);
	}
}
