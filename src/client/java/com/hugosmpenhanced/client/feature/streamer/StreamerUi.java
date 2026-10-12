package com.hugosmpenhanced.client.feature.streamer;

import java.util.UUID;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.settings.Theme;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

/** Shared look of the streamer tools: see-through panels, buttons, heads, scaled text, easing. */
public final class StreamerUi {
	public static final int GLASS = 0x8C0A0A0A;
	public static final int GLASS_STRONG = 0xB40C0C0C;
	public static final int GLASS_EDGE = 0x40FFFFFF;
	public static final int GREEN = 0xFF6EDC82;
	public static final int GREEN_FILL = 0xC8288C3C;
	public static final int RED_TEXT = 0xFFEB6464;
	public static final int AQUA = 0xFF55FFFF;
	public static final int LAVENDER = 0xFFAAAAFF;
	public static final int TWITCH = 0xFFA970FF;

	private static final String[] TWITCH_GLYPH = {
		"#######.", "#......#", "#.#..#.#", "#.#..#.#", "#......#", "#.....#.", "###.##..", "..#.#...", "..##...."};

	private static Identifier glowTexture;

	public enum Kind {
		NORMAL, GOLD, DANGER, TWITCH, GHOST
	}

	private StreamerUi() {
	}

	// ------------------------------------------------------------------ panels and buttons

	public static void glass(GuiGraphics graphics, int x, int y, int w, int h) {
		Theme.panel(graphics, x, y, w, h, GLASS, GLASS_EDGE);
	}

	public static void glass(GuiGraphics graphics, int x, int y, int w, int h, int border) {
		Theme.panel(graphics, x, y, w, h, GLASS, border);
	}

	/** Draws a button and returns whether the mouse is over it. */
	public static boolean button(GuiGraphics graphics, Font font, int x, int y, int w, int h, String label, Kind kind,
		boolean enabled, int mouseX, int mouseY) {
		boolean over = enabled && inside(mouseX, mouseY, x, y, w, h);
		int fill;
		int border;
		int text = Theme.WHITE;
		if (!enabled) {
			fill = 0x80141414;
			border = 0xFF333333;
			text = Theme.FAINT;
		} else {
			switch (kind) {
				case GOLD -> {
					fill = over ? 0xA0FFC83D : 0x5AFFC83D;
					border = Theme.GOLD;
				}
				case DANGER -> {
					fill = over ? 0x96B42828 : Theme.RED_FILL;
					border = over ? 0xFFFF6464 : Theme.RED;
				}
				case TWITCH -> {
					fill = over ? 0x80A970FF : 0x46A970FF;
					border = TWITCH;
				}
				case GHOST -> {
					fill = over ? 0x40FFFFFF : 0x00000000;
					border = over ? 0xFFB4B4B4 : 0x00000000;
					text = over ? Theme.WHITE : Theme.TEXT_DIM;
				}
				default -> {
					fill = over ? 0xC83A3A3A : 0xB41E1E1E;
					border = over ? 0xFFB4B4B4 : Theme.CONTROL_BORDER;
				}
			}
		}
		Theme.panel(graphics, x, y, w, h, fill, border);
		String shown = font.plainSubstrByWidth(label, w - 6);
		graphics.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2 + 1, text, kind != Kind.GHOST && enabled);
		return over;
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	// ------------------------------------------------------------------ text

	public static void text(GuiGraphics graphics, Font font, String text, float x, float y, float scale, int color, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	public static void centered(GuiGraphics graphics, Font font, String text, float centerX, float y, float scale, int color, boolean shadow) {
		text(graphics, font, text, centerX - font.width(text) * scale / 2f, y, scale, color, shadow);
	}

	public static void right(GuiGraphics graphics, Font font, String text, float rightX, float y, float scale, int color, boolean shadow) {
		text(graphics, font, text, rightX - font.width(text) * scale, y, scale, color, shadow);
	}

	// ------------------------------------------------------------------ heads and icons

	public static void face(GuiGraphics graphics, String name, int x, int y, int size) {
		PlayerFaceRenderer.draw(graphics, skin(name), x, y, size);
	}

	public static PlayerSkin skin(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null && name != null) {
			PlayerInfo info = client.getConnection().getPlayerInfo(name);
			if (info != null) {
				return info.getSkin();
			}
		}
		UUID id = UUIDUtil.createOfflinePlayerUUID(name == null ? "?" : name);
		return DefaultPlayerSkin.get(id);
	}

	public static void twitchIcon(GuiGraphics graphics, int x, int y, int color) {
		Theme.glyph(graphics, TWITCH_GLYPH, x, y, color);
	}

	/** A soft round glow (white, tint it with the colour's alpha and RGB). */
	public static void glow(GuiGraphics graphics, int centerX, int centerY, int radiusX, int radiusY, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, glowTexture(), centerX - radiusX, centerY - radiusY, 0, 0,
			radiusX * 2, radiusY * 2, 64, 64, 64, 64, color);
	}

	private static Identifier glowTexture() {
		if (glowTexture == null) {
			NativeImage image = new NativeImage(64, 64, false);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					double dx = (x + 0.5 - 32) / 32;
					double dy = (y + 0.5 - 32) / 32;
					double d = Math.sqrt(dx * dx + dy * dy);
					double a = d >= 1 ? 0 : Math.pow(1 - d, 2.2);
					image.setPixel(x, y, ((int) Math.round(a * 255) << 24) | 0xFFFFFF);
				}
			}
			glowTexture = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "streamer/glow");
			Minecraft.getInstance().getTextureManager().register(glowTexture, linear("streamer glow", image));
		}
		return glowTexture;
	}

	/** Smooth scaling instead of hard pixels, for generated round shapes. */
	public static DynamicTexture linear(String label, NativeImage image) {
		return new LinearTexture(label, image);
	}

	private static final class LinearTexture extends DynamicTexture {
		LinearTexture(String label, NativeImage image) {
			super(() -> label, image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	// ------------------------------------------------------------------ colour and motion

	public static int alpha(int color, float factor) {
		int a = (int) (((color >>> 24) & 0xFF) * Mth.clamp(factor, 0f, 1f));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static int mix(int from, int to, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
		int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
		int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static float easeOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	/** Overshoots a little before settling: for things that pop in. */
	public static float easeBack(float t) {
		t = Mth.clamp(t, 0f, 1f);
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}

	public static float progress(long since, long durationMs) {
		return Mth.clamp((System.currentTimeMillis() - since) / (float) durationMs, 0f, 1f);
	}
}
