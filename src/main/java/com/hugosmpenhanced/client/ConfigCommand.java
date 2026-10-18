package com.hugosmpenhanced.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.feature.debug.DebugDump;
import com.hugosmpenhanced.client.feature.discord.DiscordPresence;
import com.hugosmpenhanced.client.feature.update.ModUpdates;
import com.hugosmpenhanced.client.hud.HudEditorScreen;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** {@code /hugosmpenhanced} and its short form {@code /hse}, with one subcommand per tool. */
public final class ConfigCommand {
	private static final List<Consumer<LiteralArgumentBuilder<FabricClientCommandSource>>> EXTRA = new ArrayList<>();

	private ConfigCommand() {
	}

	/** Lets a feature add its own subcommand, e.g. {@code /hse geld}. Call before the game joins a world. */
	public static void addSubcommand(Consumer<LiteralArgumentBuilder<FabricClientCommandSource>> subcommand) {
		EXTRA.add(subcommand);
	}

	/**
	 * Opens a screen once the chat screen that ran the command has closed; called inline, the chat
	 * screen would close the new screen right away.
	 */
	public static int open(Function<Screen, Screen> screen) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreenAndShow(screen.apply(null)));
		return 1;
	}

	public static int run(Runnable action) {
		Minecraft.getInstance().execute(action);
		return 1;
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(tree("hugosmpenhanced"));
			dispatcher.register(tree("hse"));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(name)
			.then(ClientCommands.literal("discord").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				return run(() -> DiscordPresence.printDiagnosis(client));
			}))
			.then(ClientCommands.literal("update").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				return run(() -> ModUpdates.openOrCheck(client));
			}))
			.then(ClientCommands.literal("hud").executes(context -> open(HudEditorScreen::new)))
			.then(ClientCommands.literal("debug")
				.executes(context -> run(DebugDump::all))
				.then(ClientCommands.literal("chat")
					.executes(context -> run(() -> DebugDump.chat(20)))
					.then(ClientCommands.argument("zeilen", IntegerArgumentType.integer(1, 200))
						.executes(context -> {
							int lines = IntegerArgumentType.getInteger(context, "zeilen");
							return run(() -> DebugDump.chat(lines));
						})))
				.then(ClientCommands.literal("item").executes(context -> run(DebugDump::item)))
				.then(ClientCommands.literal("menu").executes(context -> run(DebugDump::menu)))
				.then(ClientCommands.literal("scoreboard").executes(context -> run(DebugDump::sidebar))))
			.executes(context -> open(parent -> new SettingsScreen(Minecraft.getInstance().gui.screen())));
		for (Consumer<LiteralArgumentBuilder<FabricClientCommandSource>> extra : EXTRA) {
			extra.accept(root);
		}
		return root;
	}
}
