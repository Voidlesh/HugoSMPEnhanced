package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.inventorylayout.InventoryLayout;
import com.hugosmpenhanced.client.feature.sell.SellOverlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Draws the saved inventory layout's ghost items and the sell assistant's marks over the slots. */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryLayoutSlotMixin {
	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void hugosmpenhanced$renderLayout(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		InventoryLayout.renderSlot(graphics, slot);
		SellOverlay.renderSlot(graphics, slot);
	}
}
