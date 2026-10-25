package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.settings.SettingsFeature;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/** The settings key opens the menu before other key bindings on the same key get the press. */
@Mixin(value = KeyboardHandler.class, priority = 900)
public abstract class SettingsKeyMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$openSettings(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == 1 && window == Minecraft.getInstance().getWindow().handle() && SettingsFeature.onKeyPress(event)) {
			ci.cancel();
		}
	}
}
