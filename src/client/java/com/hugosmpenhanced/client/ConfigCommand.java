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
							client.setScreen(ConfigScreenBuilder.build(client.screen));
							return 1;
						})));
	}
}
