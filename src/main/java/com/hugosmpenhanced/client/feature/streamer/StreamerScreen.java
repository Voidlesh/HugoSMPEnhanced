package com.hugosmpenhanced.client.feature.streamer;

//? if modern
import com.hugosmpenhanced.client.util.Desktop;
import com.mojang.blaze3d.platform.InputConstants;
//? end
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//? if legacy
import org.lwjgl.glfw.GLFW;
//? end

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * The streamer panel (J or /streamer) with two tabs. Auktion: set up the item and rules, start a
 * real auction or a 30 s test run; while it runs or after it ended only a control bar sits at the
 * bottom so the overlay stays visible. Giveaway: open joining, see who is in, spin the wheel.
 */
public class StreamerScreen extends Screen implements StyledScreen {
	private static final int[] DURATIONS = {30, 45, 60, 90, 120, 150, 180, 240, 300, 420, 600, 900};

	private enum Tab {
		AUCTION, GIVEAWAY
	}

	private enum Mode {
		SETUP, LIVE, GIVEAWAY
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private static Tab tab = Tab.AUCTION;
	private static ItemStack auctionItem = ItemStack.EMPTY;
	private static String auctionName = "";
	private static int liveBarTop = -1;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private Mode mode;
	private EditBox nameField;
	private EditBox startField;
	private EditBox stepField;
	private EditBox prizeField;
	private boolean picking;
	private boolean pickingPrize;
	private boolean confirmRefund;
	private long cancelArmedAt;
	private int listScroll;
	private long openedAt;

	private int fx;
	private int fy;
	private int fw;
	private int fh;

	public StreamerScreen(Screen parent) {
		super(Component.literal("Streamer-Tools"));
		this.parent = parent;
		this.openedAt = System.currentTimeMillis();
	}

	/** True while a full panel (not just the live control bar) is open. */
	public static boolean coversHud(Screen screen) {
		return screen instanceof StreamerScreen streamer && streamer.mode != null && streamer.mode != Mode.LIVE;
	}

	private Mode currentMode() {
		if (tab == Tab.GIVEAWAY) {
			return Mode.GIVEAWAY;
		}
		return DonationAuction.state() == DonationAuction.State.IDLE ? Mode.SETUP : Mode.LIVE;
	}

	@Override
	protected void init() {
		this.mode = this.currentMode();
		this.fw = Math.min(this.width - 12, 520);
		this.fh = Math.min(this.height - 12, 262);
		this.fx = (this.width - this.fw) / 2;
		this.fy = (this.height - this.fh) / 2;
		var config = ModConfigManager.CONFIG;
		if (this.mode == Mode.SETUP) {
			int rx = this.rightX();
			int rw = this.fx + this.fw - 10 - rx;
			this.nameField = this.field(rx, this.fy + 52, rw, auctionName, 48, "Name fürs Overlay (leer = Item-Name)");
			this.nameField.setResponder(value -> auctionName = value);
			int third = (rw - 12) / 3;
			this.startField = this.field(rx + third + 6, this.fy + 84, third, Money.plain(config.streamerStartBid), 16, "10.000");
			this.startField.setResponder(value -> {
				long amount = Money.parse(value);
				if (amount > 0) {
					config.streamerStartBid = amount;
				}
			});
			this.stepField = this.field(rx + 2 * (third + 6), this.fy + 84, rw - 2 * (third + 6), Money.plain(config.streamerMinStep), 16, "5.000");
			this.stepField.setResponder(value -> {
				long amount = Money.parse(value);
				if (amount >= 0 && !value.isBlank()) {
					config.streamerMinStep = amount;
				}
			});
		} else if (this.mode == Mode.GIVEAWAY) {
			int x = this.fx + 10 + 26;
			this.prizeField = this.field(x, this.fy + 50, 150, Giveaway.prizeName(), 48, "Preis, z. B. Elytra");
			this.prizeField.setResponder(value -> Giveaway.setPrize(Giveaway.prize(), value));
		}
	}

	private EditBox field(int x, int y, int w, String value, int max, String hint) {
		EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(hint));
		box.setMaxLength(max);
		box.setValue(value);
		box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
		return this.addRenderableWidget(box);
	}

	/** Small windows (or a big GUI scale): rule texts move into tooltips so everything fits. */
	private boolean compact() {
		return this.fh < 262;
	}

	private int rightX() {
		return this.fx + 132;
	}

	@Override
	public void tick() {
		if (this.currentMode() != this.mode) {
			this.confirmRefund = false;
			this.picking = false;
			this.rebuildWidgets();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		ModConfigManager.save();
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// See-through: the world and the overlay stay visible behind the panel.
		if (this.mode != Mode.LIVE) {
			float fade = StreamerUi.easeOut(StreamerUi.progress(this.openedAt, 250));
			graphics.fillGradient(0, 0, this.width, this.height, StreamerUi.alpha(0x50000000, fade), StreamerUi.alpha(0x78000000, fade));
		}
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.hits.clear();
		if (this.mode == Mode.LIVE) {
			this.renderLiveBar(graphics, mouseX, mouseY);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			if (this.confirmRefund) {
				this.renderRefundConfirm(graphics, mouseX, mouseY);
			}
			return;
		}
		float appear = StreamerUi.easeOut(StreamerUi.progress(this.openedAt, 220));
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, (1 - appear) * 8);
		Theme.panel(graphics, this.fx, this.fy, this.fw, this.fh, 0xA8101010, Theme.FRAME_BORDER);
		this.renderHeader(graphics, mouseX, mouseY);
		if (this.mode == Mode.SETUP) {
			this.renderSetup(graphics, mouseX, mouseY);
		} else {
			this.renderGiveaway(graphics, mouseX, mouseY);
		}
		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (this.picking) {
			this.renderPicker(graphics, mouseX, mouseY);
		}
	}

