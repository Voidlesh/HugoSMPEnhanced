package com.hugosmpenhanced.test;

import java.util.List;
import java.util.Random;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.hugosmpenhanced.client.feature.antileak.CoordinateMask;
import com.hugosmpenhanced.client.feature.antileak.DebugCoordinates;
import com.hugosmpenhanced.client.feature.antileak.FakeCoordinates;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Anti-Leak in a test world: an open pit down to the bedrock floor with a random bedrock pattern
 * and ores in its walls, next to a patch of dirt, grass, flowers and sand. Screenshots with F3
 * open, with Anti-Leak off, on (blur), on (bar) and of the texture rotation, plus checks that the
 * masked F3 text carries no real digits.
 */
@SuppressWarnings("UnstableApiUsage")
public class AntiLeakGameTest implements FabricClientGameTest {
	private static final String[] ORES = {"coal_ore", "deepslate_iron_ore", "copper_ore", "deepslate_gold_ore", "redstone_ore",
		"diamond_ore", "deepslate_diamond_ore", "lapis_ore", "emerald_ore", "deepslate_redstone_ore", "deepslate_coal_ore"};

	@Override
	public void runTest(ClientGameTestContext context) {
		checkText();
		context.runOnClient(client -> {
			client.options.menuBackgroundBlurriness().set(0);
			AntiLeak.zonesInFlatWorlds = true;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			BlockPos p = context.computeOnClient(client -> client.player.blockPosition());
			int x = p.getX(), z = p.getZ();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 6000");
			buildScene(server, x, z);
			context.waitTicks(20);

			// Pit with ores, F3 open, looking at the diamond ore in the back wall
			server.runCommand("tp @p " + (x + 0.5) + " -54 " + (z + 1.5) + " 0 21");
			context.runOnClient(client -> {
				//? if modern
				client.debugEntries.setStatus(DebugScreenEntries.LOOKING_AT_BLOCK_STATE, DebugScreenEntryStatus.IN_OVERLAY);
				//? else
				client.debugEntries.setStatus(DebugScreenEntries.LOOKING_AT_BLOCK, DebugScreenEntryStatus.IN_OVERLAY);
				//? end
				client.debugEntries.setOverlayVisible(true);
			});
			setAntiLeak(context, false, CoordinateMask.BLUR);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("antileak-1-aus");

			setAntiLeak(context, true, CoordinateMask.BLUR);
			context.waitTicks(5);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("antileak-2-an");
			checkRendering(context, x, z);

			setAntiLeak(context, true, CoordinateMask.BAR);
			context.waitTicks(5);
			context.takeScreenshot("antileak-3-balken");
			setAntiLeak(context, true, CoordinateMask.RANDOM);
			context.waitTicks(5);
			context.takeScreenshot("antileak-3c-zufall");

			// The pit itself, without F3 in front of it
			context.runOnClient(client -> client.debugEntries.setOverlayVisible(false));
			context.waitTicks(3);
			context.takeScreenshot("antileak-3b-grube-an");
			setAntiLeak(context, false, CoordinateMask.BLUR);
			context.waitTicks(5);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("antileak-3a-grube-aus");

			// Texture rotation and flower offsets on the patch behind the pit
			// Back to a plain superflat world: no bedrock layers, so the ground stays as it is
			context.runOnClient(client -> AntiLeak.zonesInFlatWorlds = false);
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p " + (x - 2.5) + " -45 " + (z - 0.5) + " 180 72");
			setAntiLeak(context, false, CoordinateMask.BLUR);
			context.waitTicks(5);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);
			//? if modern
			context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
			//? else
			context.runOnClient(client -> client.gui.getChat().clearMessages(false));
			//? end
			context.takeScreenshot("antileak-4-rotation-aus");
			setAntiLeak(context, true, CoordinateMask.BLUR);
			context.waitTicks(5);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("antileak-5-rotation-an");

			server.runCommand("gamemode survival @p");
			setAntiLeak(context, false, CoordinateMask.BLUR);
			context.runOnClient(client -> ModConfigManager.CONFIG.antiLeakOres = false);
			int guiScale = context.computeOnClient(client -> client.options.guiScale().get());
			context.runOnClient(client -> client.options.guiScale().set(1));
			context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Anti-Leak"));
			context.waitTicks(3);
			context.takeScreenshot("antileak-6-einstellungen");
			context.setScreen(() -> null);
			context.runOnClient(client -> client.options.guiScale().set(guiScale));

			context.runOnClient(client -> {
				ModConfig config = ModConfigManager.CONFIG;
				config.antiLeakEnabled = false;
				config.antiLeakOres = new ModConfig().antiLeakOres;
				config.antiLeakF3Style = CoordinateMask.BLUR;
				AntiLeak.zonesInFlatWorlds = false;
			});
		}
	}

