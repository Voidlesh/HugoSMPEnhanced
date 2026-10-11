package com.hugosmpenhanced.client.feature.inventorybuttons;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.InventoryButtonsScreen;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

/**
 * Adds the configured buttons to the player's own inventory: a column to the right of it and a
 * row above it, plus a small edit button in the top-right corner that opens the editor. The
 * positions are refreshed every frame because opening the recipe book moves the inventory
 * without re-initialising the screen.
 */
public final class InventoryButtonsFeature {
	public static final int SIZE = 20;
	private static final int GAP = 2;
	private static final int STEP = SIZE + GAP;
	private static final int PER_COLUMN = 7;
	private static final int PER_ROW = 8;

	private InventoryButtonsFeature() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inventory) || !ModConfigManager.CONFIG.inventoryButtonsEnabled) {
				return;
			}
			addButtons(inventory);
		});
	}

	private static void addButtons(InventoryScreen screen) {
		ModConfig config = ModConfigManager.CONFIG;
		List<AbstractWidget> widgets = Screens.getButtons(screen);
		List<Placed> placed = new ArrayList<>();

		int right = 0;
		int top = 0;
		for (InventoryButton entry : config.inventoryButtons) {
			IconButton button = new IconButton(InventoryIcons.stack(entry.icon), Component.literal(entry.name),
				b -> run(entry.action));
			button.setTooltip(Tooltip.create(tooltip(entry)));
			int index = entry.position == InventoryButtonPosition.TOP ? top++ : right++;
			placed.add(new Placed(button, entry.position, index));
			widgets.add(button);
		}

		IconButton edit = new IconButton("✎", Component.literal("Inventar-Buttons bearbeiten"),
			b -> Minecraft.getInstance().setScreen(new InventoryButtonsScreen(screen)));
		edit.setTooltip(Tooltip.create(Component.literal("Inventar-Buttons bearbeiten")));
		widgets.add(edit);

		layout(screen, placed, edit);
		ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> layout(screen, placed, edit));
	}

	private static void layout(InventoryScreen screen, List<Placed> placed, IconButton edit) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int left = accessor.hugosmpenhanced$getLeftPos();
		int topPos = accessor.hugosmpenhanced$getTopPos();
		int rightX = left + accessor.hugosmpenhanced$getImageWidth() + GAP;

		for (Placed p : placed) {
			if (p.position == InventoryButtonPosition.TOP) {
				p.button.setPosition(left + (p.index % PER_ROW) * STEP, topPos - STEP - (p.index / PER_ROW) * STEP);
			} else {
				p.button.setPosition(rightX + (p.index / PER_COLUMN) * STEP, topPos + (p.index % PER_COLUMN) * STEP);
			}
		}
		edit.setPosition(rightX, topPos - STEP);
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

	private record Placed(IconButton button, InventoryButtonPosition position, int index) {
	}
}
