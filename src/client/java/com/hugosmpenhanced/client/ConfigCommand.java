package com.hugosmpenhanced.client;

import com.hugosmpenhanced.client.config.ConfigScreenBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

public final class ConfigCommand {
	private ConfigCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("hugosmpenhanced")
						.executes(context -> {
							Minecraft client = Minecraft.getInstance();
							// Deferred: the chat screen closes itself right after dispatching this
							// command, which would immediately undo a setScreen() called inline here.
							client.execute(() -> client.setScreen(ConfigScreenBuilder.build(client.screen)));
							return 1;
						})));
	}
}
