package com.hugosmpenhanced.client.feature.update;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Shows a new release: version, date and changelog, and installs it with one click. The jar is
 * downloaded and checked in the background and swapped in when the game closes.
 */
public class UpdateScreen extends Screen implements StyledScreen {
	private static final int HEADER = 40;
	private static final int FOOTER = 46;
	private static final int LINE = 10;

	private final Screen parent;
	private final ModUpdates.Release release;
	private int fx;
	private int fy;
	private int fw;
	private int fh;
	private List<Changelog.Line> lines = List.of();
	private int contentHeight;
	private double scroll;
	private UpdateButton primary;
	private UpdateInstaller.State shownState;

	public UpdateScreen(Screen parent, ModUpdates.Release release) {
		super(Component.literal("Update verfügbar"));
		this.parent = parent;
		this.release = release;
	}

	@Override
	protected void init() {
		this.fw = Math.min(380, this.width - 16);
		this.fh = Math.min(280, this.height - 16);
		this.fx = (this.width - this.fw) / 2;
		this.fy = (this.height - this.fh) / 2;
		String body = this.release.body().isBlank() ? "Keine Beschreibung zu diesem Release." : this.release.body();
		this.lines = Changelog.layout(body, this.font, this.boxWidth() - 16);
		this.contentHeight = 0;
		for (Changelog.Line line : this.lines) {
			this.contentHeight += LINE + line.gap();
		}
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());

		this.shownState = UpdateInstaller.state(this.release);
		int y = this.fy + this.fh - 24;
		int right = this.fx + this.fw - 8;
		boolean manual = UpdateInstaller.blocker(this.release) != null || this.shownState == UpdateInstaller.State.INCOMPATIBLE;
		String primaryText = this.primaryText(manual);
		int primaryWidth = Math.max(96, this.font.width(primaryText) + 16);
		this.primary = this.addRenderableWidget(new UpdateButton(right - primaryWidth, y, primaryWidth, 17, primaryText, true,
			b -> this.primaryAction(manual)));
		this.primary.active = this.shownState != UpdateInstaller.State.DOWNLOADING;
		right -= primaryWidth + 4;

