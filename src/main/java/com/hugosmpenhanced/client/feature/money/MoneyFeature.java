package com.hugosmpenhanced.client.feature.money;

import com.hugosmpenhanced.client.ConfigCommand;
import com.hugosmpenhanced.client.hud.HudManager;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

/** Money tracker, its HUD element and {@code /hse geld}. */
public final class MoneyFeature {
	private MoneyFeature() {
	}

	public static void register() {
		MoneyTracker.register();
		HudManager.add(MoneyHud.INSTANCE);
		ConfigCommand.addSubcommand(root -> root.then(ClientCommandManager.literal("geld")
			.executes(context -> ConfigCommand.open(MoneyScreen::new))));
	}
}