	private static void setAntiLeak(ClientGameTestContext context, boolean on, CoordinateMask style) {
		context.runOnClient(client -> {
			ModConfig config = ModConfigManager.CONFIG;
			config.antiLeakEnabled = on;
			config.antiLeakOres = true;
			config.antiLeakF3Style = style;
		});
	}

	/**
	 * Flat test world, so the ground is at -61 and the bedrock floor at -64: a deepslate block
	 * from -63 to -55 with an open pit down to the floor in it.
	 */
	private static void buildScene(TestServerContext server, int x, int z) {
		server.runCommand(fill(x - 16, -63, z - 10, x + 16, -55, z + 28, "deepslate"));
		server.runCommand(fill(x - 6, -63, z + 4, x + 6, -55, z + 14, "air"));
		// Bedrock pattern like the generator's: denser towards the floor
		Random random = new Random(7);
		double[] chance = {0.8, 0.6, 0.4, 0.2};
		for (int bx = x - 6; bx <= x + 6; bx++) {
			for (int bz = z + 4; bz <= z + 14; bz++) {
				for (int layer = 0; layer < 4; layer++) {
					if (random.nextDouble() < chance[layer]) {
						server.runCommand("setblock " + bx + " " + (-63 + layer) + " " + bz + " bedrock");
					}
				}
			}
		}
		for (int i = 0; i < ORES.length; i++) {
			server.runCommand("setblock " + (x - 5 + i) + " -58 " + (z + 15) + " " + ORES[i]);
			server.runCommand("setblock " + (x - 5 + i) + " -57 " + (z + 15) + " " + ORES[ORES.length - 1 - i]);
		}
		server.runCommand("setblock " + x + " -58 " + (z + 15) + " diamond_ore");
		server.runCommand("setblock " + (x - 7) + " -59 " + (z + 8) + " iron_ore");
		// Tuff patch with ores in the right wall: they should take the look of the tuff
		server.runCommand(fill(x + 7, -59, z + 5, x + 7, -56, z + 9, "tuff"));
		server.runCommand("setblock " + (x + 7) + " -58 " + (z + 7) + " diamond_ore");
		server.runCommand("setblock " + (x + 7) + " -57 " + (z + 7) + " copper_ore");
		server.runCommand("setblock " + (x + 7) + " -57 " + (z + 6) + " coal_ore");
		server.runCommand("setblock " + (x + 7) + " -60 " + (z + 10) + " gold_ore");
		// Patch for the texture rotation: dirt, grass with flowers, sand
		server.runCommand(fill(x - 9, -55, z - 10, x - 3, -55, z + 2, "dirt"));
		server.runCommand(fill(x - 2, -55, z - 10, x + 3, -55, z + 2, "grass_block"));
		server.runCommand(fill(x + 4, -55, z - 10, x + 10, -55, z + 2, "sand"));
		String[] plants = {"short_grass", "dandelion", "poppy", "short_grass", "azure_bluet", "short_grass"};
		for (int px = x - 2; px <= x + 3; px++) {
			for (int pz = z - 10; pz <= z - 3; pz++) {
				server.runCommand("setblock " + px + " -54 " + pz + " " + plants[Math.floorMod(px * 7 + pz * 3, plants.length)]);
			}
		}
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
	}