		if (!manual) {
			int w = this.font.width("GitHub") + 16;
			this.addRenderableWidget(new UpdateButton(right - w, y, w, 17, "GitHub", false,
				b -> Util.getPlatform().openUri(this.releaseUrl())));
			right -= w + 4;
		}
		if (this.shownState != UpdateInstaller.State.READY) {
			int w = this.font.width("Überspringen") + 16;
			UpdateButton skip = this.addRenderableWidget(new UpdateButton(right - w, y, w, 17, "Überspringen", false, b -> {
				ModConfigManager.CONFIG.updateSkippedVersion = this.release.version();
				ModConfigManager.save();
				this.onClose();
			}));
			skip.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.literal("Keine Hinweise mehr zu " + this.release.version() + ". Die nächste Version wird wieder gemeldet.")));
			right -= w + 4;
		}
		int w = this.font.width("Später") + 16;
		if (right - w >= this.fx + 8) {
			this.addRenderableWidget(new UpdateButton(right - w, y, w, 17, "Später", false, b -> this.onClose()));
		}
	}

	private String primaryText(boolean manual) {
		if (manual) {
			return this.shownState == UpdateInstaller.State.INCOMPATIBLE ? "Auf GitHub ansehen" : "Von GitHub laden";
		}
		return switch (this.shownState) {
			case DOWNLOADING -> "Lädt…";
			case READY -> "Spiel beenden";
			case FAILED -> "Nochmal versuchen";
			default -> "Installieren";
		};
	}

	private void primaryAction(boolean manual) {
		if (manual) {
			Util.getPlatform().openUri(this.releaseUrl());
			return;
		}
		switch (UpdateInstaller.state(this.release)) {
			case READY -> this.minecraft.stop();
			case FAILED -> UpdateInstaller.retry(this.release);
			case NONE -> UpdateInstaller.download(this.release);
			default -> {
			}
		}
		this.rebuildWidgets();
	}

	private String releaseUrl() {
		return this.release.url().isBlank() ? ModUpdates.RELEASES_PAGE : this.release.url();
	}

	@Override
	public void tick() {
		if (UpdateInstaller.state(this.release) != this.shownState) {
			this.rebuildWidgets();
		}
		if (this.shownState == UpdateInstaller.State.DOWNLOADING) {
			float progress = UpdateInstaller.progress();
			this.primary.setMessage(Component.literal(progress >= 0 ? "Lädt… " + Math.round(progress * 100) + " %" : "Lädt…"));
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	private int boxWidth() {
		return this.fw - 16;
	}

	private int boxTop() {
		return this.fy + HEADER + 4;
	}

	private int boxHeight() {
		return this.fh - HEADER - 4 - FOOTER;
	}

	private int maxScroll() {
		return Math.max(0, this.contentHeight + 12 - this.boxHeight());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		this.scroll = Mth.clamp(this.scroll - scrollY * 20, 0, this.maxScroll());
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Theme.panel(graphics, this.fx, this.fy, this.fw, this.fh, Theme.FRAME, Theme.FRAME_BORDER);
		this.renderHeader(graphics);
		this.renderChangelog(graphics);
		this.renderStatus(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderHeader(GuiGraphics graphics) {
		int x = this.fx + 10;
		int y = this.fy + 9;
		Theme.icon(graphics, "brand", x, y + 1, 20, Theme.WHITE);
		x += 28;
		graphics.drawString(this.font, Component.literal("Update verfügbar").withStyle(s -> s.withBold(true)), x, y, Theme.WHITE);
		if (this.release.prerelease()) {
			int chipX = x + this.font.width(Component.literal("Update verfügbar").withStyle(s -> s.withBold(true))) + 6;
			Theme.panel(graphics, chipX, y - 2, this.font.width("Vorabversion") + 8, 12, Theme.CHIP, Theme.CHIP_BORDER);
			graphics.drawString(this.font, "Vorabversion", chipX + 4, y, Theme.TEXT_DIM, false);
		}
		y += 13;
		String current = ModUpdates.currentVersion();
		graphics.drawString(this.font, current, x, y, Theme.MUTED, false);
		x += this.font.width(current) + 5;
		graphics.drawString(this.font, "→", x, y, Theme.GOLD, false);
		x += this.font.width("→") + 5;
		graphics.drawString(this.font, Component.literal(this.release.version()).withStyle(s -> s.withBold(true)), x, y, Theme.WHITE, false);
		String date = this.release.date();
		if (!date.isEmpty()) {
			String text = "veröffentlicht am " + date;
			graphics.drawString(this.font, text, this.fx + this.fw - 10 - this.font.width(text), y, Theme.FAINT, false);
		}
		graphics.fill(this.fx + 8, this.fy + HEADER, this.fx + this.fw - 8, this.fy + HEADER + 1, Theme.FRAME_BORDER);
	}

	private void renderChangelog(GuiGraphics graphics) {
		int left = this.fx + 8;
		int top = this.boxTop();
		int width = this.boxWidth();
		int height = this.boxHeight();
		Theme.panel(graphics, left, top, width, height, Theme.SECTION, Theme.SECTION_BORDER);
		graphics.enableScissor(left + 1, top + 1, left + width - 1, top + height - 1);
		int y = top + 7 - (int) this.scroll;
		for (Changelog.Line line : this.lines) {
			y += line.gap();
			if (y > top - LINE && y < top + height) {
				if (line.bullet() != null) {
					graphics.drawString(this.font, line.bullet(), left + 8 + line.indent() - this.font.width(line.bullet()) - 4, y, Theme.GOLD, false);
				}
				graphics.drawString(this.font, line.text(), left + 8 + line.indent(), y, Theme.TEXT_DIM, false);
			}
			y += LINE;
		}
		graphics.disableScissor();
		int max = this.maxScroll();
		if (max > 0) {
			int track = height - 4;
			int thumb = Math.max(16, track * height / (this.contentHeight + 12));
			int thumbY = top + 2 + (int) ((track - thumb) * (this.scroll / max));
			graphics.fill(left + width - 4, thumbY, left + width - 2, thumbY + thumb, 0xFF6A6A6A);
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		int x = this.fx + 10;
		int y = this.fy + this.fh - FOOTER + 7;
		String blocker = UpdateInstaller.blocker(this.release);
		UpdateInstaller.State state = UpdateInstaller.state(this.release);
		String text;
		int color = Theme.MUTED;
		if (blocker != null) {
			text = blocker + ". Jar selbst austauschen.";
		} else if (state == UpdateInstaller.State.INCOMPATIBLE) {
			text = "Passt nicht zu deinem Spiel: " + UpdateInstaller.message();
			color = Theme.RED_TEXT;
		} else {
			switch (state) {
				case DOWNLOADING -> {
					text = "Lädt " + this.release.jar().name() + "…";
					float progress = UpdateInstaller.progress();
					int barWidth = this.fw - 20;
					graphics.fill(x, y + 11, x + barWidth, y + 13, 0xFF2A2A2A);
					if (progress >= 0) {
						graphics.fill(x, y + 11, x + Math.round(barWidth * progress), y + 13, Theme.GOLD);
					}
				}
				case READY -> {
					text = "✔ Bereit, wird beim Beenden des Spiels installiert";
					color = Theme.GREEN;
				}
				case FAILED -> {
					text = "Fehlgeschlagen: " + UpdateInstaller.message();
					color = Theme.RED_TEXT;
				}
				default -> text = "Wird geladen, geprüft und beim Beenden des Spiels eingesetzt";
			}
		}
		graphics.drawString(this.font, this.font.plainSubstrByWidth(text, this.fw - 20), x, y, color, false);
	}
}
