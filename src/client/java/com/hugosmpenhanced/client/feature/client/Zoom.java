package com.hugosmpenhanced.client.feature.client;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Zoom while a key is held (C by default). The field of view is divided by the current factor,
 * the mouse turns slower by the same amount, and the wheel changes the factor while zoomed. The
 * factor glides towards its target unless "smooth" is off; that glide is per frame, so it looks the
 * same at any frame rate.
 */
public final class Zoom {
	public static final double MIN = 1.5;
	public static final double MAX = 50.0;

	public static KeyMapping key;
	private static boolean held;
	/** Target while held; starts at the configured factor and follows the wheel. */
	private static double target = 1.0;
	private static double current = 1.0;
	private static long lastFrame;
	private static Boolean cinematicBefore;

	private Zoom() {
	}

	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hugosmpenhanced.zoom",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, TpaKeybinds.CATEGORY));
	}

	/** Once per client tick: starts or ends the zoom. */
	static void tick(Minecraft client) {
		ModConfig config = ModConfigManager.CONFIG;
		boolean down = key.isDown() && client.screen == null && client.player != null;
		if (down && !held) {
			held = true;
			target = config.clientZoomFactor;
			if (config.clientZoomCinematic) {
				cinematicBefore = client.options.smoothCamera;
				client.options.smoothCamera = true;
			}
		} else if (!down && held) {
			held = false;
			target = 1.0;
			if (cinematicBefore != null) {
				client.options.smoothCamera = cinematicBefore;
				cinematicBefore = null;
			}
		}
	}

	public static boolean zooming() {
		return held;
	}

	/** The factor to divide the field of view by this frame; 1 when not zoomed. */
	public static double factor() {
		long now = System.nanoTime();
		double seconds = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1.0E9);
		lastFrame = now;
		if (!ModConfigManager.CONFIG.clientZoomSmooth) {
			current = target;
		} else {
			// Closes about 99 % of the gap in a quarter second, in log space so zooming in and out feel alike.
			double t = 1.0 - Math.exp(-seconds * 18.0);
			current = Math.exp(Mth.lerp(t, Math.log(current), Math.log(target)));
			if (Math.abs(current - target) < 0.002) {
				current = target;
			}
		}
		return current;
	}

	/** How much slower the mouse turns: the current zoom, so aiming feels the same as unzoomed. */
	public static double mouseScale() {
		return current <= 1.0 ? 1.0 : 1.0 / current;
	}

	/** Mouse wheel while zoomed; returns true when the wheel was used for the zoom. */
	public static boolean scroll(double amount) {
		if (!held || !ModConfigManager.CONFIG.clientZoomScroll || amount == 0) {
			return false;
		}
		target = Mth.clamp(target * (amount > 0 ? 1.25 : 0.8), MIN, MAX);
		return true;
	}
}
