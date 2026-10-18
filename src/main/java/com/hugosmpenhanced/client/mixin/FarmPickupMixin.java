package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.feature.farm.FarmTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;

/** Hands the farm tracker every item the player picks up, before the item entity is removed. */
@Mixin(ClientPacketListener.class)
public abstract class FarmPickupMixin {
	@Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
	private void hugosmpenhanced$countPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		// The handler first runs on the network thread only to hand itself over to the game thread.
		if (Minecraft.getInstance().isSameThread()) {
			FarmTracker.onPickup(packet.getItemId(), packet.getPlayerId(), packet.getAmount());
		}
	}
}
