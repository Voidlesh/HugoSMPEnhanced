package com.hugosmpenhanced.client.feature.tpa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public final class TpaToast implements Toast {
	private static final int WIDTH = 180;
	private static final int HEIGHT = 36;
	private static final int ACCENT_COLOR = 0xFFB983FF;
	private static final int BACKGROUND_COLOR = 0xE6101014;
	private static final int BORDER_COLOR = 0xFF2B2B33;

	private final Component title;
	private final Component subtitle;
	private final ItemStack icon;
	private final long durationMs;

	private Visibility wantedVisibility = Visibility.SHOW;

	public TpaToast(Component title, Component subtitle, ItemStack icon, long durationMs) {
		this.title = title;
		this.subtitle = subtitle;
		this.icon = icon;
		this.durationMs = Math.max(1000L, durationMs);
	}

	public static void show(Component title, Component subtitle, ItemStack icon, long durationMs) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		client.gui.toastManager().addToast(new TpaToast(title, subtitle, icon, durationMs));
	}

	@Override
	public Visibility getWantedVisibility() {
		return wantedVisibility;
	}

	@Override
	public void update(ToastManager manager, long timeVisibleMs) {
		if (timeVisibleMs >= durationMs) {
			wantedVisibility = Visibility.HIDE;
		}
	}

	@Override
	public SoundEvent getSoundEvent() {
		return SoundEvents.UI_TOAST_IN;
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return HEIGHT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long timeVisibleMs) {
		graphics.fill(0, 0, WIDTH, HEIGHT, BACKGROUND_COLOR);
		graphics.outline(0, 0, WIDTH, HEIGHT, BORDER_COLOR);
		graphics.fill(0, 0, 3, HEIGHT, ACCENT_COLOR);

		graphics.item(icon, 9, (HEIGHT - 16) / 2);

		graphics.text(font, title, 31, 8, 0xFFFFFF);
		graphics.text(font, subtitle, 31, 19, 0xAAAAAA);

		float progress = 1.0f - Math.min(1.0f, timeVisibleMs / (float) durationMs);
		int barWidth = Math.round(WIDTH * progress);
		if (barWidth > 0) {
			graphics.fill(0, HEIGHT - 2, barWidth, HEIGHT, ACCENT_COLOR);
		}
	}
}
