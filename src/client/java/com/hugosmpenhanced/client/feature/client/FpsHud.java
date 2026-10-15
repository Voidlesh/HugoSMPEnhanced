package com.hugosmpenhanced.client.feature.client;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.hud.HudElement;
import com.hugosmpenhanced.client.hud.HudPos;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

/** A small "120 FPS · 35 ms" line; ping only on a server. Placed in the HUD editor. */
public final class FpsHud implements HudElement {
	public static final FpsHud INSTANCE = new FpsHud();
	private static final int TEXT = 0xFFE8E8E8;
	private static final int MUTED = 0xFF9A9A9A;
	private static final int GOOD = 0xFF5ADC78;
	private static final int OK = 0xFFF0BE3C;
	private static final int BAD = 0xFFE65050;
	private static final String SEPARATOR = "  ·  ";

	private FpsHud() {
	}

	@Override
	public String id() {
		return "fps";
	}

	@Override
	public String label() {
		return "FPS und Ping";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.clientFpsHudOn;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.clientFpsHudOn = enabled;
	}

	@Override
	public boolean visible() {
		return true;
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(1, 0);
	}

	private static String fps() {
		return Minecraft.getInstance().getFps() + " FPS";
	}

	private static String pingText(boolean preview) {
		int ping = ping(Minecraft.getInstance());
		if (ping < 0 && preview && ModConfigManager.CONFIG.clientFpsShowPing) {
			return "35 ms";
		}
		return ping < 0 ? null : ping + " ms";
	}

	@Override
	public int width(Font font, boolean preview) {
		String ping = pingText(preview);
		return font.width(fps()) + (ping == null ? 0 : font.width(SEPARATOR) + font.width(ping)) + 8;
	}

	@Override
	public int height(Font font, boolean preview) {
		return 13;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, boolean preview) {
		String fps = fps();
		String ping = pingText(preview);
		graphics.fill(0, 0, this.width(font, preview), 13, 0x90000000);
		int tx = 4;
		graphics.drawString(font, fps, tx, 3, TEXT, false);
		if (ping != null) {
			tx += font.width(fps);
			graphics.drawString(font, SEPARATOR, tx, 3, MUTED, false);
			tx += font.width(SEPARATOR);
			int ms = Integer.parseInt(ping.substring(0, ping.indexOf(' ')));
			graphics.drawString(font, ping, tx, 3, ms < 80 ? GOOD : ms < 150 ? OK : BAD, false);
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
