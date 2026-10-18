package com.hugosmpenhanced.client.feature.spawnerstorage;

import com.hugosmpenhanced.client.ConfigCommand;
import com.hugosmpenhanced.client.hud.HudManager;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

/** Spawner storage reminder, its HUD element and {@code /hse spawner}. */
public final class SpawnerStorageFeature {
	private SpawnerStorageFeature() {
	}

	public static void register() {
		SpawnerStorage.register();
		HudManager.add(SpawnerHud.INSTANCE);
		ConfigCommand.addSubcommand(root -> root.then(ClientCommandManager.literal("spawner")
			.executes(context -> ConfigCommand.open(SpawnerStorageScreen::new))));
	}
}
