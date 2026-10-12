package com.hugosmpenhanced.client.feature.spawnerprices;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /spawnerpreise}: every spawner kind with the shown server's Ankauf and Verkauf next to
 * the best offer of all trading servers, as on hugosmptools.com/trading. A green price is the best
 * one there is.
 */
public class SpawnerPricesScreen extends Screen implements StyledScreen {
	private static final int PANEL_WIDTH = 410;
	private static final int ROW_HEIGHT = 18;
	private static final int HEADER_HEIGHT = 46;
	private static final int FOOTER_HEIGHT = 40;
	private static final int MOB_COLUMN = 110;
	private static final int PRICE_COLUMN = 75;
	private static final int ACCENT = 0xFFB061FF;
	private static final int BEST = 0xFF55FF55;
	private static final int PRICE = 0xFFFFFFFF;
	private static final int MUTED = 0xFF5A5A5A;
	private static final int LABEL = 0xFF8A8A8A;

	private final Screen parent;
	private Button discordButton;
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
		this.discordButton = this.addRenderableWidget(Button.builder(Component.literal("Discord beitreten"), b -> {
			SpawnerPrices.Server server = SpawnerPrices.shownServer();
			if (server != null && server.invite() != null) {
				Util.getPlatform().openUri(server.invite());
			}
		}).bounds(left + 8, y, 110, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("hugosmptools.com"), b -> Util.getPlatform().openUri(SpawnerPrices.SITE))
			.bounds(left + 122, y, 104, 20).build());
		this.refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Aktualisieren"), b -> SpawnerPrices.refresh())
			.bounds(left + 230, y, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
			.bounds(left + PANEL_WIDTH - 8 - 74, y, 74, 20).build());
		this.tick();
	}

	@Override
	public void tick() {
		SpawnerPrices.Server server = SpawnerPrices.shownServer();
		this.discordButton.active = server != null && server.invite() != null;
		this.refreshButton.active = !SpawnerPrices.loading();
		this.refreshButton.setMessage(Component.literal(SpawnerPrices.loading() ? "Lädt…" : "Aktualisieren"));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int height = this.panelHeight();
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - height) / 2;
		HugoStyle.drawBox(graphics, left, top, PANEL_WIDTH, height, HugoStyle.State.NORMAL);
		// Darker than the plain box: the prices must stay readable over a bright world.
		graphics.fill(left + 1, top + 3, left + PANEL_WIDTH - 1, top + height - 1, 0x70000000);
		graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 3, ACCENT);
		super.render(graphics, mouseX, mouseY, partialTick);

		SpawnerPrices.Snapshot snapshot = SpawnerPrices.snapshot();
		SpawnerPrices.Server server = snapshot != null ? snapshot.server(ModConfigManager.CONFIG.spawnerPricesServer) : null;
		String serverName = server != null ? server.name() : ModConfigManager.CONFIG.spawnerPricesServer;

		graphics.pose().pushMatrix();
		graphics.pose().translate(left + 10, top + 9);
		graphics.pose().scale(1.5f, 1.5f);
		graphics.drawString(this.font, "Spawner-Preise", 0, 0, 0xFFFFFFFF);
		graphics.pose().popMatrix();
		String status = this.status(snapshot, server);
		graphics.drawString(this.font, status, left + PANEL_WIDTH - 10 - this.font.width(status), top + 12,
			snapshot == null && SpawnerPrices.state() == SpawnerPrices.State.FAILED ? 0xFFFF6060 : LABEL);

		// Column heads: the shown server's two prices, then the best of all servers.
		int headY = top + HEADER_HEIGHT - 12;
		int x = left + 10 + MOB_COLUMN;
		graphics.drawString(this.font, this.font.plainSubstrByWidth(serverName, PRICE_COLUMN * 2 - 6), x, headY - 11, ACCENT);
		graphics.drawString(this.font, "Ankauf", x, headY, LABEL);
		graphics.drawString(this.font, "Verkauf", x + PRICE_COLUMN, headY, LABEL);
		graphics.drawString(this.font, "Bester Kurs", x + PRICE_COLUMN * 2, headY - 11, 0xFFDDDDDD);
		graphics.drawString(this.font, "Ankauf", x + PRICE_COLUMN * 2, headY, LABEL);
		graphics.drawString(this.font, "Verkauf", x + PRICE_COLUMN * 3, headY, LABEL);
		graphics.fill(left + 6, top + HEADER_HEIGHT - 2, left + PANEL_WIDTH - 6, top + HEADER_HEIGHT - 1, 0xFF3A3A3A);

		int y = top + HEADER_HEIGHT;
		boolean odd = false;
		for (SpawnerMob mob : SpawnerMob.values()) {
			if (odd) {
				graphics.fill(left + 4, y, left + PANEL_WIDTH - 4, y + ROW_HEIGHT, 0x30FFFFFF);
			}
			odd = !odd;
			this.row(graphics, left, y, mob, snapshot, server, mouseX, mouseY);
			y += ROW_HEIGHT;
		}
		graphics.drawString(this.font, "Ankauf = was der Server dir zahlt, Verkauf = was du zahlst", left + 10, y + 3, LABEL);
	}

	private String status(SpawnerPrices.Snapshot snapshot, SpawnerPrices.Server server) {
		if (snapshot == null) {
			return switch (SpawnerPrices.state()) {
				case FAILED -> "hugosmptools.com nicht erreichbar";
				default -> "Lädt Preise…";
			};
		}
		String age = "Stand " + SpawnerPrices.age(snapshot.fetchedAt());
		if (SpawnerPrices.lastError() != null) {
			return age + " · offline";
		}
		return server == null ? "Server nicht gelistet · " + age : age;
	}

	private void row(GuiGraphics graphics, int left, int y, SpawnerMob mob, SpawnerPrices.Snapshot snapshot,
		SpawnerPrices.Server server, int mouseX, int mouseY) {
		graphics.renderItem(new ItemStack(mob.icon), left + 10, y + 1);
		int textY = y + (ROW_HEIGHT - 8) / 2;
		graphics.drawString(this.font, mob.label, left + 30, textY, 0xFFE0E0E0);
		if (snapshot == null) {
			return;
		}
		SpawnerPrices.Best bestBuy = snapshot.bestBuy(mob);
		SpawnerPrices.Best bestSell = snapshot.bestSell(mob);
		Long buy = server != null ? server.buy(mob) : null;
		Long sell = server != null ? server.sell(mob) : null;
		int x = left + 10 + MOB_COLUMN;
		this.price(graphics, x, textY, buy, bestBuy != null && buy != null && buy >= bestBuy.price());
		this.price(graphics, x + PRICE_COLUMN, textY, sell, bestSell != null && sell != null && sell <= bestSell.price());
		this.best(graphics, x + PRICE_COLUMN * 2, y, bestBuy, server);
		this.best(graphics, x + PRICE_COLUMN * 3, y, bestSell, server);
	}

	private void price(GuiGraphics graphics, int x, int y, Long price, boolean best) {
		if (price == null) {
			graphics.drawString(this.font, "–", x, y, MUTED);
		} else {
			graphics.drawString(this.font, SpawnerPrices.format(price), x, y, best ? BEST : PRICE);
		}
	}

	/** The best price with the server offering it underneath, or a dash. */
	private void best(GuiGraphics graphics, int x, int y, SpawnerPrices.Best best, SpawnerPrices.Server shown) {
		if (best == null) {
			graphics.drawString(this.font, "–", x, y + (ROW_HEIGHT - 8) / 2, MUTED);
			return;
		}
		boolean own = best.server() == shown;
		graphics.drawString(this.font, SpawnerPrices.format(best.price()), x, y + 1, own ? BEST : 0xFFFFDD55);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + 10f);
		graphics.pose().scale(0.75f, 0.75f);
		graphics.drawString(this.font, this.font.plainSubstrByWidth(best.server().name(), (int) ((PRICE_COLUMN - 4) / 0.75f)), 0, 0, LABEL);
		graphics.pose().popMatrix();
	}
}
