package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.feature.tpa.TpaRequestHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class TpaSystemChatMixin {
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void hugosmpenhanced$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		// HEAD runs twice: first on the network thread (which then hands the packet over), then on
		// the render thread. Toasts and chat lines may only be touched from the render thread.
		if (!Minecraft.getInstance().isSameThread()) {
			return;
		}
		TpaRequestHandler.handleSystemMessage(packet.content().getString());
	}
}
