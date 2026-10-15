package com.hugosmpenhanced.client.feature.farm;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.hud.HudPos;
import com.hugosmpenhanced.client.hud.HudText;
import com.hugosmpenhanced.client.hud.TextHudElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** What the farm brings in: time, the top drops per hour and the value. Placed in the HUD editor. */
public final class FarmHud extends TextHudElement {
	public static final FarmHud INSTANCE = new FarmHud();
	private static final int SHOWN = 4;

	private FarmHud() {
	}

	@Override
	public String id() {
		return "farm";
	}

	@Override
	public String label() {
		return "Farm-Tracker";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.farmHudOn;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.farmHudOn = enabled;
		if (enabled) {
			ModConfigManager.CONFIG.farmTrackerEnabled = true;
		}
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(0, 0.6);
	}

	@Override
	protected HudText build(boolean preview) {
		HudText text = new HudText();
		if (!ModConfigManager.CONFIG.farmTrackerEnabled || !FarmTracker.hasData()) {
			if (!preview) {
				return null;
			}
			text.line().add("Farm ", HudText.GOLD).add("1:12 h", HudText.MUTED);
			text.line().column("Knochen", HudText.TEXT, 64).add("4.210  ", HudText.TEXT).add("3.508/h", HudText.MUTED);
			text.line().column("Pfeil", HudText.TEXT, 64).add("2.876  ", HudText.TEXT).add("2.396/h", HudText.MUTED);
			text.line().add("Wert ", HudText.MUTED).add("$1,9 Mio", HudText.GOLD).add("  $1,6 Mio/h", HudText.MUTED);
			return text;
		}
		Font font = Minecraft.getInstance().font;
		text.line().add("Farm ", HudText.GOLD).add(FarmTracker.duration(FarmTracker.activeMillis()), HudText.MUTED)
			.add(FarmTracker.running() ? "" : "  pausiert", HudText.ORANGE);
		List<FarmTracker.Drop> drops = FarmTracker.drops();
		int width = 0;
		for (FarmTracker.Drop drop : drops.subList(0, Math.min(SHOWN, drops.size()))) {
			width = Math.max(width, font.width(drop.example.getHoverName().getString()) + 6);
		}
		for (FarmTracker.Drop drop : drops.subList(0, Math.min(SHOWN, drops.size()))) {
			double perHour = FarmTracker.perHour(drop.count);
			text.line().column(drop.example.getHoverName().getString(), HudText.TEXT, Math.min(width, 110))
				.add(Money.plain(drop.count) + "  ", HudText.TEXT)
				.add(perHour < 0 ? "" : Money.plain(Math.round(perHour)) + "/h", HudText.MUTED);
		}
		if (drops.size() > SHOWN) {
			text.line().add("+" + (drops.size() - SHOWN) + " weitere", HudText.MUTED);
		}
		double value = FarmTracker.value();
		if (value > 0) {
			double perHour = FarmTracker.perHour(1) < 0 ? -1 : value * 3_600_000.0 / FarmTracker.activeMillis();
			text.line().add("Wert ", HudText.MUTED).add(Money.compact(Math.round(value)), HudText.GOLD)
				.add(perHour < 0 ? "" : "  " + Money.compact(Math.round(perHour)) + "/h", HudText.MUTED);
		}
		return text;
	}
}
