package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.feature.auction.AuctionSearch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
//? if v263
import net.minecraft.world.level.block.entity.SignTextSlot;
//? end

/** Opens the auction search instead of HugoSMP's search sign. */
@Mixin(LocalPlayer.class)
public class AuctionSearchSignMixin {
	@Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
	//? if v263
	private void hugosmpenhanced$auctionSearch(SignBlockEntity sign, SignTextSlot slot, CallbackInfo ci) {
		if (AuctionSearch.tryOpen(sign, slot == SignTextSlot.FRONT)) {
	//? else
	private void hugosmpenhanced$auctionSearch(SignBlockEntity sign, boolean front, CallbackInfo ci) {
		if (AuctionSearch.tryOpen(sign, front)) {
	//? end
			ci.cancel();
		}
	}
}
