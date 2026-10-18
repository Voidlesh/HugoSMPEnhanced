package com.hugosmpenhanced.client.feature.farm;

import com.hugosmpenhanced.client.ConfigCommand;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.hud.HudManager;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Farm tracker, its HUD element and {@code /hse farm reset}. */
public final class FarmFeature {
	private FarmFeature() {
	}

	private static void printSummary() {
		var chat = Minecraft.getInstance().gui.hud.getChat();
		Component prefix = Component.literal("[HugoSMP Enhanced] ").withStyle(ChatFormatting.GOLD);
		if (!FarmTracker.hasData()) {
			chat.addClientSystemMessage(prefix.copy().append(Component.literal("Noch nichts aufgehoben.").withStyle(ChatFormatting.GRAY)));
			return;
		}
		chat.addClientSystemMessage(prefix.copy().append(Component.literal("Farm seit " + FarmTracker.duration(FarmTracker.activeMillis())
			+ ": " + Money.plain(FarmTracker.totalItems()) + " Items").withStyle(ChatFormatting.GRAY)));
		for (FarmTracker.Drop drop : FarmTracker.drops()) {
			double perHour = FarmTracker.perHour(drop.count);
			chat.addClientSystemMessage(Component.literal("  " + drop.example.getHoverName().getString() + " ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(Money.plain(drop.count) + (perHour < 0 ? "" : " (" + Money.plain(Math.round(perHour)) + "/h)"))
					.withStyle(ChatFormatting.GRAY)));
		}
		double value = FarmTracker.value();
		if (value > 0) {
			chat.addClientSystemMessage(Component.literal("  Wert ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Money.format(Math.round(value))).withStyle(ChatFormatting.GOLD)));
		}
	}

	public static void register() {
		FarmTracker.register();
		HudManager.add(FarmHud.INSTANCE);
		ConfigCommand.addSubcommand(root -> root.then(ClientCommands.literal("farm")
			.executes(context -> ConfigCommand.run(FarmFeature::printSummary))
			.then(ClientCommands.literal("reset").executes(context -> ConfigCommand.run(() -> {
				FarmTracker.reset();
				Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("[HugoSMP Enhanced] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal("Farm-Tracker zurückgesetzt.").withStyle(ChatFormatting.GRAY)));
			})))));
	}
}
