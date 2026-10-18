package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.auction.LimitedOffers;
import com.hugosmpenhanced.client.feature.orders.OrdersHelper;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Draws the frames behind the best limited offers and the fillable orders, under the item. */
@Mixin(AbstractContainerScreen.class)
public abstract class LimitedOfferSlotMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void hugosmpenhanced$renderBestOffer(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		LimitedOffers.renderSlotBackground(graphics, slot);
		OrdersHelper.renderSlotBackground(graphics, slot);
	}
}
