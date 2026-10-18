package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.quickjoin.QuickJoinAction;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenQuickJoinMixin extends Screen {
	protected TitleScreenQuickJoinMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void hugosmpenhanced$addQuickJoinButton(CallbackInfo ci) {
		if (!ModConfigManager.CONFIG.mainMenuButtonEnabled) {
			return;
		}

		this.addRenderableWidget(Button.builder(
						Component.literal("HugoSMP.net beitreten"),
						button -> QuickJoinAction.join((Screen) (Object) this))
				.bounds(4, this.height - 24, 120, 20)
				.build());
	}
}
