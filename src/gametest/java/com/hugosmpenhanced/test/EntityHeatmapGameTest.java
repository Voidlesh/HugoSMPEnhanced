package com.hugosmpenhanced.test;

import java.util.List;

import com.hugosmpenhanced.client.feature.entityheatmap.EntityHeatmap;

import com.hugosmpenhanced.client.settings.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Builds a small mob farm floor (hoppers in stone) and checks the entity heatmap: counts per
 * block, the type list on a mixed block, and that it shows through glass and stained glass but not
 * through a stone wall. Also opens the config screen scrolled to the heatmap options.
 */
@SuppressWarnings("UnstableApiUsage")
public class EntityHeatmapGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			BlockPos p = context.computeOnClient(client -> client.player.blockPosition());
			int x = p.getX(), y = p.getY(), z = p.getZ();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 6000");
			server.runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z + 3) + " " + (x + 4) + " " + (y - 1) + " " + (z + 5) + " smooth_stone");
			// Hoppers only under the mob-only blocks, so the drops stay on the floor.
			server.runCommand("fill " + (x - 3) + " " + (y - 1) + " " + (z + 4) + " " + (x - 1) + " " + (y - 1) + " " + (z + 4) + " hopper");
			// Mixed block in front: husks, cave spiders and a few drops.
			summon(server, "husk", x + 0.5, y, z + 4.5, 16);
			summon(server, "cave_spider", x + 0.5, y, z + 4.5, 3);
			item(server, x + 0.5, y, z + 4.5, "rotten_flesh", 4);
			// Only drops, only chickens, and a block at the cramming limit (one more and they take damage).
			item(server, x + 1.5, y, z + 4.5, "rotten_flesh", 12);
			item(server, x + 1.5, y, z + 4.5, "bone", 5);
			item(server, x + 1.5, y, z + 4.5, "arrow", 7);
			summon(server, "chicken", x - 0.5, y, z + 4.5, 6);
			summon(server, "husk", x - 1.5, y, z + 4.5, 24);
			server.runCommand("tp @p " + (x + 0.5) + " " + y + " " + (z - 1.0) + " 0 15");
			context.runOnClient(client -> EntityHeatmap.shown = true);
			context.waitTicks(40);

			List<EntityHeatmap.Cell> cells = context.computeOnClient(EntityHeatmap::collect);
			System.out.println("HEATMAP cells: " + cells.stream()
				.map(c -> c.pos().getX() - x + ":" + c.total() + "/" + c.mobs() + "/" + c.groups().size()).toList());
			check(cells, x + 0, 20, 19, 3);
			check(cells, x + 1, 3, 0, 3);
			check(cells, x - 1, 6, 6, 1);
			check(cells, x - 2, 24, 24, 1);
			context.takeScreenshot("heatmap-open");

			server.runCommand("fill " + (x - 4) + " " + y + " " + (z + 2) + " " + (x + 4) + " " + (y + 2) + " " + (z + 2) + " glass");
			context.waitTicks(10);
			context.takeScreenshot("heatmap-glass");
			server.runCommand("fill " + (x - 4) + " " + y + " " + (z + 2) + " " + (x + 4) + " " + (y + 2) + " " + (z + 2) + " light_blue_stained_glass");
			context.waitTicks(10);
			context.takeScreenshot("heatmap-stained-glass");
			server.runCommand("fill " + (x - 4) + " " + y + " " + (z + 2) + " " + (x + 4) + " " + (y + 2) + " " + (z + 2) + " stone_bricks");
			context.waitTicks(10);
			context.takeScreenshot("heatmap-wall");
			context.runOnClient(client -> EntityHeatmap.shown = false);

			context.setScreen(() -> new SettingsScreen(null).select("Werkzeuge", "Heatmap"));
			context.waitTicks(3);
			context.takeScreenshot("heatmap-config");
			context.setScreen(() -> null);
		}
	}

	private static void summon(TestServerContext server, String type, double x, double y, double z, int count) {
		for (int i = 0; i < count; i++) {
			server.runCommand("summon " + type + " " + x + " " + y + " " + z + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
		}
	}

	private static void item(TestServerContext server, double x, double y, double z, String id, int count) {
		server.runCommand("summon item " + x + " " + y + " " + z
			+ " {Item:{id:\"" + id + "\",count:" + count + "},PickupDelay:32767,Age:-32768,Motion:[0.0,0.0,0.0]}");
	}

	private static void check(List<EntityHeatmap.Cell> cells, int blockX, int total, int mobs, int groups) {
		EntityHeatmap.Cell cell = cells.stream().filter(c -> c.pos().getX() == blockX).findFirst()
			.orElseThrow(() -> new AssertionError("No heatmap cell at x=" + blockX));
		if (cell.total() != total || cell.mobs() != mobs || cell.groups().size() != groups) {
			throw new AssertionError("Heatmap cell at x=" + blockX + " was " + cell);
		}
	}
}
