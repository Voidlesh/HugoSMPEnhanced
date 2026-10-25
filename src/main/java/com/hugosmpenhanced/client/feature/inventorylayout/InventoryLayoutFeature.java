package com.hugosmpenhanced.client.feature.inventorylayout;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? end
import java.util.ArrayList;
import java.util.List;

//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.inventorybuttons.IconButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonsFeature;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryIcons;
import com.hugosmpenhanced.client.feature.inventorybuttons.PlacedButton;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The two inventory layout buttons: the eye shows a saved layout (see {@link InventoryLayout}),
 * the book saves the inventory as a layout. They sit on the inventory button spots and are dragged
 * like those (see {@link InventoryButtonsFeature}), starting out right of the edit button.
 * With several layouts a click folds a small list out of the button instead of opening a screen:
 * the eye's list picks the layout to show, the book's list saves a new one, overwrites or deletes
 * one. Hovering a layout in either list previews it on the inventory.
 */
public final class InventoryLayoutFeature {
	private static final int ACCENT = 0xFF55C46A;
	private static final int ROW_HEIGHT = 13;
	private static final int PAD = 3;
	private static final int DELETE_WIDTH = 12;
	private static final int PANEL = 0xF0181818;
	private static final int BORDER = 0xFF555555;
	private static final int ROW_HOVER = 0x30FFFFFF;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF808080;
	private static final int TEXT_CONFIRM = 0xFFFFD040;
	private static final int TEXT_DELETE = 0xFFFF5555;

	/** The lists of the inventory screen open right now, for the game test. */
	private static Menus current;

	private InventoryLayoutFeature() {
	}

	/** Centre of a row (or of its delete cross) in the open fold-out list, in GUI pixels; null when closed. */
	public static int[] openRowCenter(int row, boolean delete) {
		Menus menus = current;
		if (menus == null || menus.open == null || row >= menus.rows.size()) {
			return null;
		}
		int x = delete ? menus.menuX + menus.menuWidth - PAD - DELETE_WIDTH / 2 : menus.menuX + menus.menuWidth / 3;
		return new int[] {x, menus.menuY + 1 + row * ROW_HEIGHT + ROW_HEIGHT / 2};
	}

	/** Creates the buttons for one inventory screen and hooks their fold-out lists into it. */
	public static List<PlacedButton> install(InventoryScreen screen) {
		Menus menus = new Menus(screen);
		menus.register();
		current = menus;
		ModConfig config = ModConfigManager.CONFIG;
		return List.of(new PlacedButton(menus.eye, config.inventoryLayoutShowButton, menus::clickEye),
			new PlacedButton(menus.book, config.inventoryLayoutSaveButton, menus::clickBook));
	}

	private enum Kind {
		EYE, BOOK
	}

	/** A line in a fold-out list; layout is the layout it previews, -1 for none. */
	private record Row(String label, int color, int layout, Runnable action, Runnable delete) {
	}

	private static final class Menus {
		private final InventoryScreen screen;
		private final IconButton eye;
		private final IconButton book;
		private Kind open;
		private int confirmOverwrite = -1;
		private int confirmDelete = -1;
		private int tooltipState = Integer.MIN_VALUE;

		// Bounds of the open list, refreshed every frame.
		private List<Row> rows = List.of();
		private int menuX;
		private int menuY;
		private int menuWidth;

		private Menus(InventoryScreen screen) {
			this.screen = screen;
			ModConfig config = ModConfigManager.CONFIG;
			this.eye = new IconButton(InventoryIcons.stack(config.inventoryLayoutShowButton.icon),
				Component.literal("Inventar-Layout anzeigen"), b -> this.clickEye());
			this.book = new IconButton(InventoryIcons.stack(config.inventoryLayoutSaveButton.icon),
				Component.literal("Inventar-Layout speichern"), b -> this.clickBook());
		}

