package com.hugosmpenhanced.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
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
 * Shows the clan lines above four players: a look-alike of a real clan, a player from a real clan, a
 * manual fake, and a HugoGuard scammer from a real clan (both lines stacked). Takes a screenshot at
 * the normal size and one at a larger overhead scale.
 */
@SuppressWarnings("UnstableApiUsage")
public class FakeClanGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		ModConfigManager.CONFIG.fakeClanWarning = true;
		ModConfigManager.CONFIG.trustedClanOverhead = true;
		ModConfigManager.CONFIG.hugoGuardOverhead = true;
		ModConfigManager.CONFIG.overheadScale = 1.0;
		ModConfigManager.CONFIG.fakeClanReal = new ArrayList<>(List.of("Vanguard", "Nova"));
		ModConfigManager.CONFIG.fakeClanFake = new ArrayList<>(List.of("Hexen"));
		ServerCheck.forceHugoSmp = true;
		HugoGuard.forceActive = true;
		HugoGuard.putForTest(new HugoGuard.Entry("Schwindler", UUID.nameUUIDFromBytes("Schwindler".getBytes()),
			HugoGuard.Status.SCAMMER, 3, 0, null));

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
				float yaw = self.getYRot() + 180;
				spawn(client, "Faelscher", at.add(-4.4, 0, 0), yaw, "Vangu4rd", " [Admin]");
				spawn(client, "Echter", at.add(-1.5, 0, 0), yaw, "Vanguard", "");
				spawn(client, "Hexer", at.add(1.5, 0, 0), yaw, "Hexen", "");
				spawn(client, "Schwindler", at.add(4.4, 0, 0), yaw, "Vanguard", "");
			});
			context.waitTicks(10);
			context.takeScreenshot("fakeclan-overhead");

			context.runOnClient(client -> ModConfigManager.CONFIG.overheadScale = 1.6);
			context.waitTicks(10);
			context.takeScreenshot("fakeclan-overhead-scale");
		}
		ServerCheck.forceHugoSmp = false;
		HugoGuard.forceActive = false;
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
