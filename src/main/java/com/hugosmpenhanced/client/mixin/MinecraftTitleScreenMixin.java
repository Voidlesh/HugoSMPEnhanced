package com.hugosmpenhanced.client.mixin;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.mainmenu.HugoTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shows {@link HugoTitleScreen} wherever the game opens the vanilla title screen, while it is enabled. */
@Mixin(Minecraft.class)
public abstract class MinecraftTitleScreenMixin {
	@ModifyVariable(method = "setScreenAndShow", at = @At("HEAD"), argsOnly = true)
	private Screen hugosmpenhanced$replaceTitleScreen(Screen screen) {
		if (screen != null && screen.getClass() == TitleScreen.class && ModConfigManager.CONFIG.mainMenuCustom) {
			return new HugoTitleScreen();
		}
		return screen;
	}
}
