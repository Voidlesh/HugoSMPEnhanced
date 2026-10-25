package com.hugosmpenhanced.test;

import java.util.UUID;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.tpa.TpaRequestHandler;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Loads the real HugoGuard scammer list, then shows a listed scammer as a player in front of the
 * camera, chat lines from and about that player, and the TPA warning, with screenshots of each.
 */
@SuppressWarnings("UnstableApiUsage")
public class HugoGuardGameTest implements FabricClientGameTest {
	//? if modern
	private static final java.util.concurrent.atomic.AtomicInteger FAKE_ID = new java.util.concurrent.atomic.AtomicInteger(900000);

	//? end
	@Override
	public void runTest(ClientGameTestContext context) {
		boolean loaded = HugoGuard.fetchAll();
		HugoGuard.forceActive = true;
		ModConfigManager.CONFIG.hugoGuardEnabled = true;
		HugoGuard.Entry scammer = context.computeOnClient(client -> HugoGuard.scammer("StepOnMyBrick"));
		System.out.println("HUGOGUARD loaded=" + loaded + " entry=" + scammer);
		if (scammer == null) {
			throw new AssertionError("HugoGuard list did not contain the expected scammer");
		}

		// No menu blur and a flat world, so the world loads in time under software rendering.
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> {
				var self = client.player;
				Vec3 look = self.getLookAngle();
				Vec3 at = self.position().add(look.x * 4, 0, look.z * 4);
				spawn(client, scammer.uuid(), scammer.username(), at, self.getYRot() + 180);
				spawn(client, UUID.randomUUID(), "Ehrlich", at.add(look.z * 2.5, 0, -look.x * 2.5), self.getYRot() + 180);
				//? if modern
				client.gui.hud.getChat().addClientSystemMessage(Component.literal("[Clan] [VIP] " + scammer.username() + ": verkaufe Elytra, zahl zuerst"));
				client.gui.hud.getChat().addClientSystemMessage(Component.literal("Ehrlich: Vorsicht vor " + scammer.username()));
				client.gui.hud.getChat().addClientSystemMessage(Component.literal("[Nachricht] " + scammer.username() + " -> Du: komm tp"));
				//? else
				client.gui.getChat().addMessage(Component.literal("[Clan] [VIP] " + scammer.username() + ": verkaufe Elytra, zahl zuerst"));
				client.gui.getChat().addMessage(Component.literal("Ehrlich: Vorsicht vor " + scammer.username()));
				client.gui.getChat().addMessage(Component.literal("[Nachricht] " + scammer.username() + " -> Du: komm tp"));
				//? end
			});
			context.waitTicks(10);
			context.takeScreenshot("hugoguard-overhead");

			context.runOnClient(client -> TpaRequestHandler.warnScammer(scammer));
			context.waitTicks(10);
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(5);
			context.takeScreenshot("hugoguard-chat-tpa");
			context.setScreen(() -> null);
		}
		HugoGuard.forceActive = false;
	}

	private static void spawn(net.minecraft.client.Minecraft client, UUID uuid, String name, Vec3 at, float yaw) {
		RemotePlayer player = new RemotePlayer(client.level, new GameProfile(uuid, name));
		player.snapTo(at.x, at.y, at.z, yaw, 0);
		player.setYHeadRot(yaw);
		//? if modern
		player.setId(FAKE_ID.getAndIncrement());
		//? end
		client.level.addEntity(player);
	}
}
