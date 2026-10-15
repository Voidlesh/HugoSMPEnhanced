package com.hugosmpenhanced.client.feature.money;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.hud.HudPos;
import com.hugosmpenhanced.client.hud.HudText;
import com.hugosmpenhanced.client.hud.TextHudElement;

/** Balance with the change this session (or today) and per hour. Placed in the HUD editor. */
public final class MoneyHud extends TextHudElement {
	public static final MoneyHud INSTANCE = new MoneyHud();

	private MoneyHud() {
	}

	@Override
	public String id() {
		return "money";
	}

	@Override
	public String label() {
		return "Geld-Tracker";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.moneyHudOn;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.moneyHudOn = enabled;
		if (enabled) {
			ModConfigManager.CONFIG.moneyTrackerEnabled = true;
		}
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(0, 0.3);
	}

	@Override
	protected HudText build(boolean preview) {
		boolean real = MoneyTracker.active() && MoneyTracker.balance() >= 0;
		if (!real && !preview) {
			return null;
		}
		long balance = real ? MoneyTracker.balance() : 260_900_000L;
		long change;
		String label;
		if (ModConfigManager.CONFIG.moneyHudMode == 1) {
			MoneyTracker.Book today = MoneyTracker.days(1);
			change = real ? today.totalIncome() - today.totalSpent() : 3_450_000L;
			label = "Heute ";
		} else {
			change = real ? MoneyTracker.sessionChange() : 1_250_000L;
			label = "Sitzung ";
		}
		long perHour = real ? MoneyTracker.perHour() : 412_000L;
		HudText text = new HudText();
		text.line().add("Geld ", HudText.MUTED).add(Money.compact(balance), HudText.GOLD);
		text.line().add(label, HudText.MUTED).add(sign(change), change >= 0 ? HudText.GREEN : HudText.RED);
		if (perHour != 0 || !real) {
			text.add("  " + sign(perHour) + "/h", HudText.MUTED);
		}
		return text;
	}

	static String sign(long amount) {
		return (amount >= 0 ? "+" : "−") + Money.compact(Math.abs(amount));
	}
}
