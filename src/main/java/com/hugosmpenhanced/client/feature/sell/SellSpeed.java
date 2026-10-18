package com.hugosmpenhanced.client.feature.sell;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.Labeled;

import net.minecraft.network.chat.Component;

/** How fast stacks go into the sell menu: a pause between steps and the clicks per step. */
public enum SellSpeed implements Labeled {
	CAREFUL("Vorsichtig", 6, 1),
	NORMAL("Normal", 3, 3),
	FAST("Schnell", 2, 12),
	CUSTOM("Eigene", 0, 0);

	private final String label;
	private final int delay;
	private final int clicks;

	SellSpeed(String label, int delay, int clicks) {
		this.label = label;
		this.delay = delay;
		this.clicks = clicks;
	}

	/** Ticks between two click steps. */
	public int delayTicks() {
		return this == CUSTOM ? Math.max(1, ModConfigManager.CONFIG.sellCustomDelay) : this.delay;
	}

	/** Shift-clicks sent in one step. */
	public int clicksPerStep() {
		return this == CUSTOM ? Math.max(1, ModConfigManager.CONFIG.sellCustomClicks) : this.clicks;
	}

	@Override
	public Component getDisplayName() {
		return Component.literal(this.label);
	}
}
