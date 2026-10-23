package com.hugosmpenhanced.test;

import java.util.List;
import java.util.UUID;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Shows fake-clan warnings above three players whose clan suffix comes from a scoreboard team: one
 * look-alike of a real clan, one exact real clan and one manually listed fake, with a screenshot.
 */
@SuppressWarnings("UnstableApiUsage")
public class FakeClanGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		ModConfigManager.CONFIG.fakeClanWarning = true;
		ModConfigManager.CONFIG.fakeClanReal = new java.util.ArrayList<>(List.of("Vanguard", "Nova"));
		ModConfigManager.CONFIG.fakeClanFake = new java.util.ArrayList<>(List.of("Hexen"));
		ServerCheck.forceHugoSmp = true;

		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.runOnClient(client -> {
				var self = client.player;
				Vec3 look = self.getLookAngle();
				Vec3 at = self.position().add(look.x * 4, 0, look.z * 4);
				var scoreboard = client.level.getScoreboard();
				// Look-alike of Vanguard (V4nguard would be one edit too), with a rank bracket after the clan.
				spawn(client, "Faelscher", at.add(-2.2, 0, 0), self.getYRot() + 180, "Vangu4rd", " [Admin]");
				// Exact real clan: no warning.
				spawn(client, "Echter", at, self.getYRot() + 180, "Vanguard", "");
				// Manually listed fake.
				spawn(client, "Hexer", at.add(2.2, 0, 0), self.getYRot() + 180, "Hexen", "");
			});
			context.waitTicks(10);
			context.takeScreenshot("fakeclan-overhead");
		}
		ServerCheck.forceHugoSmp = false;
	}

	private static void spawn(net.minecraft.client.Minecraft client, String name, Vec3 at, float yaw, String clan, String rank) {
		var scoreboard = client.level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(clan) == null ? scoreboard.addPlayerTeam(clan) : scoreboard.getPlayerTeam(clan);
		team.setPlayerSuffix(Component.literal(" [" + clan + "]" + rank));
		RemotePlayer player = new RemotePlayer(client.level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		player.snapTo(at.x, at.y, at.z, yaw, 0);
		player.setYHeadRot(yaw);
		client.level.addEntity(player);
		scoreboard.addPlayerToTeam(name, team);
	}
}
