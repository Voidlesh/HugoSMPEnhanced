package com.hugosmpenhanced.client.feature.fakeclan;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

/** Reads the clan tag a remote player shows on HugoSMP, for the fake and trusted clan lines. */
public final class FakeClan {
	private FakeClan() {
	}

	/**
	 * The clan tag of a remote player, or null. Only read while on HugoSMP and while one of the clan
	 * lines is on. The tag is read the way the nametag shows it: the team's text around the name.
	 */
	public static String tagOf(Player player) {
		ModConfig config = ModConfigManager.CONFIG;
		Minecraft minecraft = Minecraft.getInstance();
		boolean wanted = config.fakeClanWarning || config.trustedClanOverhead;
		if (!wanted || minecraft.level == null || player == minecraft.player || !ServerCheck.isHugoSmp()) {
			return null;
		}
		String name = player.getScoreboardName();
		PlayerTeam team = minecraft.level.getScoreboard().getPlayersTeam(name);
		String shown = PlayerTeam.formatNameForTeam(team, Component.literal(name)).getString();
		int at = shown.indexOf(name);
		if (at < 0) {
			return null;
		}
		return FakeClanRules.clanOf(shown.substring(at + name.length()));
	}

	/** The real clans: the config's list plus the one from the Voidbot, never null. */
	public static List<String> realClans() {
		List<String> list = ModConfigManager.CONFIG.fakeClanReal;
		return RemoteClanLists.real(list == null ? List.of() : list);
	}

	/** The fake clans: the config's list plus the one from the Voidbot, never null. */
	public static List<String> fakeClans() {
		List<String> list = ModConfigManager.CONFIG.fakeClanFake;
		return RemoteClanLists.fake(list == null ? List.of() : list);
	}
}
