package com.hugosmpenhanced.client.util;

import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class ServerCheck {
	private static final String SERVER_HOST = "hugosmp.net";

	private static volatile boolean lastKnownHugoSmp = false;

	private ServerCheck() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> isHugoSmp());
	}

	public static boolean isHugoSmp() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		boolean result = server != null && server.ip != null
				&& server.ip.toLowerCase(Locale.ROOT).contains(SERVER_HOST);
		lastKnownHugoSmp = result;
		return result;
	}

	/**
	 * Last known result of {@link #isHugoSmp()}, refreshed every client tick.
	 * Use this instead of {@link #isHugoSmp()} in places that may run after the
	 * connection has already been torn down (e.g. disconnect handling), where the
	 * live server lookup can no longer see which server we were just on.
	 */
	public static boolean wasHugoSmp() {
		return lastKnownHugoSmp;
	}
}
