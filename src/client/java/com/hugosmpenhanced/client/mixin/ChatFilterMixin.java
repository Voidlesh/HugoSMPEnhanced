package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.feature.chatfilter.ChatFilterService;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ChatFilterMixin {
	@Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		if (ServerCheck.isHugoSmp() && ChatFilterService.shouldCancel(packet.content().getString())) {
			ci.cancel();
		}
	}

	@Inject(method = "handlePlayerChat", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
		if (!ServerCheck.isHugoSmp()) {
			return;
		}

		Component unsigned = packet.unsignedContent();
		String text = unsigned != null ? unsigned.getString() : packet.body().content();

		if (ChatFilterService.shouldCancel(text)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleDisguisedChat", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
		if (ServerCheck.isHugoSmp() && ChatFilterService.shouldCancel(packet.message().getString())) {
			ci.cancel();
		}
	}
}
