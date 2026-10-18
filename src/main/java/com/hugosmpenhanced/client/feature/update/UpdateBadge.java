package com.hugosmpenhanced.client.feature.update;

import java.util.List;

import com.hugosmpenhanced.client.settings.Theme;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/** "Update verfügbar" on the title screen, a small gold tag that opens {@link UpdateScreen}. */
public class UpdateBadge extends Button {
	private static final String[] ARROW = {"..#..", ".###.", "#.#.#", "..#..", "..#.."};
	public static final int HEIGHT = 15;

	private final ModUpdates.Release release;

	private UpdateBadge(int x, int y, ModUpdates.Release release, Screen screen) {
		super(x, y, width(release), HEIGHT, Component.literal(label(release)),
			b -> Minecraft.getInstance().setScreen(new UpdateScreen(screen, release)), DEFAULT_NARRATION);
		this.release = release;
		this.setTooltip(Tooltip.create(Component.literal(UpdateInstaller.isReady(release)
			? "HugoSMP Enhanced " + release.version() + " wird beim Beenden des Spiels installiert"
			: "HugoSMP Enhanced " + release.version() + " ist da: Änderungen ansehen und installieren")));
	}

	private static String label(ModUpdates.Release release) {
		return UpdateInstaller.isReady(release) ? "Update " + release.version() + " bereit" : "Update " + release.version();
	}

	private static int width(ModUpdates.Release release) {
		return Minecraft.getInstance().font.width(label(release)) + 22;
	}

	/** Adds the badge to the vanilla title screen; the mod's own title screen adds it itself. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen.getClass() == TitleScreen.class) {
				// top left, the only corner the vanilla title screen leaves free
				add(screen, Screens.getButtons(screen), 4, 4);
			}
		});
	}

	/** Adds the badge at x, y if there is something to show. */
	public static void add(Screen screen, List<AbstractWidget> widgets, int x, int y) {
		ModUpdates.Release release = ModUpdates.notifiable();
		if (release != null) {
			widgets.add(new UpdateBadge(x, y, release, screen));
		}
	}

	/** Same for screens that add widgets through their own init(). */
	public static UpdateBadge create(Screen screen, int x, int y) {
		ModUpdates.Release release = ModUpdates.notifiable();
		return release != null ? new UpdateBadge(x, y, release, screen) : null;
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.getX();
		int y = this.getY();
		boolean over = this.isHoveredOrFocused();
		Theme.panel(graphics, x, y, this.width, this.height, over ? 0xE0453A14 : 0xD0201A0A, Theme.GOLD);
		// gentle pulse on the arrow so the tag is noticed without blinking
		float pulse = 0.6f + 0.4f * (0.5f + 0.5f * Mth.sin((Util.getMillis() % 1600L) / 1600.0f * Mth.TWO_PI));
		int alpha = Mth.clamp(Math.round(pulse * 255), 0, 255);
		Theme.glyph(graphics, ARROW, x + 6, y + 5, (alpha << 24) | (Theme.GOLD & 0xFFFFFF));
		var font = Minecraft.getInstance().font;
		graphics.drawString(font, label(this.release), x + 15, y + 4, over ? Theme.WHITE : 0xFFFFE6A0);
	}
}