		private void register() {
			ScreenEvents.beforeExtract(this.screen).register((s, graphics, mouseX, mouseY, tickDelta) -> this.update(mouseX, mouseY));
			ScreenEvents.afterExtract(this.screen).register((s, graphics, mouseX, mouseY, tickDelta) -> this.render(graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(this.screen).register((s, event) -> this.onClick(event));
			ScreenKeyboardEvents.allowKeyPress(this.screen).register((s, event) -> {
				if (this.open != null && event.key() == InputConstants.KEY_ESCAPE) {
					this.close();
					return false;
				}
				return true;
			});
			ScreenEvents.remove(this.screen).register(s -> {
				InventoryLayout.previewIndex = -1;
				if (current == this) {
					current = null;
				}
			});
		}

		private static Inventory inventory() {
			return Minecraft.getInstance().player.getInventory();
		}

		private void clickEye() {
			List<SavedInventoryLayout> layouts = InventoryLayout.layouts();
			if (layouts.isEmpty()) {
				toast("Kein Inventar-Layout gespeichert", "Sortier dein Inventar und klick auf das Buch.", Items.WRITABLE_BOOK);
			} else if (layouts.size() == 1) {
				InventoryLayout.show(InventoryLayout.shownIndex() == 0 ? -1 : 0);
			} else {
				this.toggle(Kind.EYE);
			}
		}

		private void clickBook() {
			if (InventoryLayout.layouts().isEmpty()) {
				this.saveNew();
			} else {
				this.toggle(Kind.BOOK);
			}
		}

		private void toggle(Kind kind) {
			Kind was = this.open;
			this.close();
			if (was != kind) {
				this.open = kind;
			}
		}

		private void close() {
			this.open = null;
			this.confirmOverwrite = -1;
			this.confirmDelete = -1;
			this.rows = List.of();
			InventoryLayout.previewIndex = -1;
		}

		private void saveNew() {
			Inventory inventory = inventory();
			if (InventoryLayout.isEmpty(inventory)) {
				toast("Dein Inventar ist leer", "Sortier erst deine Items, dann speichern.", Items.WRITABLE_BOOK);
				return;
			}
			int index = InventoryLayout.saveNew(inventory);
			this.close();
			toast(InventoryLayout.layouts().get(index).name + " gespeichert", "Mit dem Auge siehst du, wo was hingehört.", Items.ENDER_EYE);
		}

		private void overwrite(int index) {
			Inventory inventory = inventory();
			if (this.confirmOverwrite != index && !InventoryLayout.matches(index, inventory)) {
				this.confirmOverwrite = index;
				this.confirmDelete = -1;
				return;
			}
			InventoryLayout.overwrite(index, inventory);
			String name = InventoryLayout.layouts().get(index).name;
			this.close();
			toast(name + " überschrieben", "Es enthält jetzt deine aktuelle Sortierung.", Items.ENDER_EYE);
		}

		private void delete(int index) {
			if (this.confirmDelete != index) {
				this.confirmDelete = index;
				this.confirmOverwrite = -1;
				return;
			}
			String name = InventoryLayout.layouts().get(index).name;
			InventoryLayout.delete(index);
			this.close();
			toast(name + " gelöscht", "", Items.BARRIER);
		}

		private List<Row> buildRows() {
			List<Row> result = new ArrayList<>();
			List<SavedInventoryLayout> layouts = InventoryLayout.layouts();
			int shown = InventoryLayout.shownIndex();
			if (this.open == Kind.EYE) {
				for (int i = 0; i < layouts.size(); i++) {
					int index = i;
					boolean active = i == shown;
					result.add(new Row((active ? "● " : "  ") + layouts.get(i).name, active ? ACCENT : TEXT, i, () -> {
						InventoryLayout.show(active ? -1 : index);
						this.close();
					}, null));
				}
				if (shown >= 0) {
					result.add(new Row("  Ausblenden", TEXT_DIM, -1, () -> {
						InventoryLayout.show(-1);
						this.close();
					}, null));
				}
			} else if (this.open == Kind.BOOK) {
				boolean full = layouts.size() >= InventoryLayout.MAX_LAYOUTS;
				result.add(new Row(full ? "+ Neues Layout (voll)" : "+ Neues Layout", full ? TEXT_DIM : ACCENT, -1,
					full ? () -> { } : this::saveNew, null));
				for (int i = 0; i < layouts.size(); i++) {
					int index = i;
					String name = layouts.get(i).name;
					String label = i == this.confirmOverwrite ? name + " überschreiben?"
						: i == this.confirmDelete ? name + " löschen?" : name;
					int color = i == this.confirmOverwrite ? TEXT_CONFIRM : i == this.confirmDelete ? TEXT_DELETE : TEXT;
					result.add(new Row(label, color, i, () -> this.overwrite(index), () -> this.delete(index)));
				}
			}
			return result;
		}

		private void update(int mouseX, int mouseY) {
			this.eye.setSelected(this.open == Kind.EYE || InventoryLayout.shownIndex() >= 0);
			this.book.setSelected(this.open == Kind.BOOK);
			// Tooltips are rebuilt only when their text changes, so their hover delay keeps running.
			int tooltipState = this.open != null ? -1 : InventoryLayout.layouts().size();
			if (tooltipState != this.tooltipState) {
				this.tooltipState = tooltipState;
				this.eye.setTooltip(this.open != null ? null : eyeTooltip());
				this.book.setTooltip(this.open != null ? null : bookTooltip());
			}
			if (this.open == null) {
				return;
			}

			this.rows = this.buildRows();
			Font font = Minecraft.getInstance().font;
			int width = 0;
			for (Row row : this.rows) {
				width = Math.max(width, font.width(row.label()));
			}
			this.menuWidth = width + 2 * PAD + (this.open == Kind.BOOK ? DELETE_WIDTH + 2 : 0);
			int height = this.rows.size() * ROW_HEIGHT + 2;
			IconButton anchor = this.open == Kind.EYE ? this.eye : this.book;
			this.menuX = anchor.getX();
			if (this.menuX + this.menuWidth > this.screen.width - 2) {
				this.menuX = anchor.getX() + anchor.getWidth() - this.menuWidth;
			}
			this.menuX = Math.max(2, this.menuX);
			this.menuY = anchor.getY() + anchor.getHeight() + 1;
			if (this.menuY + height > this.screen.height - 2) {
				this.menuY = Math.max(2, anchor.getY() - 1 - height);
			}

			int hovered = this.rowAt(mouseX, mouseY);
			InventoryLayout.previewIndex = hovered >= 0 ? this.rows.get(hovered).layout() : -1;
		}

		private int rowAt(double mouseX, double mouseY) {
			if (mouseX < this.menuX || mouseX >= this.menuX + this.menuWidth || mouseY < this.menuY + 1) {
				return -1;
			}
			int row = (int) ((mouseY - this.menuY - 1) / ROW_HEIGHT);
			return row < this.rows.size() ? row : -1;
		}

		private boolean overDelete(double mouseX, Row row) {
			return row.delete() != null && mouseX >= this.menuX + this.menuWidth - DELETE_WIDTH - PAD;
		}

		private boolean onClick(MouseButtonEvent event) {
			if (this.open == null) {
				return true;
			}
			int index = this.rowAt(event.x(), event.y());
			if (index < 0) {
				this.close();
				return false;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				Row row = this.rows.get(index);
				(this.overDelete(event.x(), row) ? row.delete() : row.action()).run();
			}
			return false;
		}

		private void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
			if (this.open == null || this.rows.isEmpty()) {
				return;
			}
			graphics.nextStratum();
			Font font = Minecraft.getInstance().font;
			int height = this.rows.size() * ROW_HEIGHT + 2;
			graphics.fill(this.menuX, this.menuY, this.menuX + this.menuWidth, this.menuY + height, PANEL);
			graphics.outline(this.menuX, this.menuY, this.menuWidth, height, BORDER);
			int hovered = this.rowAt(mouseX, mouseY);
			for (int i = 0; i < this.rows.size(); i++) {
				Row row = this.rows.get(i);
				int y = this.menuY + 1 + i * ROW_HEIGHT;
				if (i == hovered) {
					graphics.fill(this.menuX + 1, y, this.menuX + this.menuWidth - 1, y + ROW_HEIGHT, ROW_HOVER);
				}
				graphics.text(font, row.label(), this.menuX + PAD, y + 3, row.color());
				if (row.delete() != null) {
					boolean hot = i == hovered && this.overDelete(mouseX, row);
					graphics.text(font, "✕", this.menuX + this.menuWidth - PAD - DELETE_WIDTH + 3, y + 3, hot ? TEXT_DELETE : TEXT_DIM);
				}
			}
		}

		private static Tooltip eyeTooltip() {
			int count = InventoryLayout.layouts().size();
			String hint = count > 1 ? "\nKlick: Layout auswählen." : "";
			return Tooltip.create(Component.literal("Inventar-Layout anzeigen")
				.append(Component.literal("\nZeigt halbtransparent, wo deine Items hingehören. Falsch belegte Slots werden rot."
					+ hint + "\nZum Verschieben ziehen.").withStyle(ChatFormatting.GRAY)));
		}

		private static Tooltip bookTooltip() {
			String hint = InventoryLayout.layouts().isEmpty() ? "" : "\nKlick: neues Layout, überschreiben oder löschen.";
			return Tooltip.create(Component.literal("Inventar-Layout speichern")
				.append(Component.literal("\nSpeichert deine aktuelle Sortierung. Leere Slots dürfen später alles enthalten."
					+ hint + "\nZum Verschieben ziehen.").withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void toast(String title, String subtitle, Item icon) {
		TpaToast.show(Component.literal(title), Component.literal(subtitle), new ItemStack(icon), 3500, ACCENT);
	}
}
