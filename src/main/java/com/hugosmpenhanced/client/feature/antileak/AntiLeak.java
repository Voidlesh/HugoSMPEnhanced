package com.hugosmpenhanced.client.feature.antileak;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.mixin.ClientLevelDataAccessor;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Streamer protection: hides what lets viewers work out where you are.
 *
 * <ul>
 * <li>F3 shows X and Z (and optionally Y) blurred or blacked out; the real digits are never drawn.</li>
 * <li>Bedrock: the patterned layers above the bedrock floor (and under the nether roof) are drawn as
 * solid bedrock, so neither the texture nor the shape of the pattern is visible.</li>
 * <li>Ores look like the stone around them, so no ore pattern can be matched against a seed.</li>
 * <li>Random texture rotation (dirt, grass, sand, ...) and the random offset of flowers and grass are
 * switched off; both are a pure function of the block position.</li>
 * <li>Clouds are shifted by a secret amount per game start; their pattern is fixed to X and Z.</li>
 * </ul>
 *
 * <p>All of it is client-side and visual only: collisions, mining and what the server sees stay real.
 * The render flags are read from the chunk meshing threads, so they live in volatile fields that the
 * client tick keeps in line with the config; when they change, all chunks are rebuilt.
 */
public final class AntiLeak {
	/** Pattern layers next to the bedrock floor and roof, the floor/roof row itself stays as it is. */
	private static final int PATTERN_LAYERS = 4;
	/** The cloud texture repeats every 256 cells of 12 blocks. */
	private static final double CLOUD_PERIOD = 256 * 12;

	public static KeyMapping toggleKey;

	private static volatile boolean noRotation;
	private static volatile boolean flatBedrock;
	private static volatile boolean hideOres;
	private static volatile boolean shiftClouds;
	private static volatile boolean maskF3;

	/** Inclusive y ranges drawn as bedrock in the current dimension; empty when from > to. */
	private static volatile int floorFrom = 1;
	private static volatile int floorTo = 0;
	private static volatile int roofFrom = 1;
	private static volatile int roofTo = 0;

	private static final double CLOUD_OFFSET_X = ThreadLocalRandom.current().nextDouble(CLOUD_PERIOD);
	private static final double CLOUD_OFFSET_Z = ThreadLocalRandom.current().nextDouble(CLOUD_PERIOD);

