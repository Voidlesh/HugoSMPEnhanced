package com.hugosmpenhanced.client.feature.spawnerstorage;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.pay.PayLog;
import com.hugosmpenhanced.client.gui.PanelScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** {@code /hse spawner}: every spawner you opened, fullest first, with when it will be full. */
public class SpawnerStorageScreen extends PanelScreen {
	private boolean confirmClear;

	public SpawnerStorageScreen(Screen parent) {
		super(Component.literal("Spawner-Lager"), parent, 0xFFB061FF, 400);
		this.searchable("Mob oder Welt…");
		this.footer(() -> this.confirmClear ? "Wirklich alle?" : "Alle vergessen", () -> {
			if (this.confirmClear) {
				SpawnerStorage.forgetAll();
			}
			this.confirmClear = !this.confirmClear;
		});
	}

	@Override
	protected String subtitle() {
		return "Füllstand hochgerechnet aus deinen Besuchen · Rechtsklick: vergessen";
	}

	@Override
	protected String status() {
		return ModConfigManager.CONFIG.spawnerReminderEnabled ? "Erinnerung ab " + ModConfigManager.CONFIG.spawnerReminderPercent + " %" : "Erinnerung aus";
	}

	@Override
	protected int rowHeight() {
		return 22;
	}

	@Override
	protected String emptyText() {
		return SpawnerStorage.spawners().isEmpty() ? "Öffne einen Spawner auf HugoSMP, dann steht er hier." : "Kein Spawner passt.";
	}

	@Override
	protected List<? extends Row> rows() {
		long now = System.currentTimeMillis();
		List<SpawnerStorage.Spawner> list = new ArrayList<>(SpawnerStorage.spawners());
		list.removeIf(s -> !this.matches(s.label(), s.dimension));
		list.sort(Comparator.comparingInt((SpawnerStorage.Spawner s) -> -s.percent(now)));
		List<Row> rows = new ArrayList<>();
		for (SpawnerStorage.Spawner spawner : list) {
			rows.add(new SpawnerRow(spawner));
		}
		return rows;
	}

	private static final class SpawnerRow implements Row {
		private final SpawnerStorage.Spawner spawner;

		SpawnerRow(SpawnerStorage.Spawner spawner) {
			this.spawner = spawner;
		}

		@Override
		public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, boolean hovered) {
			long now = System.currentTimeMillis();
			graphics.item(new ItemStack(Items.SPAWNER), x + 3, y + 3);
			clipped(graphics, font, this.spawner.label(), x + 24, y + 3, 120, Theme.TEXT);
			String place = this.spawner.x + ", " + this.spawner.y + ", " + this.spawner.z + "  " + world(this.spawner.dimension);
			clipped(graphics, font, place, x + 24, y + 12, 140, Theme.FAINT);
			int percent = this.spawner.percent(now);
			int color = SpawnerHud.color(percent);
			int barX = x + 170;
			int barWidth = width - 170 - 120;
			graphics.fill(barX, y + 8, barX + barWidth, y + 13, 0xFF2D2D2D);
			graphics.fill(barX, y + 8, barX + barWidth * percent / 100, y + 13, color);
			String value = (this.spawner.estimated ? "~" : "") + percent + " %";
			graphics.text(font, value, barX + barWidth + 6, y + 3, color, false);
			graphics.text(font, SpawnerStorage.fullText(this.spawner, now), barX + barWidth + 6, y + 12, Theme.MUTED, false);
		}

		@Override
		public List<Component> tooltip() {
			long now = System.currentTimeMillis();
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(this.spawner.label()).withColor(Theme.WHITE));
			lines.add(Component.literal("Zuletzt geöffnet " + PayLog.ago(this.spawner.seenAt) + ": " + this.spawner.stored + " von "
				+ this.spawner.capacity + " Items").withColor(Theme.MUTED));
			lines.add(Component.literal("Jetzt etwa " + this.spawner.storedNow(now) + " Items").withColor(Theme.MUTED));
			lines.add(Component.literal(this.spawner.rate > 0 ? "Tempo " + Math.round(this.spawner.rate) + " Items pro Stunde"
				: "Tempo erst nach dem 2. Besuch bekannt").withColor(Theme.MUTED));
			if (this.spawner.estimated) {
				lines.add(Component.literal("Geschätzt aus den Lagerplätzen des Menüs").withColor(Theme.GOLD));
			}
			lines.add(Component.literal("Rechtsklick: vergessen").withColor(Theme.FAINT));
			return lines;
		}

		@Override
		public boolean click(int button) {
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				SpawnerStorage.forget(this.spawner);
				return true;
			}
			return false;
		}

		private static String world(String dimension) {
			String path = dimension.substring(dimension.indexOf(':') + 1);
			return switch (path) {
				case "overworld" -> "Oberwelt";
				case "the_nether" -> "Nether";
				case "the_end" -> "End";
				default -> path;
			};
		}
	}
}
