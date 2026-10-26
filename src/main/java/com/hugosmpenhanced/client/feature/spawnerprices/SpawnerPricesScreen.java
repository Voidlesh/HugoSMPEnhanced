package com.hugosmpenhanced.client.feature.spawnerprices;

//? if modern
import com.hugosmpenhanced.client.util.Desktop;
//? end
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/** {@code /spawnerpreise}: VOID Market's Ankauf and Verkauf for every spawner kind. */
public class SpawnerPricesScreen extends Screen implements StyledScreen {
	private static final int PANEL_WIDTH = 330;
	private static final int ROW_HEIGHT = 18;
	private static final int HEADER_HEIGHT = 44;
	private static final int FOOTER_HEIGHT = 40;
	private static final int MOB_COLUMN = 140;
	private static final int PRICE_COLUMN = 90;
	private static final int ACCENT = 0xFFB061FF;
	private static final int BUY = 0xFF55FF55;
	private static final int SELL = 0xFFFFAA00;
	private static final int MUTED = 0xFF5A5A5A;
	private static final int LABEL = 0xFF8A8A8A;

	private final Screen parent;
	private Button refreshButton;

	public SpawnerPricesScreen(Screen parent) {
		super(Component.literal("Spawner-Preise"));
		this.parent = parent;
		SpawnerPrices.refresh();
	}

	private int panelHeight() {
		return HEADER_HEIGHT + SpawnerMob.values().length * ROW_HEIGHT + FOOTER_HEIGHT + 6;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_WIDTH) / 2;
		int bottom = (this.height - this.panelHeight()) / 2 + this.panelHeight();
		int y = bottom - 26;
		int width = (PANEL_WIDTH - 16 - 2 * 4) / 3;
		this.addRenderableWidget(Button.builder(Component.literal("Discord"), b -> {
			//? if modern
			Desktop.openUri(SpawnerPrices.DISCORD);
			//? else
			Util.getPlatform().openUri(SpawnerPrices.DISCORD);
			//? end
		}).bounds(left + 8, y, width, 20).build());
		this.refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Aktualisieren"), b -> SpawnerPrices.refresh())
			.bounds(left + 8 + (width + 4), y, width, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
			.bounds(left + 8 + 2 * (width + 4), y, width, 20).build());
		this.tick();
	}

	@Override
	public void tick() {
		this.refreshButton.active = !SpawnerPrices.loading();
		this.refreshButton.setMessage(Component.literal(SpawnerPrices.loading() ? "Lädt…" : "Aktualisieren"));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int height = this.panelHeight();
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - height) / 2;
		HugoStyle.drawBox(graphics, left, top, PANEL_WIDTH, height, HugoStyle.State.NORMAL);
		// Darker than the plain box: the prices must stay readable over a bright world.
		graphics.fill(left + 1, top + 3, left + PANEL_WIDTH - 1, top + height - 1, 0x70000000);
		graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 3, ACCENT);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		SpawnerPrices.Snapshot snapshot = SpawnerPrices.snapshot();
		SpawnerPrices.Server server = snapshot != null ? snapshot.server() : null;

		graphics.pose().pushMatrix();
		graphics.pose().translate(left + 10, top + 9);
		graphics.pose().scale(1.5f, 1.5f);
		graphics.text(this.font, "Spawner-Preise", 0, 0, 0xFFFFFFFF);
		graphics.pose().popMatrix();
		graphics.text(this.font, SpawnerPrices.SERVER, left + 10, top + 25, ACCENT);
		String status = this.status(snapshot);
		graphics.text(this.font, status, left + PANEL_WIDTH - 10 - this.font.width(status), top + 12,
			snapshot == null && SpawnerPrices.state() == SpawnerPrices.State.FAILED ? 0xFFFF6060 : LABEL);

		int x = left + 10 + MOB_COLUMN;
		graphics.text(this.font, "Ankauf", x, top + 25, LABEL);
		graphics.text(this.font, "Verkauf", x + PRICE_COLUMN, top + 25, LABEL);
		graphics.fill(left + 6, top + HEADER_HEIGHT - 6, left + PANEL_WIDTH - 6, top + HEADER_HEIGHT - 5, 0xFF3A3A3A);

		int y = top + HEADER_HEIGHT - 2;
		boolean odd = false;
		for (SpawnerMob mob : SpawnerMob.values()) {
			if (odd) {
				graphics.fill(left + 4, y, left + PANEL_WIDTH - 4, y + ROW_HEIGHT, 0x30FFFFFF);
			}
			odd = !odd;
			graphics.item(new ItemStack(mob.icon), left + 10, y + 1);
			int textY = y + (ROW_HEIGHT - 8) / 2;
			graphics.text(this.font, mob.label, left + 30, textY, 0xFFE0E0E0);
			if (snapshot != null) {
				this.price(graphics, x, textY, server != null ? server.buy(mob) : null, BUY);
				this.price(graphics, x + PRICE_COLUMN, textY, server != null ? server.sell(mob) : null, SELL);
			}
			y += ROW_HEIGHT;
		}
		graphics.text(this.font, "Ankauf = was VOID dir zahlt, Verkauf = was du zahlst", left + 10, y + 3, LABEL);
	}

	private String status(SpawnerPrices.Snapshot snapshot) {
		if (snapshot == null) {
			return SpawnerPrices.state() == SpawnerPrices.State.FAILED ? "Nicht erreichbar" : "Lädt Preise…";
		}
		String age = "Stand " + SpawnerPrices.age(snapshot.fetchedAt());
		if (SpawnerPrices.lastError() != null) {
			return age + " · offline";
		}
		return age;
	}

	private void price(GuiGraphicsExtractor graphics, int x, int y, Long price, int color) {
		graphics.text(this.font, price == null ? "–" : SpawnerPrices.format(price), x, y, price == null ? MUTED : color);
	}
}
