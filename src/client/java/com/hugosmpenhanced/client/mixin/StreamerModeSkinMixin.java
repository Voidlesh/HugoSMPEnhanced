package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.feature.streamermode.StreamerMode;
import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;

/** Streamer mode: players in the world and heads in the tab list use a default skin. */
@Mixin(PlayerInfo.class)
public abstract class StreamerModeSkinMixin {
	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void hugosmpenhanced$hideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin skin = StreamerMode.skin(this.getProfile(), cir.getReturnValue());
		if (skin != cir.getReturnValue()) {
			cir.setReturnValue(skin);
		}
	}
}
