package com.hugosmpenhanced.client.feature.spawnerprices;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

/** Spawner prices: loading, the tooltip on spawner items and {@code /spawnerpreise}. */
public final class SpawnerPricesFeature {
	private SpawnerPricesFeature() {
	}

	public static void register() {
		SpawnerPrices.register();
		SpawnerPricesTooltip.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String name : new String[] {"spawnerpreise", "spawnerprices"}) {
				dispatcher.register(ClientCommands.literal(name).executes(context -> {
					Minecraft client = Minecraft.getInstance();
					// Deferred: the chat screen closes itself right after running the command.
					client.execute(() -> client.setScreenAndShow(new SpawnerPricesScreen(null)));
					return 1;
				}));
			}
		});
	}
}
