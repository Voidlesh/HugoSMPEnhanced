package com.hugosmpenhanced.test;

import com.hugosmpenhanced.client.feature.colorcodes.ColorText;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A stand-in for a server's /rename in the singleplayer test world: names the held item with
 * {@code &} codes read like Essentials-style plugins do (including {@code &#RRGGBB}).
 */
public class TestRenameServer implements ModInitializer {
	public static String lastRename;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
			dispatcher.register(Commands.literal("rename")
				.then(Commands.argument("name", StringArgumentType.greedyString()).executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					String name = StringArgumentType.getString(context, "name");
					lastRename = name;
					ItemStack stack = player.getMainHandItem();
					Component component = ColorText.parse(name).toComponent().copy().withStyle(style -> style.withItalic(false));
					stack.set(DataComponents.CUSTOM_NAME, component);
					player.sendSystemMessage(Component.literal("Umbenannt in ").append(component));
					return 1;
				}))));
	}
}
