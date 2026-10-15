package com.hugosmpenhanced.client.feature.spawnerstorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.hud.HudPos;
import com.hugosmpenhanced.client.hud.HudText;
import com.hugosmpenhanced.client.hud.TextHudElement;

/** The fullest spawners with their projected fill. Placed in the HUD editor. */
public final class SpawnerHud extends TextHudElement {
	public static final SpawnerHud INSTANCE = new SpawnerHud();
	private static final int SHOWN = 4;

	private SpawnerHud() {
	}

	@Override
	public String id() {
		return "spawner";
	}

	@Override
	public String label() {
		return "Spawner-Lager";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.spawnerHudOn;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.spawnerHudOn = enabled;
		if (enabled) {
			ModConfigManager.CONFIG.spawnerReminderEnabled = true;
		}
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(1, 0.3);
	}

	static int color(int percent) {
		return percent >= 100 ? HudText.RED : percent >= ModConfigManager.CONFIG.spawnerReminderPercent ? HudText.ORANGE : HudText.GREEN;
	}

	@Override
	protected HudText build(boolean preview) {
		long now = System.currentTimeMillis();
		List<SpawnerStorage.Spawner> list = SpawnerStorage.active() ? new ArrayList<>(SpawnerStorage.spawners()) : List.of();
		HudText text = new HudText();
		if (list.isEmpty()) {
			if (!preview) {
				return null;
			}
			text.line().add("Spawner-Lager", HudText.MUTED);
			text.line().column("4× Zombie", HudText.TEXT, 70).add("92 %  voll in 25 Min.", HudText.ORANGE).bar(92, HudText.ORANGE);
			text.line().column("Skelett", HudText.TEXT, 70).add("41 %  voll in 6 Std.", HudText.GREEN).bar(41, HudText.GREEN);
			return text;
		}
		list.sort(Comparator.comparingInt((SpawnerStorage.Spawner s) -> -s.percent(now)));
		text.line().add("Spawner-Lager", HudText.MUTED);
		int width = 0;
		for (SpawnerStorage.Spawner spawner : list.subList(0, Math.min(SHOWN, list.size()))) {
			width = Math.max(width, net.minecraft.client.Minecraft.getInstance().font.width(spawner.label()) + 6);
		}
		for (SpawnerStorage.Spawner spawner : list.subList(0, Math.min(SHOWN, list.size()))) {
			int percent = spawner.percent(now);
			text.line().column(spawner.label(), HudText.TEXT, width)
				.add((spawner.estimated ? "~" : "") + percent + " %  " + SpawnerStorage.fullText(spawner, now), color(percent))
				.bar(percent, color(percent));
		}
		if (list.size() > SHOWN) {
			text.line().add("+" + (list.size() - SHOWN) + " weitere · /hse spawner", HudText.MUTED);
		}
		return text;
	}
}
