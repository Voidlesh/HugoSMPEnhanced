package com.hugosmpenhanced.client.feature.mainmenu;

import com.terraformersmc.modmenu.gui.ModsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Kept apart so Mod Menu's classes only load when Mod Menu is installed and the button is used. */
final class ModMenuLink {
	private ModMenuLink() {
	}

	static void open(Screen parent) {
		Minecraft.getInstance().setScreenAndShow(new ModsScreen(parent));
	}
}
