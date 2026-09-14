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

		List<PlayerInfo> original = cir.getReturnValue();
		int selfIndex = -1;
		for (int i = 0; i < original.size(); i++) {
			if (original.get(i).getProfile().id().equals(selfId)) {
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
