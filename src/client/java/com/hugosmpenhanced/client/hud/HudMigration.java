package com.hugosmpenhanced.client.hud;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.client.FpsHud;
import com.hugosmpenhanced.client.feature.client.HudCorner;
import com.hugosmpenhanced.client.feature.notes.NotesHud;
import com.hugosmpenhanced.client.feature.sell.SellHud;

import net.minecraft.client.Minecraft;

/**
 * Moves the positions from before the HUD editor (FPS corner, AFK farm position, pinned notes in
 * pixels) into the editor's positions, once. Needs the screen size, so it runs on the first frame.
 */
public final class HudMigration {
	private HudMigration() {
	}

	public static void runIfNeeded(int screenWidth, int screenHeight) {
		ModConfig config = ModConfigManager.CONFIG;
		if (config.hudVersion >= 1) {
			return;
		}
		if (config.clientFpsHud != null) {
			config.clientFpsHudOn = config.clientFpsHud != HudCorner.OFF;
			HudPos pos = switch (config.clientFpsHud) {
				case TOP_LEFT -> new HudPos(0, 0);
				case BOTTOM_LEFT -> new HudPos(0, 1);
				case BOTTOM_RIGHT -> new HudPos(1, 1);
				default -> null;
			};
			if (pos != null) {
				config.hudPositions.put(FpsHud.INSTANCE.id(), pos);
			}
			config.clientFpsHud = null;
		}
		if (config.sellHudPosition != null) {
			config.sellHudOn = !"OFF".equals(config.sellHudPosition);
			if ("TOP_LEFT".equals(config.sellHudPosition)) {
				config.hudPositions.put(SellHud.INSTANCE.id(), new HudPos(0, 0));
			} else if ("TOP_RIGHT".equals(config.sellHudPosition)) {
				config.hudPositions.put(SellHud.INSTANCE.id(), new HudPos(1, 0));
			}
			config.sellHudPosition = null;
		}
		if (config.notesHudX != 4 || config.notesHudY != 4) {
			var font = Minecraft.getInstance().font;
			HudPos pos = new HudPos();
			pos.place(config.notesHudX, config.notesHudY, screenWidth, screenHeight,
				NotesHud.INSTANCE.width(font, false), Math.max(20, NotesHud.INSTANCE.height(font, false)));
			config.hudPositions.put(NotesHud.INSTANCE.id(), pos);
			config.notesHudX = 4;
			config.notesHudY = 4;
		}
		config.hudVersion = 1;
		ModConfigManager.save();
	}
}
