package com.hugosmpenhanced.client.feature.entityheatmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Farm helper, toggled by a key: every block with mobs or dropped items in it gets a coloured
 * tile on its floor and the count above it, with the mob types listed when there is more than
 * one. The colour follows the server's entity cramming limit (green, yellow from two thirds, red
 * at the limit).
 *
 * <p>Everything is submitted with the entities, depth-tested and before translucent terrain, so
 * walls hide it like any mob behind them, while glass (cutout or stained) lets it show through.
 * Only entities the server sent to the client are counted.
 */
public final class EntityHeatmap {
	private static final float PIXEL = 0.025F;
	private static final float NUMBER_SCALE = 1.4F;
	private static final float DETAIL_SCALE = 0.7F;
	private static final float INSET = 0.0625F;
	private static final float BORDER = 0.0625F;

	public static KeyMapping toggleKey;
	public static boolean shown;
	private static List<Cell> cells = List.of();

	private EntityHeatmap() {
	}

	public static void register() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hugosmpenhanced.entity_heatmap",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				if (!ModConfigManager.CONFIG.entityHeatmapEnabled) {
					continue;
				}
				shown = !shown;
				if (client.player != null) {
					client.player.displayClientMessage(Component.literal(
						shown ? "§aEntity-Heatmap an" : "§7Entity-Heatmap aus"), true);
				}
			}
			cells = shown && ModConfigManager.CONFIG.entityHeatmapEnabled ? collect(client) : List.of();
		});

		WorldRenderEvents.BEFORE_ENTITIES.register(EntityHeatmap::render);
	}

	/** One block position and what stands in it. */
	public record Cell(BlockPos pos, double floorY, double topY, int total, int mobs, List<Group> groups) {
	}

	/** One mob type or item in a cell. */
	public record Group(Component name, int count, int items) {
	}

	private static boolean counts(Entity entity, ModConfig config) {
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof ItemEntity) {
			return config.entityHeatmapItems;
		}
		return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand);
	}

	/** Groups the counted entities around the player by block, most crowded first. */
	public static List<Cell> collect(Minecraft client) {
		if (client.level == null || client.player == null) {
			return List.of();
		}
		ModConfig config = ModConfigManager.CONFIG;
		double radius = config.entityHeatmapRadius;
		Map<Long, CellBuilder> byPos = new LinkedHashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!counts(entity, config) || entity.distanceToSqr(client.player) > radius * radius) {
				continue;
			}
			BlockPos pos = entity.blockPosition();
			byPos.computeIfAbsent(pos.asLong(), key -> new CellBuilder(pos)).add(entity);
		}
		List<Cell> result = new ArrayList<>();
		for (CellBuilder builder : byPos.values()) {
			if (builder.total >= config.entityHeatmapMinCount) {
				result.add(builder.build());
			}
		}
		result.sort((a, b) -> Integer.compare(b.total(), a.total()));
		return result;
	}

	private static final class CellBuilder {
		private final BlockPos pos;
		private final Map<Object, int[]> counts = new LinkedHashMap<>();
		private final Map<Object, Component> names = new LinkedHashMap<>();
		private double floorY = Double.MAX_VALUE;
		private double topY = -Double.MAX_VALUE;
		private int total;
		private int mobs;

		CellBuilder(BlockPos pos) {
			this.pos = pos;
		}

		void add(Entity entity) {
			floorY = Math.min(floorY, entity.getY());
			topY = Math.max(topY, entity.getBoundingBox().maxY);
			total++;
			Object key;
			int items = 0;
			if (entity instanceof ItemEntity item) {
				key = item.getItem().getItem();
				items = item.getItem().getCount();
				names.putIfAbsent(key, item.getItem().getHoverName());
			} else {
				key = entity.getType();
				mobs++;
				names.putIfAbsent(key, entity.getType().getDescription());
			}
			int[] count = counts.computeIfAbsent(key, k -> new int[2]);
			count[0]++;
			count[1] += items;
		}

		Cell build() {
			List<Group> groups = new ArrayList<>();
			counts.forEach((key, count) -> groups.add(new Group(names.get(key), count[0], count[1])));
			groups.sort((a, b) -> Integer.compare(b.count(), a.count()));
			return new Cell(pos, floorY, topY, total, mobs, groups);
		}
	}

	/** Tile colour (RGB) and text colour for a cell, by how close its mobs are to the cramming limit. */
	public static int tileColor(Cell cell) {
		int limit = ModConfigManager.CONFIG.entityHeatmapCrammingLimit;
		if (cell.mobs() >= limit) {
			return 0xFF4040;
		}
		if (cell.mobs() * 3 >= limit * 2) {
			return 0xFFB020;
		}
		return 0x50E050;
	}

	private static int textColor(Cell cell) {
		int limit = ModConfigManager.CONFIG.entityHeatmapCrammingLimit;
		if (cell.mobs() >= limit) {
			return 0xFFFF5555;
		}
		if (cell.mobs() * 3 >= limit * 2) {
			return 0xFFFFFF55;
		}
		return 0xFFFFFFFF;
	}

	private static void render(WorldRenderContext context) {
		List<Cell> current = cells;
		if (current.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		CameraRenderState camera = context.worldState().cameraRenderState;
		Vec3 cam = camera.pos;
		PoseStack poseStack = context.matrices();
		var collector = context.commandQueue();

		for (Cell cell : current) {
			double x = cell.pos().getX() - cam.x;
			double y = cell.floorY() + 0.01 - cam.y;
			double z = cell.pos().getZ() - cam.z;
			int rgb = tileColor(cell);

			poseStack.pushPose();
			poseStack.translate(x, y, z);
			collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
				int fill = 0x48 << 24 | rgb;
				int edge = 0xC0 << 24 | rgb;
				float a = INSET;
				float b = 1 - INSET;
				quad(consumer, pose, a + BORDER, a + BORDER, b - BORDER, b - BORDER, fill);
				quad(consumer, pose, a, a, b, a + BORDER, edge);
				quad(consumer, pose, a, b - BORDER, b, b, edge);
				quad(consumer, pose, a, a + BORDER, a + BORDER, b - BORDER, edge);
				quad(consumer, pose, b - BORDER, a + BORDER, b, b - BORDER, edge);
			});
			poseStack.popPose();

			// Count, plus one line per type when the block holds more than one.
			List<FormattedCharSequence> details = new ArrayList<>();
			if (cell.groups().size() > 1) {
				for (Group group : cell.groups()) {
					// Drops are listed by stack size, mobs by head count.
					int amount = group.items() > 0 ? group.items() : group.count();
					String line = amount + "× " + group.name().getString();
					details.add(Component.literal(line).getVisualOrderText());
				}
			}
			float detailHeight = details.size() * 10 * PIXEL * DETAIL_SCALE;
			float numberHeight = 10 * PIXEL * NUMBER_SCALE;

			poseStack.pushPose();
			// Above the tallest entity, so the mobs in the block do not cover their own count.
			double labelY = cell.topY() + 0.15 - cam.y;
			poseStack.translate(x + 0.5, labelY + detailHeight + numberHeight, z + 0.5);
			poseStack.mulPose(camera.orientation);
			poseStack.pushPose();
			poseStack.scale(PIXEL * NUMBER_SCALE, -PIXEL * NUMBER_SCALE, PIXEL * NUMBER_SCALE);
			FormattedCharSequence number = Component.literal(String.valueOf(cell.total())).getVisualOrderText();
			collector.submitText(poseStack, -font.width(number) / 2.0F, 0, number, false, Font.DisplayMode.NORMAL,
				LightTexture.FULL_BRIGHT, textColor(cell), 0x60000000, 0);
			poseStack.popPose();
			if (!details.isEmpty()) {
				poseStack.translate(0, -numberHeight, 0);
				poseStack.scale(PIXEL * DETAIL_SCALE, -PIXEL * DETAIL_SCALE, PIXEL * DETAIL_SCALE);
				int widest = 0;
				for (FormattedCharSequence line : details) {
					widest = Math.max(widest, font.width(line));
				}
				for (int i = 0; i < details.size(); i++) {
					collector.submitText(poseStack, -widest / 2.0F, i * 10, details.get(i), false, Font.DisplayMode.NORMAL,
						LightTexture.FULL_BRIGHT, 0xFFE0E0E0, 0x60000000, 0);
				}
			}
			poseStack.popPose();
		}
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, int argb) {
		consumer.addVertex(pose, x0, 0, z0).setColor(argb);
		consumer.addVertex(pose, x0, 0, z1).setColor(argb);
		consumer.addVertex(pose, x1, 0, z1).setColor(argb);
		consumer.addVertex(pose, x1, 0, z0).setColor(argb);
	}
}
