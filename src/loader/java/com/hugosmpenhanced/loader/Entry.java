package com.hugosmpenhanced.loader;

import net.fabricmc.api.ClientModInitializer;

/** Starts the copy of the mod that belongs to the running Minecraft line. */
public class Entry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		try {
			Class<?> type = Class.forName(Line.className("com.hugosmpenhanced.client.HugoSmpEnhancedClient"));
			((ClientModInitializer) type.getDeclaredConstructor().newInstance()).onInitializeClient();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("HugoSMP Enhanced could not start", e);
		}
	}
}
