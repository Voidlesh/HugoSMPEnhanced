package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.feature.auction.AuctionSearch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/** Opens the auction search instead of HugoSMP's search sign. */
@Mixin(LocalPlayer.class)
public class AuctionSearchSignMixin {
	@Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$auctionSearch(SignBlockEntity sign, boolean front, CallbackInfo ci) {
		if (AuctionSearch.tryOpen(sign, front)) {
			ci.cancel();
		}
	}
}
