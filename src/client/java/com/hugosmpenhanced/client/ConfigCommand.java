package com.hugosmpenhanced.client;

import com.hugosmpenhanced.client.config.ConfigScreenBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

public final class ConfigCommand {
	private ConfigCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("hugosmpenhanced")
						.executes(context -> {
							Minecraft client = Minecraft.getInstance();
							client.gui.setScreen(ConfigScreenBuilder.build(client.gui.screen()));
							return 1;
						})));
	}
}
