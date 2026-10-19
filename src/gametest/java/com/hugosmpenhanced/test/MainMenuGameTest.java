package com.hugosmpenhanced.test;

import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.mainmenu.HugoTitleScreen;
import com.hugosmpenhanced.client.feature.mainmenu.MainMenuLayout;
import com.hugosmpenhanced.client.feature.mainmenu.MenuButton;
import com.hugosmpenhanced.client.feature.mainmenu.SplashAnimation;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** The HugoSMP title screen in both layouts, its layout switch, the splash animations and its config options. */
public class MainMenuGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.mainMenuCustom = true;
			ModConfigManager.CONFIG.mainMenuLayout = MainMenuLayout.CLASSIC;
			ModConfigManager.CONFIG.splashAnimation = SplashAnimation.PULSE;
		});
		context.setScreen(TitleScreen::new);
		context.waitTicks(10);
		boolean replaced = context.computeOnClient(client -> client.gui.screen() instanceof HugoTitleScreen);
		if (!replaced) {
			throw new AssertionError("Title screen was not replaced by HugoTitleScreen");
		}
		context.takeScreenshot("menu-classic");

		// press the layout switch in the top right corner like a player would
		context.runOnClient(client -> {
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof MenuButton button && button.getMessage().getString().startsWith("⇄")) {
					client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX() + 2, button.getY() + 2,
							new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
					return;
				}
			}
			throw new AssertionError("Layout switch button missing");
		});
		context.waitTicks(5);
		MainMenuLayout layout = context.computeOnClient(client -> ModConfigManager.CONFIG.mainMenuLayout);
		if (layout != MainMenuLayout.SIDEBAR) {
			throw new AssertionError("Layout switch did not switch to the sidebar, got " + layout);
		}
		context.takeScreenshot("menu-sidebar");
		context.waitTicks(100);
		context.takeScreenshot("menu-sidebar-ping");

		context.runOnClient(client -> ModConfigManager.CONFIG.splashAnimation = SplashAnimation.RAINBOW);
		context.waitTicks(3);
		context.takeScreenshot("menu-sidebar-rainbow");
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.splashAnimation = SplashAnimation.WOBBLE;
			ModConfigManager.CONFIG.mainMenuLayout = MainMenuLayout.CLASSIC;
		});
		context.setScreen(TitleScreen::new);
		context.waitTicks(15);
		context.takeScreenshot("menu-classic-wobble");

		context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Hauptmenü"));
		context.waitTicks(3);
		context.runOnClient(client -> {
			for (int i = 0; i < 60; i++) {
				client.gui.screen().mouseScrolled(client.gui.screen().width / 2.0, client.gui.screen().height / 2.0, 0, -5);
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("menu-config");

		context.runOnClient(client -> {
			ModConfigManager.CONFIG.mainMenuCustom = false;
			ModConfigManager.CONFIG.splashAnimation = SplashAnimation.PULSE;
		});
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
		boolean vanilla = context.computeOnClient(client -> client.gui.screen() != null && client.gui.screen().getClass() == TitleScreen.class);
		if (!vanilla) {
			throw new AssertionError("Vanilla title screen expected with the custom menu off");
		}
		context.takeScreenshot("menu-vanilla");
		context.runOnClient(client -> ModConfigManager.CONFIG.mainMenuCustom = true);
		context.setScreen(TitleScreen::new);
		context.waitTicks(3);
	}
}
