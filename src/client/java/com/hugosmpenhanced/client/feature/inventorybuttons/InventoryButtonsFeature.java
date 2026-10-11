package com.hugosmpenhanced.client.feature.inventorybuttons;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.InventoryButtonsScreen;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

/**
 * Adds the configured buttons to the player's own inventory, each on its spot to the right of or
 * above it (see {@link InventoryButtonSpots}), plus an edit button in the top-right corner. A click
 * (press and release on it) runs the button's action; dragging moves it to another spot, swapping with whatever is there.
 * Positions are refreshed every frame because opening the recipe book moves the inventory without
 * re-initialising the screen.
 */
public final class InventoryButtonsFeature {
	public static final int SIZE = 20;
	private static final int DRAG_THRESHOLD = 3;
	private static final int SPOT_OUTLINE = 0x50FFFFFF;
	private static final int SPOT_TARGET = 0xFFFFFFFF;

	private InventoryButtonsFeature() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventory && ModConfigManager.CONFIG.inventoryButtonsEnabled) {
				new Overlay(inventory).install();
			}
		});
	}

	/** The buttons of one open inventory screen and the drag in progress, if any. */
	private static final class Overlay {
		private final InventoryScreen screen;
		private final List<Placed> placed = new ArrayList<>();
		private IconButton edit;

		private Placed pressed;
		private double pressX;
		private double pressY;
		private boolean dragging;

		private Overlay(InventoryScreen screen) {
			this.screen = screen;
		}

		private void install() {
			ModConfig config = ModConfigManager.CONFIG;
			InventoryButtonSpots.normalize(config.inventoryButtons);
			List<AbstractWidget> widgets = Screens.getButtons(this.screen);

			for (InventoryButton entry : config.inventoryButtons) {
				if (entry.slot < 0) {
					continue;
				}
				IconButton button = new IconButton(InventoryIcons.stack(entry.icon), Component.literal(entry.name),
					b -> run(entry.action));
				button.setTooltip(Tooltip.create(tooltip(entry)));
				Placed p = new Placed(button, entry);
				button.setPressHandler(b -> this.press(p));
				this.placed.add(p);
				widgets.add(button);
			}

			this.edit = new IconButton("✎", Component.literal("Inventar-Buttons bearbeiten"),
				b -> Minecraft.getInstance().setScreen(new InventoryButtonsScreen(this.screen)));
			this.edit.setTooltip(Tooltip.create(Component.literal("Inventar-Buttons bearbeiten")
				.append(Component.literal("\nButtons kannst du im Inventar per Ziehen verschieben.").withStyle(ChatFormatting.GRAY))));
			widgets.add(this.edit);

			this.layout();
			ScreenEvents.beforeRender(this.screen).register((s, graphics, mouseX, mouseY, tickDelta) -> this.layout());
			ScreenMouseEvents.allowMouseDrag(this.screen).register((s, event, dx, dy) -> this.onDrag(event));
			ScreenMouseEvents.allowMouseRelease(this.screen).register((s, event) -> this.onRelease(event));
			ScreenEvents.afterRender(this.screen).register((s, graphics, mouseX, mouseY, tickDelta) -> this.renderSpots(graphics, mouseX, mouseY));
		}

		private int left() {
			return ((AbstractContainerScreenAccessor) this.screen).hugosmpenhanced$getLeftPos();
		}

		private int top() {
			return ((AbstractContainerScreenAccessor) this.screen).hugosmpenhanced$getTopPos();
		}

		private int imageWidth() {
			return ((AbstractContainerScreenAccessor) this.screen).hugosmpenhanced$getImageWidth();
		}

		private void layout() {
			int left = this.left();
			int top = this.top();
			int width = this.imageWidth();
			for (Placed p : this.placed) {
				if (this.dragging && p == this.pressed) {
					continue;
				}
				p.button.setPosition(left + InventoryButtonSpots.x(p.entry.position, p.entry.slot, width),
					top + InventoryButtonSpots.y(p.entry.position, p.entry.slot));
			}
			this.edit.setPosition(left + width + 2, top - InventoryButtonSpots.STEP);
		}

		private void press(Placed p) {
			Minecraft minecraft = Minecraft.getInstance();
			this.pressed = p;
			this.pressX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
			this.pressY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
			this.dragging = false;
		}

		/** Moves the held button with the mouse once it has left its spot by a few pixels. */
		private boolean onDrag(MouseButtonEvent event) {
			if (this.pressed == null) {
				return true;
			}
			if (!this.dragging && Math.hypot(event.x() - this.pressX, event.y() - this.pressY) > DRAG_THRESHOLD) {
				this.dragging = true;
			}
			if (this.dragging) {
				this.pressed.button.setPosition((int) event.x() - SIZE / 2, (int) event.y() - SIZE / 2);
			}
			return false;
		}

		/** Releasing either runs the button's action (a click) or drops it on a spot (a drag). */
		private boolean onRelease(MouseButtonEvent event) {
			if (this.pressed == null) {
				return true;
			}
			Placed released = this.pressed;
			boolean wasDragging = this.dragging;
			this.pressed = null;
			this.dragging = false;
			if (wasDragging) {
				this.drop(released, event.x(), event.y());
			} else if (released.button.isMouseOver(event.x(), event.y())) {
				run(released.entry.action);
			}
			this.layout();
			return false;
		}

		private void drop(Placed p, double mouseX, double mouseY) {
			Spot target = this.spotAt(mouseX, mouseY);
			if (target == null) {
				return;
			}
			List<InventoryButton> buttons = ModConfigManager.CONFIG.inventoryButtons;
			InventoryButton other = InventoryButtonSpots.at(buttons, target.side, target.slot);
			if (other != null && other != p.entry) {
				other.position = p.entry.position;
				other.slot = p.entry.slot;
			}
			p.entry.position = target.side;
			p.entry.slot = target.slot;
			ModConfigManager.save();
		}

		private Spot spotAt(double mouseX, double mouseY) {
			int left = this.left();
			int top = this.top();
			int width = this.imageWidth();
			for (InventoryButtonPosition side : InventoryButtonPosition.values()) {
				for (int slot = 0; slot < InventoryButtonSpots.count(side); slot++) {
					int x = left + InventoryButtonSpots.x(side, slot, width);
					int y = top + InventoryButtonSpots.y(side, slot);
					if (mouseX >= x - 1 && mouseX < x + SIZE + 1 && mouseY >= y - 1 && mouseY < y + SIZE + 1) {
						return new Spot(side, slot);
					}
				}
			}
			return null;
		}

		/** While dragging, outlines every spot and highlights the one under the mouse. */
		private void renderSpots(GuiGraphics graphics, int mouseX, int mouseY) {
			if (!this.dragging) {
				return;
			}
			int left = this.left();
			int top = this.top();
			int width = this.imageWidth();
			Spot target = this.spotAt(mouseX, mouseY);
			for (InventoryButtonPosition side : InventoryButtonPosition.values()) {
				for (int slot = 0; slot < InventoryButtonSpots.count(side); slot++) {
					boolean isTarget = target != null && target.side == side && target.slot == slot;
					HugoStyle.drawOutline(graphics, left + InventoryButtonSpots.x(side, slot, width),
						top + InventoryButtonSpots.y(side, slot), SIZE, SIZE, isTarget ? SPOT_TARGET : SPOT_OUTLINE);
				}
			}
		}
	}

	private static Component tooltip(InventoryButton entry) {
		String name = entry.name.isBlank() ? "Button" : entry.name;
		String action = entry.action.isBlank() ? "(keine Aktion)" : entry.action.trim();
		return Component.literal(name).append(Component.literal("\n" + action).withStyle(ChatFormatting.GRAY));
	}

	/** A leading "/" runs the text as a command, anything else is sent as a chat message. */
	public static void run(String action) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		String text = action == null ? "" : action.trim();
		if (connection == null || text.isEmpty()) {
			return;
		}
		minecraft.gui.getChat().addRecentChat(text);
		if (text.startsWith("/")) {
			connection.sendCommand(text.substring(1));
		} else {
			connection.sendChat(text);
		}
	}

	private record Placed(IconButton button, InventoryButton entry) {
	}

	private record Spot(InventoryButtonPosition side, int slot) {
	}
}
