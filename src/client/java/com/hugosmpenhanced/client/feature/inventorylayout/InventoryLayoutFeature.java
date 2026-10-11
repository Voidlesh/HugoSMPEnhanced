package com.hugosmpenhanced.client.feature.inventorylayout;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.inventorybuttons.IconButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonSpots;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Two buttons in the top-right corner of the player's inventory, next to the inventory buttons'
 * edit button: one shows or hides the saved layout (see {@link InventoryLayout}), the other saves
 * the current inventory as the layout. Overwriting a different saved layout takes a second click.
 */
public final class InventoryLayoutFeature {
	private static final long CONFIRM_MS = 4000;
	private static final int ACCENT = 0xFF55C46A;

	private InventoryLayoutFeature() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventory && ModConfigManager.CONFIG.inventoryLayoutEnabled) {
				new Buttons(inventory).install();
			}
		});
	}

	private static final class Buttons {
		private final InventoryScreen screen;
		private IconButton toggle;
		private IconButton save;
		private long confirmUntil;

		private Buttons(InventoryScreen screen) {
			this.screen = screen;
		}

		private void install() {
			this.toggle = new IconButton(new ItemStack(Items.ENDER_EYE), Component.literal("Inventar-Layout anzeigen"),
				b -> this.toggle());
			this.toggle.setTooltip(Tooltip.create(Component.literal("Inventar-Layout anzeigen")
				.append(Component.literal("\nZeigt, wo deine Items laut gespeichertem Layout hingehören. "
					+ "Falsch belegte Slots werden rot.").withStyle(ChatFormatting.GRAY))));
			this.save = new IconButton(new ItemStack(Items.WRITABLE_BOOK), Component.literal("Inventar-Layout speichern"),
				b -> this.save());
			this.updateSaveTooltip();
			Screens.getButtons(this.screen).add(this.toggle);
			Screens.getButtons(this.screen).add(this.save);

			this.layout();
			ScreenEvents.beforeRender(this.screen).register((s, graphics, mouseX, mouseY, tickDelta) -> this.layout());
		}

		/** Refreshed every frame, because the recipe book moves the inventory without re-initialising. */
		private void layout() {
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this.screen;
			int x = accessor.hugosmpenhanced$getLeftPos() + accessor.hugosmpenhanced$getImageWidth() + 2;
			if (ModConfigManager.CONFIG.inventoryButtonsEnabled) {
				x += InventoryButtonSpots.STEP; // the edit button sits in the corner
			}
			int y = accessor.hugosmpenhanced$getTopPos() - InventoryButtonSpots.STEP;
			this.toggle.setPosition(x, y);
			this.save.setPosition(x + InventoryButtonSpots.STEP, y);
			this.toggle.setSelected(ModConfigManager.CONFIG.inventoryLayoutShown);
			if (this.confirmUntil != 0 && Util.getMillis() > this.confirmUntil) {
				this.confirmUntil = 0;
				this.updateSaveTooltip();
			}
			this.save.setSelected(this.confirmUntil != 0);
		}

		private void toggle() {
			if (!InventoryLayout.hasLayout()) {
				TpaToast.show(Component.literal("Kein Inventar-Layout gespeichert"),
					Component.literal("Sortier dein Inventar und klick auf das Buch."), new ItemStack(Items.WRITABLE_BOOK), 4000, ACCENT);
				return;
			}
			ModConfigManager.CONFIG.inventoryLayoutShown = !ModConfigManager.CONFIG.inventoryLayoutShown;
			ModConfigManager.save();
		}

		private void save() {
			Inventory inventory = Minecraft.getInstance().player.getInventory();
			if (InventoryLayout.hasLayout() && !InventoryLayout.matches(inventory) && this.confirmUntil == 0) {
				this.confirmUntil = Util.getMillis() + CONFIRM_MS;
				this.updateSaveTooltip();
				TpaToast.show(Component.literal("Altes Layout überschreiben?"),
					Component.literal("Klick nochmal auf das Buch zum Speichern."), new ItemStack(Items.WRITABLE_BOOK), CONFIRM_MS, ACCENT);
				return;
			}
			this.confirmUntil = 0;
			InventoryLayout.save(inventory);
			this.updateSaveTooltip();
			TpaToast.show(Component.literal("Inventar-Layout gespeichert"),
				Component.literal("Mit dem Auge siehst du, wo was hingehört."), new ItemStack(Items.ENDER_EYE), 3000, ACCENT);
		}

		private void updateSaveTooltip() {
			Component hint = this.confirmUntil != 0
				? Component.literal("\nNochmal klicken, um das alte Layout zu überschreiben.").withStyle(ChatFormatting.YELLOW)
				: Component.literal("\nSpeichert, wie dein Inventar gerade sortiert ist. Leere Slots dürfen später alles enthalten.")
					.withStyle(ChatFormatting.GRAY);
			this.save.setTooltip(Tooltip.create(Component.literal("Inventar-Layout speichern").append(hint)));
		}
	}
}
