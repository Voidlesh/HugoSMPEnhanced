package com.hugosmpenhanced.client.feature.client;

import com.hugosmpenhanced.client.config.ModConfigManager;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

/** A small "120 FPS · 35 ms" line in a corner of the screen; ping only on a server. */
public final class FpsHud {
	private static final int TEXT = 0xFFE8E8E8;
	private static final int MUTED = 0xFF9A9A9A;
	private static final int GOOD = 0xFF5ADC78;
	private static final int OK = 0xFFF0BE3C;
	private static final int BAD = 0xFFE65050;

	private FpsHud() {
	}

	static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hugosmpenhanced", "fps"), (graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		HudCorner corner = ModConfigManager.CONFIG.clientFpsHud;
		if (corner == HudCorner.OFF || client.options.hideGui || client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = client.font;
		String fps = client.getFps() + " FPS";
		int ping = ping(client);
		String pingText = ping < 0 ? null : ping + " ms";
		String separator = "  ·  ";
		int width = font.width(fps) + (pingText == null ? 0 : font.width(separator) + font.width(pingText)) + 8;
		int height = 13;
		int x = switch (corner) {
			case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - width - 3;
			default -> 3;
		};
		int y = switch (corner) {
			case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - height - 3;
			default -> 3;
		};
		graphics.fill(x, y, x + width, y + height, 0x90000000);
		int tx = x + 4;
		graphics.drawString(font, fps, tx, y + 3, TEXT, false);
		if (pingText != null) {
			tx += font.width(fps);
			graphics.drawString(font, separator, tx, y + 3, MUTED, false);
			tx += font.width(separator);
			graphics.drawString(font, pingText, tx, y + 3, ping < 80 ? GOOD : ping < 150 ? OK : BAD, false);
		}
	}

	/** Latency as the server reports it in the tab list, or -1 in singleplayer, when off or unknown. */
	private static int ping(Minecraft client) {
		if (!ModConfigManager.CONFIG.clientFpsShowPing || client.player == null || client.getConnection() == null
			|| client.isLocalServer()) {
			return -1;
		}
		PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}
}