	private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean auction = this.mode == Mode.SETUP;
		//? if modern
		StreamerUi.text(graphics, this.font, auction ? "Spenden-Auktion" : "Giveaway", this.fx + 10, this.fy + 8, 1.25f, Theme.WHITE, true);
		//? else
		StreamerUi.text(graphics, this.font, auction ? "Spenden-Auktion" : "Giveaway", this.fx + 10, this.fy + 8, 1.25f, Theme.WHITE, true);
		//? end
		graphics.text(this.font, auction ? "Zuschauer bieten mit /pay, die höchste Summe gewinnt"
			: "Teilnehmer sammeln, dann das Glücksrad drehen", this.fx + 10, this.fy + 21, Theme.MUTED, false);
		// tabs on the right of the title
		int tx = this.fx + this.fw - 30;
		String[] names = {"Giveaway", "Auktion"};
		Tab[] tabs = {Tab.GIVEAWAY, Tab.AUCTION};
		for (int i = 0; i < names.length; i++) {
			int w = this.font.width(names[i]) + 12;
			tx -= w + 4;
			boolean selected = tab == tabs[i];
			boolean over = StreamerUi.inside(mouseX, mouseY, tx, this.fy + 7, w, 16);
			if (selected) {
				Theme.panel(graphics, tx, this.fy + 7, w, 16, 0xC0282828, 0xFF5A5A5A);
				graphics.fill(tx + 2, this.fy + 20, tx + w - 2, this.fy + 21, Theme.GOLD);
			} else if (over) {
				Theme.panel(graphics, tx, this.fy + 7, w, 16, 0x801E1E1E, 0);
			}
			graphics.text(this.font, names[i], tx + 6, this.fy + 11, selected ? Theme.WHITE : over ? Theme.TEXT : Theme.MUTED, selected);
			Tab target = tabs[i];
			this.hit(tx, this.fy + 7, w, 16, () -> this.switchTab(target));
		}
		int closeX = this.fx + this.fw - 24;
		boolean overClose = StreamerUi.inside(mouseX, mouseY, closeX, this.fy + 7, 16, 16);
		Theme.panel(graphics, closeX, this.fy + 7, 16, 16, overClose ? 0xFF3A3A3A : 0xB41E1E1E, overClose ? 0xFFB4B4B4 : Theme.CONTROL_BORDER);
		Theme.glyph(graphics, Theme.CROSS, closeX + 6, this.fy + 13, Theme.TEXT);
		this.hit(closeX, this.fy + 7, 16, 16, this::onClose);
		graphics.fill(this.fx + 8, this.fy + 33, this.fx + this.fw - 8, this.fy + 34, Theme.FRAME_BORDER);
	}

	private void switchTab(Tab target) {
		if (tab != target) {
			tab = target;
			this.openedAt = System.currentTimeMillis();
			this.rebuildWidgets();
		}
	}

	// ------------------------------------------------------------------ auction setup

	private void renderSetup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var config = ModConfigManager.CONFIG;
		long now = System.currentTimeMillis();
		int lx = this.fx + 10;
		int y = this.fy + 40;
		graphics.text(this.font, "ITEM", lx, y, 0xFFA0A0A0, false);
		int box = this.compact() ? Mth.clamp(this.fh - 132, 56, 112) : 112;
		int boxY = y + 12;
		Theme.panel(graphics, lx, boxY, box, box, 0x70000000, 0xFF3A3A3A);
		if (!auctionItem.isEmpty()) {
			StreamerUi.glow(graphics, lx + box / 2, boxY + box / 2, box / 2, box / 2, 0x38FFC850);
			float yaw = (now % 4000L) / 4000f * 360f;
			//? if modern
			graphics.guiRenderState.addPicturesInPictureState(
			//? else
			graphics.guiRenderState.submitPicturesInPictureState(
			//? end
				ItemSpinRenderState.of(auctionItem, yaw, 0, lx + box / 2, boxY + box / 2, box - 16, graphics.scissorStack.peek()));
		} else {
			graphics.centeredText(this.font, "Kein Item", lx + box / 2, boxY + box / 2 - 9, Theme.MUTED);
			graphics.centeredText(this.font, "gewählt", lx + box / 2, boxY + box / 2 + 1, Theme.MUTED);
		}
		int by = boxY + box + 4;
		if (!auctionItem.isEmpty()) {
			String itemName = this.font.plainSubstrByWidth(auctionItem.getHoverName().getString(), box);
			graphics.text(this.font, itemName, lx, by, StreamerUi.AQUA, true);
			by += 11;
		}
		boolean hasHand = this.minecraft.player != null && !this.minecraft.player.getMainHandItem().isEmpty();
		StreamerUi.button(graphics, this.font, lx, by, box, 16, "Aus der Hand", StreamerUi.Kind.NORMAL, hasHand, mouseX, mouseY);
		this.hit(lx, by, box, 16, () -> {
			if (hasHand) {
				auctionItem = this.minecraft.player.getMainHandItem().copy();
			}
		});
		StreamerUi.button(graphics, this.font, lx, by + 19, box, 16, "Aus dem Inventar", StreamerUi.Kind.NORMAL,
			this.minecraft.player != null, mouseX, mouseY);
		this.hit(lx, by + 19, box, 16, () -> {
			this.picking = true;
			this.pickingPrize = false;
		});

		// right column
		int rx = this.rightX();
		int rw = this.fx + this.fw - 10 - rx;
		graphics.text(this.font, "Name im Overlay", rx, this.fy + 41, 0xFFA0A0A0, false);
		int third = (rw - 12) / 3;
		graphics.text(this.font, "Dauer", rx, this.fy + 73, 0xFFA0A0A0, false);
		graphics.text(this.font, "Startgebot $", rx + third + 6, this.fy + 73, 0xFFA0A0A0, false);
		graphics.text(this.font, "Mindestschritt $", rx + 2 * (third + 6), this.fy + 73, 0xFFA0A0A0, false);
		// duration stepper
		int sy = this.fy + 84;
		Theme.panel(graphics, rx, sy, third, 16, Theme.FIELD, Theme.FIELD_BORDER);
		graphics.centeredText(this.font, StreamerOverlay.clock(config.streamerAuctionSeconds * 1000L) + " min", rx + third / 2, sy + 4, Theme.WHITE);
		this.stepButton(graphics, rx + 1, sy + 1, "-", mouseX, mouseY, -1);
		this.stepButton(graphics, rx + third - 15, sy + 1, "+", mouseX, mouseY, 1);

		int y2 = sy + 24;
		y2 = this.section(graphics, rx, y2, rw, "REGELN", new String[][] {
			{"Zahlungen zusammenrechnen", "2x $10.000 von einem Spieler = Gebot $20.000"},
			{"Spätes Gebot verlängert", "Neues Höchstgebot in den letzten " + config.streamerExtendSeconds + " s setzt die Uhr auf " + config.streamerExtendSeconds + " s"},
			{"Ansagen im Chat", "Start, neues Höchstgebot (höchstens alle 10 s) und Gewinner"}},
			new boolean[] {config.streamerSumPayments, config.streamerExtendLateBids, config.streamerAnnounce},
			new Runnable[] {
				() -> config.streamerSumPayments = !config.streamerSumPayments,
				() -> config.streamerExtendLateBids = !config.streamerExtendLateBids,
				() -> config.streamerAnnounce = !config.streamerAnnounce}, mouseX, mouseY);

		// payment detection
		y2 += 4;
		boolean compact = this.compact();
		int h = compact ? 24 : 38;
		int line = compact ? 8 : 19;
		int buttonY = compact ? y2 + 4 : y2 + 15;
		Theme.panel(graphics, rx, y2, rw, h, 0x60181818, Theme.SECTION_BORDER);
		if (!compact) {
			graphics.text(this.font, "ZAHLUNGEN ERKENNEN", rx + 7, y2 + 5, 0xFFA0A0A0, false);
		}
		ChatPatterns.Learning learning = ChatPatterns.learning();
		String status;
		int dot;
		if (learning == ChatPatterns.Learning.PAYMENT) {
			status = "Warte auf eine Zahlung… lass dir $1 schicken";
			dot = StreamerUi.alpha(Theme.GOLD, 0.5f + 0.5f * (float) Math.sin(now / 200.0));
		} else if (ChatPatterns.lastPayment() != null) {
			ChatPatterns.Payment last = ChatPatterns.lastPayment();
			status = "Zuletzt: " + last.name() + " " + Money.format(last.amount()) + ", " + ago(ChatPatterns.lastPaymentAt());
			dot = StreamerUi.GREEN;
		} else {
			status = ChatPatterns.hasLearned(ChatPatterns.Learning.PAYMENT) ? "Angelernt, noch keine Zahlung gesehen"
				: "Übliche Texte werden erkannt. Sonst einmal anlernen.";
			dot = ChatPatterns.hasLearned(ChatPatterns.Learning.PAYMENT) ? StreamerUi.GREEN : 0xFFB4B4B4;
		}
		graphics.fill(rx + 7, y2 + line + 2, rx + 11, y2 + line + 6, dot);
		int buttonsW = ChatPatterns.hasLearned(ChatPatterns.Learning.PAYMENT) ? 128 : 64;
		String shown = this.font.plainSubstrByWidth(status, rw - 26 - buttonsW);
		graphics.text(this.font, shown, rx + 15, y2 + line, Theme.TEXT, false);
		if (!shown.equals(status) && StreamerUi.inside(mouseX, mouseY, rx + 15, y2 + line - 2, rw - 26 - buttonsW, 12)) {
			graphics.setTooltipForNextFrame(this.font, Component.literal(status), mouseX, mouseY);
		}
		int bx = rx + rw - 7 - 60;
		boolean learningNow = learning == ChatPatterns.Learning.PAYMENT;
		StreamerUi.button(graphics, this.font, bx, buttonY, 60, 16, learningNow ? "Abbrechen" : "Anlernen",
			learningNow ? StreamerUi.Kind.NORMAL : StreamerUi.Kind.GOLD, true, mouseX, mouseY);
		this.hit(bx, buttonY, 60, 16, () -> {
			if (learningNow) {
				ChatPatterns.stopLearning();
			} else {
				ChatPatterns.startLearning(ChatPatterns.Learning.PAYMENT);
			}
		});
		if (ChatPatterns.hasLearned(ChatPatterns.Learning.PAYMENT)) {
			StreamerUi.button(graphics, this.font, bx - 64, buttonY, 60, 16, "Vergessen", StreamerUi.Kind.DANGER, true, mouseX, mouseY);
			this.hit(bx - 64, buttonY, 60, 16, () -> ChatPatterns.forget(ChatPatterns.Learning.PAYMENT));
		}

		// bottom row
		int bottom = this.fy + this.fh - 24;
		boolean ready = !auctionItem.isEmpty();
		int startW = 110;
		StreamerUi.button(graphics, this.font, this.fx + this.fw - 10 - startW, bottom, startW, 18, "Auktion starten", StreamerUi.Kind.GOLD, ready, mouseX, mouseY);
		this.hit(this.fx + this.fw - 10 - startW, bottom, startW, 18, () -> {
			if (!auctionItem.isEmpty()) {
				DonationAuction.start(auctionItem, auctionName, false);
				this.rebuildWidgets();
			}
		});
		StreamerUi.button(graphics, this.font, this.fx + this.fw - 14 - startW - 84, bottom, 84, 18, "Testlauf (30 s)", StreamerUi.Kind.NORMAL, ready, mouseX, mouseY);
		this.hit(this.fx + this.fw - 14 - startW - 84, bottom, 84, 18, () -> {
			if (!auctionItem.isEmpty()) {
				StreamerFeature.startDemo(auctionItem, auctionName);
				this.rebuildWidgets();
			}
		});
		if (!ready && StreamerUi.inside(mouseX, mouseY, this.fx + this.fw - 14 - startW - 84, bottom, startW + 88, 18)) {
			graphics.setTooltipForNextFrame(this.font, Component.literal("Erst ein Item wählen"), mouseX, mouseY);
		}
		String log = "Protokoll";
		int logW = this.font.width(log) + 10;
		StreamerUi.button(graphics, this.font, lx, bottom, logW, 18, log, StreamerUi.Kind.GHOST, true, mouseX, mouseY);
		//? if modern
		this.hit(lx, bottom, logW, 18, () -> Desktop.openPath(DonationAuction.logFile().getParent()));
		//? else
		this.hit(lx, bottom, logW, 18, () -> Util.getPlatform().openPath(DonationAuction.logFile().getParent()));
		//? end
		if (StreamerUi.inside(mouseX, mouseY, lx, bottom, logW, 18)) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(
				"Jede erkannte Zahlung steht mit Uhrzeit in streamer-zahlungen.log, falls du etwas nachprüfen oder zurückzahlen musst."), 200), mouseX, mouseY);
		}
		if (this.fw >= 460) {
			graphics.text(this.font, "Taste J öffnet dieses Fenster", lx + logW + 8, bottom + 5, Theme.FAINT, false);
		}
	}

	private void stepButton(GuiGraphicsExtractor graphics, int x, int y, String label, int mouseX, int mouseY, int direction) {
		boolean over = StreamerUi.inside(mouseX, mouseY, x, y, 14, 14);
		Theme.panel(graphics, x, y, 14, 14, over ? 0xFF3A3A3A : 0xFF1E1E1E, over ? 0xFFB4B4B4 : 0xFF3A3A3A);
		graphics.centeredText(this.font, label, x + 7, y + 3, Theme.WHITE);
		this.hit(x, y, 14, 14, () -> this.stepDuration(direction));
	}

	private void stepDuration(int direction) {
		var config = ModConfigManager.CONFIG;
		int index = 0;
		for (int i = 0; i < DURATIONS.length; i++) {
			if (DURATIONS[i] <= config.streamerAuctionSeconds) {
				index = i;
			}
		}
		config.streamerAuctionSeconds = DURATIONS[Mth.clamp(index + direction, 0, DURATIONS.length - 1)];
	}

	private int section(GuiGraphicsExtractor graphics, int x, int y, int w, String title, String[][] rows, boolean[] values, Runnable[] toggles,
		int mouseX, int mouseY) {
		boolean compact = this.compact();
		int rowH = compact ? 14 : 22;
		int h = 14 + rows.length * rowH + 2;
		Theme.panel(graphics, x, y, w, h, 0x60181818, Theme.SECTION_BORDER);
		graphics.text(this.font, title, x + 7, y + 5, 0xFFA0A0A0, false);
		int ry = y + 14;
		for (int i = 0; i < rows.length; i++) {
			boolean over = StreamerUi.inside(mouseX, mouseY, x + 1, ry, w - 2, rowH);
			if (over) {
				graphics.fill(x + 1, ry, x + w - 1, ry + rowH, Theme.ROW_HOVER);
			}
			graphics.fill(x + 7, ry, x + w - 7, ry + 1, Theme.DIVIDER);
			if (compact) {
				graphics.text(this.font, rows[i][0], x + 7, ry + 3, Theme.TEXT, false);
				Theme.toggle(graphics, x + w - 30, ry + 1, values[i], over);
				if (over) {
					graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(rows[i][1]), 200), mouseX, mouseY);
				}
			} else {
				graphics.text(this.font, rows[i][0], x + 7, ry + 3, Theme.TEXT, false);
				graphics.text(this.font, this.font.plainSubstrByWidth(rows[i][1], w - 44), x + 7, ry + 12, Theme.MUTED, false);
				Theme.toggle(graphics, x + w - 30, ry + 5, values[i], over);
			}
			Runnable toggle = toggles[i];
			this.hit(x + 1, ry, w - 2, rowH, () -> {
				toggle.run();
				ModConfigManager.save();
			});
			ry += rowH;
		}
		return y + h;
	}

	// ------------------------------------------------------------------ inventory picker

	private void renderPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.hits.clear();
		graphics.fill(0, 0, this.width, this.height, 0x60000000);
		int cols = 9;
		int cell = 20;
		int w = cols * cell + 16;
		int h = 5 * cell + 36;
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;
		// The newest hit wins, so the backdrop goes in first: a click beside the panel closes it,
		// a click on the panel between slots does nothing, a click on a slot picks it.
		this.hit(0, 0, this.width, this.height, () -> this.picking = false);
		this.hit(x, y, w, h, () -> {
		});
		Theme.panel(graphics, x, y, w, h, 0xE6141414, Theme.GOLD);
		graphics.text(this.font, "Item wählen", x + 8, y + 7, Theme.WHITE, true);
		var inventory = this.minecraft.player.getInventory();
		ItemStack hovered = ItemStack.EMPTY;
		for (int slot = 0; slot < 41; slot++) {
			// Hotbar last, like in the inventory; armour and offhand in a fifth row.
			int row;
			int col;
			if (slot < 9) {
				row = 3;
				col = slot;
			} else if (slot < 36) {
				row = (slot - 9) / 9;
				col = (slot - 9) % 9;
			} else {
				row = 4;
				col = slot - 36;
			}
			int cx = x + 8 + col * cell;
			int cy = y + 20 + row * cell + (row >= 3 ? 3 : 0) + (row == 4 ? 3 : 0);
			ItemStack stack = inventory.getItem(slot);
			boolean over = StreamerUi.inside(mouseX, mouseY, cx, cy, cell - 2, cell - 2);
			Theme.panel(graphics, cx, cy, cell - 2, cell - 2, over && !stack.isEmpty() ? 0x60FFC83D : 0x50000000, over ? Theme.GOLD : 0xFF2A2A2A);
			if (!stack.isEmpty()) {
				graphics.item(stack, cx + 1, cy + 1);
				//? if modern
				graphics.itemDecorations(this.font, stack, cx + 1, cy + 1);
				//? else
				graphics.renderItemDecorations(this.font, stack, cx + 1, cy + 1);
				//? end
				if (over) {
					hovered = stack;
				}
				ItemStack chosen = stack.copy();
				this.hit(cx, cy, cell - 2, cell - 2, () -> {
					if (this.pickingPrize) {
						Giveaway.setPrize(chosen, this.prizeField != null ? this.prizeField.getValue() : "");
					} else {
						auctionItem = chosen;
					}
					this.picking = false;
				});
			}
		}
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ live control bar

	private record LiveButton(String label, String shortLabel, StreamerUi.Kind kind, Runnable action, String tooltip) {
	}

	private List<LiveButton> liveButtons() {
		List<LiveButton> buttons = new ArrayList<>();
		if (DonationAuction.state() != DonationAuction.State.ENDED) {
			buttons.add(new LiveButton("+30 s", "+30 s", StreamerUi.Kind.NORMAL, () -> DonationAuction.addTime(30), null));
			buttons.add(new LiveButton("Jetzt beenden", "Beenden", StreamerUi.Kind.GOLD, DonationAuction::finish, null));
			// With real bids a slip of the mouse must not end the stream's auction: the first click arms it.
			boolean needsConfirm = !DonationAuction.demo() && DonationAuction.bidderCount() > 0;
			boolean armed = needsConfirm && System.currentTimeMillis() - this.cancelArmedAt < 3000;
			buttons.add(new LiveButton(armed ? "Wirklich abbrechen?" : "Abbrechen", armed ? "Sicher?" : "Abbrechen", StreamerUi.Kind.DANGER, () -> {
				if (!needsConfirm || System.currentTimeMillis() - this.cancelArmedAt < 3000) {
					this.cancelArmedAt = 0;
					DonationAuction.cancel();
				} else {
					this.cancelArmedAt = System.currentTimeMillis();
				}
			}, needsConfirm ? "Niemand gewinnt. Danach kannst du allen ihr Geld zurückzahlen." : "Ohne Gewinner beenden"));
			return buttons;
		}
		String winner = DonationAuction.winner();
		if (winner != null && !DonationAuction.demo()) {
			buttons.add(new LiveButton("TPA zu " + winner, "TPA", StreamerUi.Kind.GOLD, () -> {
				StreamerFeature.command("tpa " + winner);
				this.onClose();
			}, "Schickt /tpa " + winner));
			buttons.add(new LiveButton("Zu mir holen", "Holen", StreamerUi.Kind.NORMAL, () -> StreamerFeature.command("tpahere " + winner),
				"Schickt /tpahere " + winner));
			// Not after paying back: place two would win with money they already got back.
			if (DonationAuction.ranking().size() > 1 && !DonationAuction.refunded()) {
				buttons.add(new LiveButton("Platz 2 gewinnt", "Platz 2", StreamerUi.Kind.NORMAL, DonationAuction::nextWinner,
					"Wenn der Gewinner offline ist oder nicht antwortet"));
			}
		}
		Map<String, Long> refunds = DonationAuction.refunds();
		if (!refunds.isEmpty() && !DonationAuction.demo()) {
			String label;
			if (!DonationAuction.refunded()) {
				label = refunds.size() + " zurückzahlen";
			} else if (StreamerFeature.refundsLeft() > 0) {
				label = "Zahle zurück… " + (StreamerFeature.refundTotal() - StreamerFeature.refundsLeft()) + "/" + StreamerFeature.refundTotal();
			} else {
				label = "Zurückgezahlt";
			}
			buttons.add(new LiveButton(label, label, DonationAuction.refunded() ? StreamerUi.Kind.GHOST : StreamerUi.Kind.DANGER, () -> {
				if (!DonationAuction.refunded()) {
					this.confirmRefund = true;
				}
			}, DonationAuction.refunded() ? null : "Zahlt allen, die nicht gewonnen haben, ihr Gebot zurück. Du siehst die Liste vorher."));
		}
		buttons.add(new LiveButton("Neue Auktion", "Neu", StreamerUi.Kind.NORMAL, DonationAuction::close, "Zurück zum Einrichten"));
		return buttons;
	}

	/**
	 * The control bar while an auction runs or after it ended. Its width follows the buttons, long
	 * labels get shorter when the screen is narrow, and only then the buttons wrap onto a second row,
	 * so nothing is ever drawn on top of something else or left out.
	 */
	private void renderLiveBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean ended = DonationAuction.state() == DonationAuction.State.ENDED;
		String winner = DonationAuction.winner();
		List<LiveButton> buttons = this.liveButtons();
		int gap = 4;
		int pad = 6;
		int maxW = Math.min(this.width - 8, 480);
		int maxInner = maxW - 2 * pad;

		// Too wide: shorten the label that saves the most first ("TPA zu Name" becomes "TPA"), one at a time.
		String[] labels = new String[buttons.size()];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = buttons.get(i).label();
		}
		while (this.rowWidth(labels, gap) > maxInner) {
			int best = -1;
			int saving = 0;
			for (int i = 0; i < labels.length; i++) {
				int save = this.font.width(labels[i]) - this.font.width(buttons.get(i).shortLabel());
				if (save > saving) {
					saving = save;
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			labels[best] = buttons.get(best).shortLabel();
		}
		int[] widths = new int[buttons.size()];
		for (int i = 0; i < buttons.size(); i++) {
			widths[i] = Math.min(maxInner, this.font.width(labels[i]) + 16);
		}

		String info;
		if (!ended) {
			info = (DonationAuction.demo() ? "Testlauf  ·  " : "") + StreamerOverlay.clock(Math.max(0, DonationAuction.endsAt() - System.currentTimeMillis()))
				+ "  ·  " + DonationAuction.bidderCount() + " Bieter  ·  " + Money.format(DonationAuction.sum());
		} else {
			info = winner == null ? (DonationAuction.cancelled() ? "Abgebrochen, niemand gewinnt" : "Keine gültigen Gebote")
				: (DonationAuction.demo() ? "Testlauf vorbei" : "Gewinner: " + winner);
		}
		String giveaway = Giveaway.open() ? "Giveaway (" + Giveaway.count() + ")" : "Giveaway";
		int gw = this.font.width(giveaway) + 10;
		int headerTools = gw + 2 + 14;
		int natural = this.rowWidth(labels, gap);
		int w = Mth.clamp(Math.max(natural, this.font.width(info) + 10 + headerTools) + 2 * pad, 120, maxW);
		int inner = w - 2 * pad;

		// greedy rows
		List<int[]> rows = new ArrayList<>();
		int rowStart = 0;
		int used = 0;
		for (int i = 0; i < buttons.size(); i++) {
			int need = (i == rowStart ? 0 : gap) + widths[i];
			if (i > rowStart && used + need > inner) {
				rows.add(new int[] {rowStart, i, used});
				rowStart = i;
				used = widths[i];
			} else {
				used += need;
			}
		}
		rows.add(new int[] {rowStart, buttons.size(), used});

		int h = 21 + rows.size() * 20;
		int x = (this.width - w) / 2;
		int y = this.height - h - 4;
		liveBarTop = y;
		float slide = StreamerUi.easeOut(StreamerUi.progress(this.openedAt, 250));
		y += Math.round((1 - slide) * 20);
		Theme.panel(graphics, x, y, w, h, 0xF4101010, Theme.FRAME_BORDER);

		int toolsX = x + w - pad - headerTools;
		String shownInfo = this.font.plainSubstrByWidth(info, toolsX - 8 - (x + pad));
		graphics.text(this.font, shownInfo, x + pad + 2, y + 6, ended ? Theme.GOLD : Theme.WHITE, true);
		StreamerUi.button(graphics, this.font, toolsX, y + 3, gw, 14, giveaway, StreamerUi.Kind.GHOST, true, mouseX, mouseY);
		this.hit(toolsX, y + 3, gw, 14, () -> this.switchTab(Tab.GIVEAWAY));
		int closeX = toolsX + gw + 2;
		StreamerUi.button(graphics, this.font, closeX, y + 3, 14, 14, "×", StreamerUi.Kind.GHOST, true, mouseX, mouseY);
		this.hit(closeX, y + 3, 14, 14, this::onClose);
		if (StreamerUi.inside(mouseX, mouseY, closeX, y + 3, 14, 14)) {
			graphics.setTooltipForNextFrame(this.font, Component.literal("Ausblenden. Im Chat oder mit J kommst du zurück."), mouseX, mouseY);
		}

		int by = y + 20;
		for (int[] row : rows) {
			// Rows are centred, a single short row looks balanced and a wrapped one too.
			int bx = x + pad + (inner - row[2]) / 2;
			for (int i = row[0]; i < row[1]; i++) {
				LiveButton button = buttons.get(i);
				String label = labels[i];
				boolean over = StreamerUi.button(graphics, this.font, bx, by, widths[i], 16, label, button.kind(), true, mouseX, mouseY);
				this.hit(bx, by, widths[i], 16, button.action());
				if (over) {
					String tip = button.tooltip();
					if (!label.equals(button.label())) {
						tip = tip == null ? button.label() : button.label() + ": " + tip;
					}
					if (tip != null) {
						graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(tip), 200), mouseX, mouseY);
					}
				}
				bx += widths[i] + gap;
			}
			by += 20;
		}
	}

	private int rowWidth(String[] labels, int gap) {
		int total = -gap;
		for (String label : labels) {
			total += this.font.width(label) + 16 + gap;
		}
		return Math.max(0, total);
	}

	/** Top of the live control bar while it is open, so the overlay can keep the item above it; -1 otherwise. */
	public static int liveBarTop(Screen screen) {
		return screen instanceof StreamerScreen streamer && streamer.mode == Mode.LIVE ? liveBarTop : -1;
	}

	/** Opens the panel on the auction (live bar while one runs) or on the giveaway tab. */
	public static void open(boolean giveawayTab) {
		tab = giveawayTab ? Tab.GIVEAWAY : Tab.AUCTION;
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new StreamerScreen(null));
	}

	private void renderRefundConfirm(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.hits.clear();
		// Swallows clicks beside the dialog; added first so the dialog's own buttons win.
		this.hit(0, 0, this.width, this.height, () -> {
		});
		graphics.fill(0, 0, this.width, this.height, 0x80000000);
		Map<String, Long> refunds = DonationAuction.refunds();
		int lines = Math.min(10, refunds.size());
		int w = 240;
		int h = 58 + lines * 11 + (refunds.size() > lines ? 11 : 0);
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;
		Theme.panel(graphics, x, y, w, h, 0xF0141414, Theme.RED);
		graphics.text(this.font, "Wirklich zurückzahlen?", x + 8, y + 7, Theme.WHITE, true);
		long sum = refunds.values().stream().mapToLong(Long::longValue).sum();
		graphics.text(this.font, "Diese Befehle werden gesendet, zusammen " + Money.format(sum) + ":", x + 8, y + 19, Theme.MUTED, false);
		int ly = y + 31;
		int i = 0;
		for (var entry : refunds.entrySet()) {
			if (i++ >= lines) {
				break;
			}
			graphics.text(this.font, "/pay " + entry.getKey() + " " + entry.getValue(), x + 12, ly, Theme.TEXT, false);
			ly += 11;
		}
		if (refunds.size() > lines) {
			graphics.text(this.font, "… und " + (refunds.size() - lines) + " weitere", x + 12, ly, Theme.MUTED, false);
		}
		int by = y + h - 22;
		StreamerUi.button(graphics, this.font, x + 8, by, 80, 16, "Abbrechen", StreamerUi.Kind.NORMAL, true, mouseX, mouseY);
		this.hit(x + 8, by, 80, 16, () -> this.confirmRefund = false);
		StreamerUi.button(graphics, this.font, x + w - 8 - 130, by, 130, 16, "Jetzt " + refunds.size() + "x zahlen", StreamerUi.Kind.DANGER, true, mouseX, mouseY);
		this.hit(x + w - 8 - 130, by, 130, 16, () -> {
			StreamerFeature.refundLosers();
			this.confirmRefund = false;
		});
	}

	// ------------------------------------------------------------------ giveaway

	private void renderGiveaway(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var config = ModConfigManager.CONFIG;
		long now = System.currentTimeMillis();
		int x0 = this.fx + 10;
		int x1 = this.fx + this.fw - 10;
		// prize row
		int py = this.fy + 40;
		graphics.text(this.font, "PREIS", x0, py, 0xFFA0A0A0, false);
		Theme.panel(graphics, x0, py + 9, 22, 22, 0x60000000, 0xFF3A3A3A);
		if (!Giveaway.prize().isEmpty()) {
			graphics.item(Giveaway.prize(), x0 + 3, py + 12);
		}
		int pbx = x0 + 26 + 156;
		boolean hasHand = this.minecraft.player != null && !this.minecraft.player.getMainHandItem().isEmpty();
		StreamerUi.button(graphics, this.font, pbx, py + 10, 76, 16, "Aus der Hand", StreamerUi.Kind.NORMAL, hasHand, mouseX, mouseY);
		this.hit(pbx, py + 10, 76, 16, () -> {
			if (hasHand) {
				Giveaway.setPrize(this.minecraft.player.getMainHandItem(), this.prizeField.getValue());
			}
		});
		StreamerUi.button(graphics, this.font, pbx + 80, py + 10, 76, 16, "Aus Inventar", StreamerUi.Kind.NORMAL, this.minecraft.player != null, mouseX, mouseY);
		this.hit(pbx + 80, py + 10, 76, 16, () -> {
			this.picking = true;
			this.pickingPrize = true;
		});

		// source cards
		int cy = py + 36;
		int cw = (x1 - x0 - 6) / 2;
		String self = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "Name";
		this.sourceCard(graphics, x0, cy, cw, "Ingame", "/msg " + self + " " + config.streamerIngameJoinWord, Giveaway.count(Giveaway.Source.INGAME),
			config.streamerIngameJoin, config.streamerIngameJoin ? (Giveaway.open() ? "offen" : "bereit") : "aus",
			config.streamerIngameJoin ? StreamerUi.GREEN : 0xFF808080, false, mouseX, mouseY,
			() -> config.streamerIngameJoin = !config.streamerIngameJoin);
		String channel = TwitchChat.normalize(config.streamerTwitchChannel);
		String twitchStatus;
		int twitchColor;
		if (!config.streamerTwitchJoin) {
			twitchStatus = "aus";
			twitchColor = 0xFF808080;
		} else if (channel.isEmpty()) {
			twitchStatus = "Kanal fehlt";
			twitchColor = 0xFFE6B450;
		} else {
			switch (StreamerFeature.twitch().status()) {
				case CONNECTED -> {
					twitchStatus = "verbunden, nur lesen";
					twitchColor = StreamerUi.GREEN;
				}
				case CONNECTING -> {
					twitchStatus = "verbindet…";
					twitchColor = StreamerUi.alpha(0xFFE6B450, 0.5f + 0.5f * (float) Math.sin(now / 200.0));
				}
				case FAILED -> {
					twitchStatus = "getrennt, neuer Versuch…";
					twitchColor = StreamerUi.RED_TEXT;
				}
				default -> {
					twitchStatus = Giveaway.open() ? "aus" : "verbindet beim Öffnen";
					twitchColor = 0xFFB4B4B4;
				}
			}
		}
		String twitchHow = channel.isEmpty() ? "Kanal in den Einstellungen eintragen" : config.streamerTwitchJoinWord + " auf twitch.tv/" + channel;
		this.sourceCard(graphics, x0 + cw + 6, cy, cw, "Twitch", twitchHow, Giveaway.count(Giveaway.Source.TWITCH), config.streamerTwitchJoin,
			twitchStatus, twitchColor, true, mouseX, mouseY, () -> {
				config.streamerTwitchJoin = !config.streamerTwitchJoin;
				StreamerFeature.updateTwitchConnection();
			});
		if (channel.isEmpty() && StreamerUi.inside(mouseX, mouseY, x0 + cw + 6, cy, cw, 36)) {
			this.hits.removeIf(hit -> hit.x == x0 + cw + 6 && hit.y == cy);
			this.hit(x0 + cw + 6, cy, cw, 36, () -> this.minecraft.setScreenAndShow(new SettingsScreen(this).select("Werkzeuge", "Streamer")));
		}

		// participants
		int ly = cy + 42;
		String head = "TEILNEHMER  " + Giveaway.count();
		graphics.text(this.font, head, x0, ly, 0xFFA0A0A0, false);
		String note = Giveaway.open() ? "Jeder nur einmal  ·  Twitch: !join <MC-Name> für TPA" : "Teilnahme ist geschlossen";
		graphics.text(this.font, note, x1 - this.font.width(note), ly, Giveaway.open() ? Theme.FAINT : 0xFFE6B450, false);
		int listY = ly + 10;
		int listH = this.fy + this.fh - 30 - listY;
		Theme.panel(graphics, x0, listY, x1 - x0, listH, 0x60000000, 0xFF333333);
		List<Giveaway.Participant> people = Giveaway.participants();
		int cols = Math.max(2, (x1 - x0 - 8) / 110);
		int colW = (x1 - x0 - 8) / cols;
		int rowH = 12;
		int visibleRows = (listH - 6) / rowH;
		int rows = (people.size() + cols - 1) / cols;
		this.listScroll = Mth.clamp(this.listScroll, 0, Math.max(0, rows - visibleRows));
		if (people.isEmpty()) {
			graphics.centeredText(this.font, Giveaway.open() ? "Noch niemand dabei. Sag im Stream, wie man mitmacht!" : "Öffne die Teilnahme, dann können alle mitmachen.",
				(x0 + x1) / 2, listY + listH / 2 - 4, Theme.MUTED);
		}
		graphics.enableScissor(x0 + 1, listY + 1, x1 - 1, listY + listH - 1);
		for (int i = 0; i < people.size(); i++) {
			int row = i / cols - this.listScroll;
			if (row < 0 || row >= visibleRows) {
				continue;
			}
			Giveaway.Participant p = people.get(i);
			int px = x0 + 4 + (i % cols) * colW;
			int rowY = listY + 4 + row * rowH;
			float age = (now - p.joinedAt()) / 1000f;
			float pop = StreamerUi.easeOut(age * 3);
			boolean over = StreamerUi.inside(mouseX, mouseY, px, rowY, colW - 4, rowH);
			if (over) {
				graphics.fill(px - 1, rowY - 1, px + colW - 4, rowY + rowH - 1, 0x30FFFFFF);
			}
			if (p.source() == Giveaway.Source.TWITCH) {
				StreamerUi.twitchIcon(graphics, px + 1, rowY + 1, StreamerUi.TWITCH);
			} else {
				StreamerUi.face(graphics, p.name(), px, rowY + 1, 9);
			}
			int color = StreamerUi.mix(StreamerUi.GREEN, Theme.TEXT, pop);
			String label = p.name() + (p.source() == Giveaway.Source.TWITCH && p.mcName() != null ? " (" + p.mcName() + ")" : "");
			graphics.text(this.font, this.font.plainSubstrByWidth(label, colW - 28), px + 13, rowY + 2, color, false);
			if (over) {
				Theme.glyph(graphics, Theme.CROSS, px + colW - 13, rowY + 3, Theme.RED_TEXT);
				this.hit(px + colW - 16, rowY, 11, rowH, () -> Giveaway.remove(p));
				graphics.setTooltipForNextFrame(this.font, Component.literal(p.source() == Giveaway.Source.TWITCH
					? "Über Twitch, " + ago(p.joinedAt()) : "Ingame per /msg, " + ago(p.joinedAt())), mouseX, mouseY);
			}
		}
		graphics.disableScissor();
		if (rows > visibleRows) {
			int barH = Math.max(8, listH * visibleRows / rows);
			int barY = listY + 2 + (listH - 4 - barH) * this.listScroll / Math.max(1, rows - visibleRows);
			graphics.fill(x1 - 3, barY, x1 - 1, barY + barH, 0xFF7A7A7A);
		}

		// bottom row
		int bottom = this.fy + this.fh - 24;
		StreamerUi.button(graphics, this.font, x0, bottom, 60, 18, "Leeren", StreamerUi.Kind.DANGER, Giveaway.count() > 0, mouseX, mouseY);
		this.hit(x0, bottom, 60, 18, Giveaway::clear);
		int spinW = 110;
		boolean canSpin = Giveaway.count() >= 2;
		StreamerUi.button(graphics, this.font, x1 - spinW, bottom, spinW, 18, "Glücksrad drehen", StreamerUi.Kind.GOLD, canSpin, mouseX, mouseY);
		this.hit(x1 - spinW, bottom, spinW, 18, () -> {
			if (Giveaway.count() >= 2) {
				this.minecraft.setScreenAndShow(new WheelScreen(this));
			}
		});
		String openLabel = Giveaway.open() ? "Teilnahme schließen" : "Teilnahme öffnen";
		int ow = this.font.width(openLabel) + 16;
		StreamerUi.button(graphics, this.font, x1 - spinW - 4 - ow, bottom, ow, 18, openLabel,
			Giveaway.open() ? StreamerUi.Kind.NORMAL : StreamerUi.Kind.GOLD, true, mouseX, mouseY);
		this.hit(x1 - spinW - 4 - ow, bottom, ow, 18, () -> Giveaway.setOpen(!Giveaway.open()));
	}

	private void sourceCard(GuiGraphicsExtractor graphics, int x, int y, int w, String title, String how, int count, boolean on, String status,
		int statusColor, boolean twitch, int mouseX, int mouseY, Runnable toggle) {
		Theme.panel(graphics, x, y, w, 36, on ? 0x70181818 : 0x50101010, Theme.SECTION_BORDER);
		if (twitch) {
			StreamerUi.twitchIcon(graphics, x + 8, y + 7, on ? StreamerUi.TWITCH : 0xFF606060);
		} else {
			String self = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "Steve";
			StreamerUi.face(graphics, self, x + 7, y + 7, 9);
		}
		graphics.text(this.font, title, x + 21, y + 7, on ? Theme.WHITE : Theme.MUTED, true);
		int sx = x + 25 + this.font.width(title);
		graphics.fill(sx, y + 9, sx + 4, y + 13, statusColor);
		graphics.text(this.font, this.font.plainSubstrByWidth(status, w - (sx - x) - 60), sx + 7, y + 7, statusColor, false);
		graphics.text(this.font, this.font.plainSubstrByWidth(how, w - 60), x + 8, y + 22, Theme.MUTED, false);
		String number = String.valueOf(count);
		StreamerUi.right(graphics, this.font, number, x + w - 34, y + 9, 2f, on ? Theme.WHITE : Theme.FAINT, true);
		boolean over = StreamerUi.inside(mouseX, mouseY, x + w - 28, y + 12, 22, 11);
		Theme.toggle(graphics, x + w - 28, y + 12, on, over);
		this.hit(x + w - 28, y + 10, 24, 15, () -> {
			toggle.run();
			ModConfigManager.save();
		});
	}

	// ------------------------------------------------------------------ input

	private void hit(int x, int y, int w, int h, Runnable action) {
		this.hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			// Panels drawn last lie on top, so the newest hit wins. Text fields first.
			if (!this.picking && !this.confirmRefund && super.mouseClicked(event, doubleClick)) {
				return true;
			}
			for (int i = this.hits.size() - 1; i >= 0; i--) {
				Hit hit = this.hits.get(i);
				if (StreamerUi.inside(event.x(), event.y(), hit.x, hit.y, hit.w, hit.h)) {
					this.setFocused(null);
					hit.action.run();
					StreamerFeature.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f);
					return true;
				}
			}
			this.setFocused(null);
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.mode == Mode.GIVEAWAY) {
			this.listScroll -= (int) Math.signum(scrollY);
			return true;
		}
		if (this.mode == Mode.SETUP) {
			int rx = this.rightX();
			int third = (this.fx + this.fw - 10 - rx - 12) / 3;
			if (StreamerUi.inside(mouseX, mouseY, rx, this.fy + 84, third, 16)) {
				this.stepDuration((int) Math.signum(scrollY));
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.picking && event.key() == InputConstants.KEY_ESCAPE) {
			this.picking = false;
			return true;
		}
		if (this.confirmRefund && event.key() == InputConstants.KEY_ESCAPE) {
			this.confirmRefund = false;
			return true;
		}
		boolean typing = this.getFocused() instanceof EditBox;
		if (!typing && StreamerFeature.panelKey.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private static String ago(long at) {
		long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1000);
		if (seconds < 60) {
			return "gerade eben";
		}
		long minutes = seconds / 60;
		return minutes < 60 ? "vor " + minutes + " min" : "vor " + minutes / 60 + " h";
	}
}
