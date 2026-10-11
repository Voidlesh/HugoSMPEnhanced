package com.hugosmpenhanced.client.feature.quickjoin;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class QuickJoinAction {
	private static final String ADDRESS = "hugosmp.net";

	private QuickJoinAction() {
	}

	public static void join(Screen parent) {
		Minecraft client = Minecraft.getInstance();
		ServerAddress address = ServerAddress.parseString(ADDRESS);
		ServerData serverData = new ServerData("HugoSMP", ADDRESS, ServerData.Type.OTHER);

		ConnectScreen.startConnecting(parent, client, address, serverData, false,
				new TransferState(Map.of(), Map.of(), false));
	}
}
