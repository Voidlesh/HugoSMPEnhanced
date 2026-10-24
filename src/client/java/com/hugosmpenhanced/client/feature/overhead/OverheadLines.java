package com.hugosmpenhanced.client.feature.overhead;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.fakeclan.FakeClan;
import com.hugosmpenhanced.client.feature.fakeclan.FakeClanRules;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The lines drawn above a player's nametag, from the one nearest the name outwards: the HugoGuard
 * line first, then the clan line. Each player gets at most one line of each kind, so the lines stack
 * and never draw over each other.
 */
public final class OverheadLines {
	/** Most lines one player can get; the nametag draws them in this order. */
	public static final int MAX_LINES = 2;

	private OverheadLines() {
	}

	public static List<Component> of(Player player) {
		ModConfig config = ModConfigManager.CONFIG;
		List<Component> lines = new ArrayList<>(MAX_LINES);

		if (config.hugoGuardOverhead) {
			HugoGuard.Entry entry = HugoGuard.shown(player.getUUID());
			if (entry != null) {
				lines.add(hugoGuardLine(entry));
			}
		}

		String clan = FakeClan.tagOf(player);
		if (clan != null) {
			FakeClanRules.Match fake = FakeClanRules.check(clan, FakeClan.realClans(), FakeClan.fakeClans());
			if (fake != null) {
				if (config.fakeClanWarning) {
					lines.add(Component.literal("⚠ FAKE-CLAN [" + fake.clan() + "] ⚠").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
			} else if (config.trustedClanOverhead && FakeClanRules.isReal(clan, FakeClan.realClans())) {
				lines.add(Component.literal("✔ TRUSTED CLAN [" + clan + "] ✔").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
			}
		}
		return lines;
	}

	private static Component hugoGuardLine(HugoGuard.Entry entry) {
		if (entry.status() == HugoGuard.Status.SCAMMER) {
			return Component.literal("⚠ SCAMMER ⚠").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		}
		return Component.literal("✔ TRUSTED ✔").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
	}
}
