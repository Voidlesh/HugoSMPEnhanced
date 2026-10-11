package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.auction.LimitedOffers;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Draws the gold frame behind the best limited offers, under the item. */
@Mixin(AbstractContainerScreen.class)
public abstract class LimitedOfferSlotMixin {
	@Inject(method = "renderSlot", at = @At("HEAD"))
	private void hugosmpenhanced$renderBestOffer(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		LimitedOffers.renderSlotBackground(graphics, slot);
	}
}
