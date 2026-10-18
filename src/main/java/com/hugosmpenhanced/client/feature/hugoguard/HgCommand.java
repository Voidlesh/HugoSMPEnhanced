package com.hugosmpenhanced.client.feature.hugoguard;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;

/** {@code /hg <name>} opens the player's {@link HgProfileScreen}; without a name, your own. */
public final class HgCommand {
	private HgCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal("hg")
				.executes(context -> open(Minecraft.getInstance().getUser().getName()))
				.then(ClientCommands.argument("name", StringArgumentType.word())
					.suggests((context, builder) -> {
						var connection = Minecraft.getInstance().getConnection();
						if (connection != null) {
							SharedSuggestionProvider.suggest(connection.getOnlinePlayers().stream()
								.map(PlayerInfo::getProfile).map(profile -> profile.name()), builder);
						}
						return builder.buildFuture();
					})
					.executes(context -> open(StringArgumentType.getString(context, "name"))))));
	}

	private static int open(String name) {
		Minecraft client = Minecraft.getInstance();
		// Deferred: the chat screen closes itself right after running the command.
		client.execute(() -> client.setScreenAndShow(new HgProfileScreen(null, name)));
		return 1;
	}
}
