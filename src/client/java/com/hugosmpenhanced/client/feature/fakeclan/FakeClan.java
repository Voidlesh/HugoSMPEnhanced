package com.hugosmpenhanced.client.feature.fakeclan;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.entity.player.Player;

/** Looks up the clan tag a player shows on HugoSMP and checks it against the clan lists. */
public final class FakeClan {
	private FakeClan() {
	}

	/**
	 * The fake-clan match for a remote player, or null. The tag is read the way the nametag shows it:
	 * the team's text around the name, so a clan set by a server team is found as well.
	 */
	public static FakeClanRules.Match of(Player player) {
		ModConfig config = ModConfigManager.CONFIG;
		Minecraft minecraft = Minecraft.getInstance();
		if (!config.fakeClanWarning || minecraft.level == null || player == minecraft.player || !ServerCheck.isHugoSmp()) {
			return null;
		}
		String name = player.getScoreboardName();
		PlayerTeam team = minecraft.level.getScoreboard().getPlayersTeam(name);
		String shown = PlayerTeam.formatNameForTeam(team, Component.literal(name)).getString();
		int at = shown.indexOf(name);
		if (at < 0) {
			return null;
		}
		String clan = FakeClanRules.clanOf(shown.substring(at + name.length()));
		return FakeClanRules.check(clan, orEmpty(config.fakeClanReal), orEmpty(config.fakeClanFake));
	}

	private static List<String> orEmpty(List<String> list) {
		return list == null ? List.of() : list;
	}
}