	private static final Direction[] HOST_SEARCH = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST,
		Direction.EAST, Direction.UP};
	private static final ThreadLocal<Boolean> IN_HOST_LOOKUP = ThreadLocal.withInitial(() -> false);

	private static Map<Block, BlockState> oreDisguises = Map.of();
	private static int renderSignature = -1;
	private static DimensionType lastDimension;
	private static boolean lastFlat;
	/** Game tests only: treat superflat worlds like generated ones, to have a bedrock floor to test on. */
	public static boolean zonesInFlatWorlds;

	private AntiLeak() {
	}

	public static void register() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hugosmpenhanced.anti_leak",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			TpaKeybinds.CATEGORY));

		oreDisguises = buildOreDisguises();
		readConfig(ModConfigManager.CONFIG);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				ModConfig config = ModConfigManager.CONFIG;
				config.antiLeakEnabled = !config.antiLeakEnabled;
				ModConfigManager.save();
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.literal(
						config.antiLeakEnabled ? "§aAnti-Leak an" : "§7Anti-Leak aus"));
				}
			}
			update(client);
		});
	}

	/** Takes over config and dimension changes; rebuilds the chunks when what they show changed. */
	private static void update(Minecraft client) {
		readConfig(ModConfigManager.CONFIG);
		ClientLevel level = client.level;
		if (level == null) {
			lastDimension = null;
			renderSignature = -1;
			return;
		}
		DimensionType dimension = level.dimensionType();
		boolean flat = ((ClientLevelDataAccessor) level.getLevelData()).hugosmpenhanced$isFlat() && !zonesInFlatWorlds;
		if (dimension != lastDimension || flat != lastFlat) {
			lastDimension = dimension;
			lastFlat = flat;
			setZones(dimension, flat);
			if (flatBedrock) {
				client.levelExtractor.allChanged();
			}
		}
		int signature = (noRotation ? 1 : 0) | (flatBedrock ? 2 : 0) | (hideOres ? 4 : 0);
		if (renderSignature != -1 && signature != renderSignature) {
			client.levelExtractor.allChanged();
		}
		renderSignature = signature;
	}

	private static void readConfig(ModConfig config) {
		boolean on = config.antiLeakEnabled;
		maskF3 = on && config.antiLeakF3;
		noRotation = on && config.antiLeakNoRotation;
		flatBedrock = on && config.antiLeakBedrock;
		hideOres = on && config.antiLeakOres;
		shiftClouds = on && config.antiLeakClouds;
	}

	private static void setZones(DimensionType dimension, boolean flat) {
		int minY = dimension.minY();
		if (flat) {
			// Superflat worlds have no pattern, and their ground often lies inside these layers
			floorFrom = 1;
			floorTo = 0;
			roofFrom = 1;
			roofTo = 0;
		} else if (dimension.hasCeiling()) {
			// Nether: bedrock floor at the bottom, roof at the top of the logical height
			int roof = minY + dimension.logicalHeight() - 1;
			floorFrom = minY + 1;
			floorTo = minY + PATTERN_LAYERS;
			roofFrom = roof - PATTERN_LAYERS;
			roofTo = roof - 1;
		} else if (minY < 0) {
			// Overworld-like worlds (also HugoSMP's farm world): bedrock floor only
			floorFrom = minY + 1;
			floorTo = minY + PATTERN_LAYERS;
			roofFrom = 1;
			roofTo = 0;
		} else {
			// The End and void worlds have no bedrock pattern
			floorFrom = 1;
			floorTo = 0;
			roofFrom = 1;
			roofTo = 0;
		}
	}

	private static Map<Block, BlockState> buildOreDisguises() {
		Map<Block, BlockState> map = new IdentityHashMap<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (!Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
				continue;
			}
			String path = id.getPath();
			if (path.equals("ancient_debris") || path.startsWith("nether_") && path.endsWith("_ore")) {
				map.put(block, Blocks.NETHERRACK.defaultBlockState());
			} else if (path.startsWith("deepslate_") && path.endsWith("_ore")) {
				map.put(block, Blocks.DEEPSLATE.defaultBlockState());
			} else if (path.endsWith("_ore")) {
				map.put(block, Blocks.STONE.defaultBlockState());
			}
		}
		return map;
	}

	// ------------------------------------------------------------------ used by the mixins

	public static boolean maskF3() {
		return maskF3;
	}

	public static boolean showY() {
		return ModConfigManager.CONFIG.antiLeakShowY;
	}

	public static CoordinateMask maskStyle() {
		return ModConfigManager.CONFIG.antiLeakF3Style;
	}

	public static boolean noRotation() {
		return noRotation;
	}

	/**
	 * What chunk meshing should draw at this position instead of the real block. {@code world} is
	 * where the block was read from; ores take the look of the stone next to them (granite, tuff, ...)
	 * so they blend in, and it may be null where there are no neighbours to ask.
	 */
	public static BlockState disguise(BlockState state, BlockGetter world, int x, int y, int z) {
		if (flatBedrock && (y >= floorFrom && y <= floorTo || y >= roofFrom && y <= roofTo) && isNaturalFill(state)) {
			return Blocks.BEDROCK.defaultBlockState();
		}
		if (hideOres) {
			BlockState ore = oreDisguises.get(state.getBlock());
			if (ore != null) {
				if (world == null) {
					return ore;
				}
				// Asked by a neighbouring ore looking for its host: answer as an ore, so it looks further
				return IN_HOST_LOOKUP.get() ? state : host(ore, world, x, y, z);
			}
		}
		return state;
	}

	/** The first natural stone around an ore, so a diamond in tuff looks like tuff; ores around it are skipped. */
	private static BlockState host(BlockState fallback, BlockGetter world, int x, int y, int z) {
		IN_HOST_LOOKUP.set(true);
		try {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (Direction direction : HOST_SEARCH) {
				BlockState neighbour = world.getBlockState(pos.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()));
				if (neighbour.is(BlockTags.BASE_STONE_OVERWORLD) || neighbour.is(BlockTags.BASE_STONE_NETHER)) {
					return neighbour.getBlock().defaultBlockState();
				}
			}
			return fallback;
		} finally {
			IN_HOST_LOOKUP.set(false);
		}
	}

	/** Ore particles (mining, breaking) look like the stone they are disguised as. */
	public static BlockState disguiseParticle(BlockState state) {
		if (hideOres) {
			BlockState ore = oreDisguises.get(state.getBlock());
			if (ore != null) {
				return ore;
			}
		}
		return state;
	}

	/** Shifts the camera position the cloud pattern is computed from, never the clouds' height. */
	public static double cloudShiftX() {
		return shiftClouds ? CLOUD_OFFSET_X : 0;
	}

	public static double cloudShiftZ() {
		return shiftClouds ? CLOUD_OFFSET_Z : 0;
	}

	/** Blocks the world generator leaves in the bedrock layers; player builds there stay visible. */
	private static boolean isNaturalFill(BlockState state) {
		Block block = state.getBlock();
		return state.isAir()
			|| block == Blocks.BEDROCK
			|| state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(BlockTags.BASE_STONE_NETHER)
			|| block instanceof LiquidBlock
			|| block == Blocks.MAGMA_BLOCK
			|| block == Blocks.GRAVEL
			|| block == Blocks.SOUL_SAND
			|| block == Blocks.SOUL_SOIL
			|| oreDisguises.containsKey(block);
	}
}
