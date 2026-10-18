package com.hugosmpenhanced.client.feature.discord;

import java.util.List;
import java.util.function.Supplier;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * How the presence looks on a Discord profile, live while on HugoSMP and with example values
 * elsewhere, plus the connection status on the right.
 */
public class DiscordPreviewOption extends SettingsOption {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "textures/gui/discord/app-icon.png");
	private static final int CARD = 0xFF2B2D31;
	private static final int CARD_BORDER = 0xFF1E1F22;
	private static final int BUTTON = 0xFF4E5058;
	private static final int ELAPSED = 0xFF23A55A;
	private static final int IMAGE = 52;
	private static final int MAX_WIDTH = 300;

	private Supplier<PlayerSkin> skin;

	public DiscordPreviewOption() {
		super("Vorschau", null);
	}

	@Override
	protected int controlWidth(Font font) {
		return 0;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
	}

	/** Live presence on HugoSMP, otherwise how it would look there with example values. */
	private boolean live() {
		Minecraft client = Minecraft.getInstance();
		return client.level != null && DiscordPresence.onHugoSmp();
	}

	private DiscordPresence.View view() {
		ModConfig config = ModConfigManager.CONFIG;
		if (!config.discordEnabled) {
			return null;
		}
		if (this.live()) {
			return DiscordPresence.current();
		}
		Minecraft client = Minecraft.getInstance();
		String name = client.getUser().getName();
		StatusReader.Reading example = new StatusReader.Reading("SMP", null, 1_234_567.0, null, 42, null, 7, null, 6.0, 128,
			"", List.of(), "", "", "");
		return DiscordPresence.view(example, name, config.discordShowHead ? "head" : null, DiscordPresence.buttons(config), config);
	}

	private int cardHeight(DiscordPresence.View view) {
		if (view == null) {
			return 26;
		}
		return 20 + IMAGE + 8 + view.buttons().size() * 20;
	}

	@Override
	protected int belowHeight(Font font, int width) {
		return this.cardHeight(this.view()) + (this.live() ? 4 : 14);
	}

	@Override
	protected void renderBelow(GuiGraphicsExtractor graphics, Font font, int left, int top, int width, int mouseX, int mouseY) {
		DiscordPresence.View view = this.view();
		int w = Math.min(width, MAX_WIDTH);
		int h = this.cardHeight(view);
		Theme.panel(graphics, left, top, w, h, CARD, CARD_BORDER);
		if (view == null) {
			String text = ModConfigManager.CONFIG.discordEnabled ? "Außerhalb von HugoSMP wird nichts angezeigt" : "Rich Presence ist aus";
			graphics.text(font, text, left + 8, top + 9, Theme.MUTED, false);
			return;
		}
		graphics.text(font, "SPIELT EIN SPIEL", left + 8, top + 7, 0xFFB5BAC1, false);

		int imageX = left + 8;
		int imageY = top + 20;
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, imageX, imageY, 0, 0, IMAGE, IMAGE, 256, 256, 256, 256);
		if (view.head() != null) {
			int size = 18;
			int hx = imageX + IMAGE - size + 4;
			int hy = imageY + IMAGE - size + 4;
			graphics.fill(hx - 2, hy - 2, hx + size + 2, hy + size + 2, CARD);
			PlayerFaceExtractor.extractRenderState(graphics, this.skin(), hx, hy, size);
		}

		int textX = imageX + IMAGE + 10;
		int room = left + w - 8 - textX;
		int y = imageY + 2;
		graphics.text(font, font.plainSubstrByWidth("HugoSMP", room), textX, y, Theme.WHITE, false);
		y += 12;
		for (String line : new String[] {view.details(), view.state()}) {
			if (line != null) {
				graphics.text(font, fit(font, line, room), textX, y, line == view.details() ? 0xFFDBDEE1 : 0xFFB5BAC1, false);
				y += 11;
			}
		}
		if (view.start() != null) {
			long seconds = Math.max(0, (System.currentTimeMillis() - view.start()) / 1000);
			String elapsed = seconds >= 3600
				? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
				: String.format("%02d:%02d", seconds / 60, seconds % 60);
			graphics.text(font, elapsed, textX, y, ELAPSED, false);
		}

		int by = imageY + IMAGE + 8;
		for (String[] button : view.buttons()) {
			boolean over = this.inside(mouseX, mouseY, left + 8, by, w - 16, 16);
			Theme.panel(graphics, left + 8, by, w - 16, 16, over ? 0xFF6D6F78 : BUTTON, 0);
			graphics.centeredText(font, button[0], left + w / 2, by + 4, Theme.WHITE);
			by += 20;
		}
		if (!this.live()) {
			graphics.text(font, "Beispielwerte, auf HugoSMP live", left + 2, top + h + 3, Theme.FAINT, false);
		}
	}

	private static String fit(Font font, String text, int room) {
		return font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("...")) + "...";
	}

	private PlayerSkin skin() {
		if (this.skin == null) {
			Minecraft client = Minecraft.getInstance();
			this.skin = client.getSkinManager().createLookup(client.getGameProfile(), false);
		}
		return this.skin.get();
	}
}