	/** The bedrock layers and ores come out disguised, and rotation seeds are fixed. */
	private static void checkRendering(ClientGameTestContext context, int x, int z) {
		context.runOnClient(client -> {
			expect(AntiLeak.disguise(Blocks.DIAMOND_ORE.defaultBlockState(), null, x, -58, z), Blocks.STONE, "diamond ore");
			expect(AntiLeak.disguise(Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), null, x, -58, z), Blocks.DEEPSLATE, "deepslate gold ore");
			expect(AntiLeak.disguise(Blocks.NETHER_QUARTZ_ORE.defaultBlockState(), null, x, 40, z), Blocks.NETHERRACK, "quartz ore");
			expect(AntiLeak.disguise(Blocks.AIR.defaultBlockState(), null, x, -61, z), Blocks.BEDROCK, "air in the pattern layers");
			expect(AntiLeak.disguise(Blocks.DEEPSLATE.defaultBlockState(), null, x, -60, z), Blocks.BEDROCK, "deepslate in the pattern layers");
			expect(AntiLeak.disguise(Blocks.AIR.defaultBlockState(), null, x, -59, z), Blocks.AIR, "air above the pattern layers");
			expect(AntiLeak.disguise(Blocks.CHEST.defaultBlockState(), null, x, -61, z), Blocks.CHEST, "a chest in the pattern layers");
			// Ores in the tuff patch of the right wall look like tuff, also next to another ore
			expect(AntiLeak.disguise(client.level.getBlockState(new BlockPos(x + 7, -58, z + 7)), client.level, x + 7, -58, z + 7),
				Blocks.TUFF, "diamond ore in tuff");
			expect(AntiLeak.disguise(client.level.getBlockState(new BlockPos(x + 7, -57, z + 7)), client.level, x + 7, -57, z + 7),
				Blocks.TUFF, "copper ore on top of it");
			long a = Blocks.DIRT.defaultBlockState().getSeed(new BlockPos(x, -55, z));
			long b = Blocks.DIRT.defaultBlockState().getSeed(new BlockPos(x + 13, -55, z - 5));
			if (a != b) {
				throw new AssertionError("Dirt still has a position-dependent model seed");
			}
			if (!Blocks.POPPY.defaultBlockState().getOffset(new BlockPos(x + 3, -54, z - 7)).equals(net.minecraft.world.phys.Vec3.ZERO)) {
				throw new AssertionError("Poppy is still offset");
			}
		});
	}

	private static void expect(BlockState actual, net.minecraft.world.level.block.Block block, String what) {
		if (!actual.is(block)) {
			throw new AssertionError(what + " was drawn as " + actual);
		}
	}

	/** No digit of a masked number survives in the visible text, only the height when it is wanted. */
	private static void checkText() {
		checkRandom();
		visible("XYZ: 12345.678 / 64.00000 / -9876.543", true, "64.00000", 2);
		visible("XYZ: 12345.678 / 64.00000 / -9876.543", false, "", 3);
		visible("Block: 12345 64 -9876", true, "64", 2);
		visible("Chunk: 771 4 -618 [3 22 in r.24.-20.mca]", true, "4", 6);
		visible("Section-relative: 09 00 12", true, "00", 2);
		visible("\u00a7nTargeted Block: 12345, -58, -9876", false, "", 3);
		visible("Facing: south (Towards positive Z) (0.0 / 21.0)", true, "0.0 / 21.0", 0);
	}

	/** Made-up coordinates are never the real ones and agree with each other (chunk = block / 16). */
	private static void checkRandom() {
		String x = FakeCoordinates.value(FakeCoordinates.Kind.BLOCK_X, "123456789");
		String chunk = FakeCoordinates.value(FakeCoordinates.Kind.CHUNK_X, "7716049");
		String pos = FakeCoordinates.value(FakeCoordinates.Kind.POS_X, "123456789,123");
		if (x.equals("123456789") || Integer.parseInt(chunk) != Integer.parseInt(x) >> 4 || !pos.matches("-?\\d+,\\d{3}")) {
			throw new AssertionError("Random coordinates: block " + x + ", chunk " + chunk + ", position " + pos);
		}
	}

	private static void visible(String line, boolean showY, String kept, int maskedCount) {
		List<DebugCoordinates.Part> parts = DebugCoordinates.split(line, showY);
		StringBuilder text = new StringBuilder();
		int masked = 0;
		for (DebugCoordinates.Part part : parts) {
			if (part.masked()) {
				masked++;
			} else {
				//? if modern
				text.append(part.text());
				//? else
				text.append(part.text());
				//? end
			}
		}
		String rest = text.toString().replace("\u00a7n", "").replace(kept, "");
		if (masked != maskedCount || rest.matches(".*\\d.*")) {
			throw new AssertionError("Masked F3 line \"" + line + "\" shows \"" + text + "\" with " + masked + " masked numbers");
		}
	}
}
