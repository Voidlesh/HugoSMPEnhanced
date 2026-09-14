package com.hugosmpenhanced.client.feature.tpa;

public final class TpaState {
	public static final long TPA_TTL_MS = 30_000L;

	private static String pendingPlayer;
	private static long expiresAtMs;

	private TpaState() {
	}

	public static void set(String player, long ttlMs) {
		pendingPlayer = player;
		expiresAtMs = System.currentTimeMillis() + ttlMs;
	}

	public static String pendingPlayer() {
		expireIfNeeded();
		return pendingPlayer;
	}

	public static void clear() {
		pendingPlayer = null;
		expiresAtMs = 0L;
	}

	public static void expireIfNeeded() {
		if (pendingPlayer != null && System.currentTimeMillis() >= expiresAtMs) {
			clear();
		}
	}
}
