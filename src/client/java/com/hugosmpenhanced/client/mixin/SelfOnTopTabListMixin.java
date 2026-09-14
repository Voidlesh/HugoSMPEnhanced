package com.hugosmpenhanced.client.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class SelfOnTopTabListMixin {
	@Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true)
	private void hugosmpenhanced$moveSelfToTop(CallbackInfoReturnable<List<PlayerInfo>> cir) {
		if (!ModConfigManager.CONFIG.tabListSelfOnTopEnabled || !ServerCheck.isHugoSmp()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		UUID selfId = client.player.getGameProfile().id();
		String selfName = client.player.getGameProfile().name();

		List<PlayerInfo> original = cir.getReturnValue();
		int selfIndex = -1;
		for (int i = 0; i < original.size(); i++) {
			// Matched by UUID first, falling back to name: some proxied setups (e.g. Velocity/
			// BungeeCord with misconfigured forwarding) report a different UUID for our own
			// entry in the tab list than the one the client authenticated with.
			var profile = original.get(i).getProfile();
			if (profile.id().equals(selfId) || profile.name().equalsIgnoreCase(selfName)) {
				selfIndex = i;
				break;
			}
		}
		if (selfIndex <= 0) {
			return;
		}

		List<PlayerInfo> reordered = new ArrayList<>(original);
		PlayerInfo self = reordered.remove(selfIndex);
		reordered.add(0, self);
		cir.setReturnValue(reordered);
	}
}
