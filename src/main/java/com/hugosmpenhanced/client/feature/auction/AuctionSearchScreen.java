package com.hugosmpenhanced.client.feature.auction;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;


import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * The search that replaces HugoSMP's search sign in the auction house and for orders: a search
 * field, live suggestions for items and enchantments (with the lowest auction or highest order
 * price seen recently), recent searches and favourites.
 * Enter sends the selected row as line 1 of the sign, Escape sends an empty sign like the vanilla
 * editor would.
 */
public class AuctionSearchScreen extends Screen implements StyledScreen {
	private static final int ROW = 23;
	private static final int FIELD = 20;
	private static final int MAX_SUGGESTIONS = 8;
	private static final int STAR = 16;
	private static final int MARK = 14;

	private final SignBlockEntity sign;
	private final boolean front;
	private final AuctionMarket market;
	private final List<Row> rows = new ArrayList<>();
	private EditBox field;
	private int selected;
	private boolean sent;
	private long tooShortUntil;

	private int left;
	private int panelWidth;
	private int rowsTop;
	private int visibleRows;

	private enum Kind {
		QUERY, ITEM, FAVORITE, HISTORY
	}

	private record Row(Kind kind, String send, String title, String subtitle, ItemStack icon, String priceKey, int highlight,
		int highlightLength, int color, boolean limited) {
		Row(Kind kind, String send, String title, String subtitle, ItemStack icon, String priceKey, int highlight, int highlightLength) {
			this(kind, send, title, subtitle, icon, priceKey, highlight, highlightLength, 0, false);
		}
	}

	public AuctionSearchScreen(SignBlockEntity sign, boolean front, AuctionMarket market) {
		super(Component.literal(market.title));
		this.sign = sign;
		this.front = front;
		this.market = market;
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(this.width - 20, 300);
		this.left = (this.width - this.panelWidth) / 2;
		int bottomReserve = 46;
		int maxRows = MAX_SUGGESTIONS + 1;
		int top = Math.max(18, (this.height - (FIELD + 6 + maxRows * ROW + bottomReserve)) / 2 + 10);
		this.rowsTop = top + FIELD + 6;
		this.visibleRows = Math.max(1, Math.min(maxRows, (this.height - bottomReserve - this.rowsTop) / ROW));

		String previous = this.field != null ? this.field.getValue() : "";
		this.field = new EditBox(this.font, this.left, top, this.panelWidth, FIELD, Component.literal("Suchbegriff"));
		this.field.setMaxLength(48);
		this.field.setHint(Component.literal("Suchbegriff, z. B. Diamant (min. 3 Zeichen)"));
		this.field.setValue(previous);
		this.field.setResponder(value -> this.refresh());
		this.addRenderableWidget(this.field);
		this.setInitialFocus(this.field);

		int buttonWidth = 110;
		Button signButton = this.addRenderableWidget(Button.builder(Component.literal("Schild benutzen"), b -> this.useSign())
			.bounds(this.left + this.panelWidth - buttonWidth, this.height - 26, buttonWidth, 20).build());
		signButton.setTooltip(Tooltip.create(Component.literal("Öffnet das normale Schild vom Server, falls hier etwas nicht klappt.")));
		this.refresh();
	}

	private void refresh() {
		String query = this.field.getValue().strip();
		Kind previousKind = this.rows.isEmpty() || this.selected >= this.rows.size() ? null : this.rows.get(this.selected).kind();
		this.rows.clear();
		if (query.isEmpty()) {
			for (String favorite : ModConfigManager.CONFIG.auctionSearchFavorites) {
				this.rows.add(this.termRow(Kind.FAVORITE, favorite));
			}
			for (String recent : ModConfigManager.CONFIG.auctionSearchHistory) {
				if (!AuctionSearch.isFavorite(recent)) {
					this.rows.add(this.termRow(Kind.HISTORY, recent));
				}
			}
		} else {
			this.rows.add(new Row(Kind.QUERY, query, "„" + query + "“ suchen", null, new ItemStack(Items.SPYGLASS), null, -1, 0));
			for (AuctionSearch.Match match : AuctionSearch.search(query, MAX_SUGGESTIONS)) {
				AuctionSearch.Entry entry = match.entry();
				String subtitle = entry.enchantment() ? "Verzauberung" : null;
				if (entry.custom()) {
					subtitle = (entry.limited() ? "Limitiert" : "HugoSMP-Item") + (entry.baseName() != null ? " · " + entry.baseName() : "");
				} else if (!entry.englishName().equalsIgnoreCase(entry.gameName())) {
					subtitle = subtitle == null ? entry.englishName() : subtitle + " · " + entry.englishName();
				}
				this.rows.add(new Row(Kind.ITEM, entry.sendName(), entry.gameName(), subtitle, entry.icon(), entry.priceKey(),
					match.highlight(), query.length(), entry.color(), entry.limited()));
			}
		}
		while (this.rows.size() > this.visibleRows) {
			this.rows.remove(this.rows.size() - 1);
		}
		if (previousKind != Kind.ITEM || this.selected >= this.rows.size()) {
			this.selected = 0;
		}
	}

	private Row termRow(Kind kind, String term) {
		AuctionSearch.Entry entry = AuctionSearch.exact(term);
		ItemStack icon = entry != null ? entry.icon() : new ItemStack(kind == Kind.FAVORITE ? Items.NETHER_STAR : Items.PAPER);
		return new Row(kind, term, term, kind == Kind.FAVORITE ? "Favorit" : "Letzte Suche", icon,
			entry != null ? entry.priceKey() : null, -1, 0, entry != null ? entry.color() : 0, entry != null && entry.limited());
	}

	private void submit(String query) {
		query = query.strip();
		if (query.length() < AuctionSearch.MIN_LENGTH) {
			this.tooShortUntil = System.currentTimeMillis() + 2000;
			return;
		}
		AuctionSearch.remember(query);
		this.sent = true;
		AuctionSearch.send(this.sign, this.front, query);
		this.minecraft.setScreenAndShow(null);
	}

	private void useSign() {
		this.sent = true;
		AuctionSearch.openVanilla(this.sign, this.front);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(null);
	}

	@Override
	public void removed() {
		// The server waits for the sign; closing without a search answers with an empty line, like vanilla.
		if (!this.sent) {
			this.sent = true;
			AuctionSearch.send(this.sign, this.front, "");
		}
		AuctionPriceMemory.saveIfDirty();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			this.submit(this.rows.isEmpty() ? this.field.getValue() : this.rows.get(this.selected).send());
			return true;
		}
		if ((key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) && !this.rows.isEmpty()) {
			int step = key == InputConstants.KEY_DOWN ? 1 : -1;
			this.selected = Math.floorMod(this.selected + step, this.rows.size());
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			Row row = this.tabTarget();
			if (row != null) {
				this.field.setValue(row.send());
				this.field.moveCursorToEnd(false);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	/** The row Tab completes to: the selected one, or the first suggestion. */
	private Row tabTarget() {
		if (this.rows.isEmpty()) {
			return null;
		}
		Row row = this.rows.get(this.selected);
		if (row.kind() == Kind.QUERY && this.rows.size() > 1) {
			return this.rows.get(1);
		}
		return row;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = this.rowAt(event.x(), event.y());
		if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			Row row = this.rows.get(index);
			if (row.kind() != Kind.QUERY && event.x() >= this.starX()) {
				AuctionSearch.toggleFavorite(row.send());
				this.refresh();
			} else if (this.canMark(row) && event.x() >= this.markX()) {
				MarkedItems.toggle(row.priceKey());
			} else {
				this.submit(row.send());
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int rowAt(double x, double y) {
		if (x < this.left || x >= this.left + this.panelWidth || y < this.rowsTop) {
			return -1;
		}
		int index = (int) ((y - this.rowsTop) / ROW);
		return index < this.rows.size() ? index : -1;
	}

	private int starX() {
		return this.left + this.panelWidth - STAR - 4;
	}

	private int markX() {
		return this.starX() - MARK;
	}

	/** Rows for one item can be marked, so that item stands out in the auction house. */
	private boolean canMark(Row row) {
		return row.kind() != Kind.QUERY && row.priceKey() != null && ModConfigManager.CONFIG.auctionMarkHighlight;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, "— " + this.market.heading + " —", this.width / 2, this.field.getY() - 12, 0xFFAAAAAA);

		int hovered = this.rowAt(mouseX, mouseY);
		for (int i = 0; i < this.rows.size(); i++) {
			Row row = this.rows.get(i);
			boolean here = i == hovered;
			boolean onStar = here && mouseX >= this.starX();
			boolean onMark = here && !onStar && this.canMark(row) && mouseX >= this.markX();
			this.renderRow(graphics, row, this.rowsTop + i * ROW, i == this.selected || here, onStar, onMark);
		}
		if (hovered >= 0 && this.canMark(this.rows.get(hovered)) && mouseX >= this.markX() && mouseX < this.starX()) {
			boolean marked = MarkedItems.isMarked(this.rows.get(hovered).priceKey());
			graphics.setTooltipForNextFrame(this.font, List.of(
				Component.literal(marked ? "Markierung entfernen" : "Im AH markieren").getVisualOrderText(),
				Component.literal("Genau dieses Item bekommt im AH einen").withStyle(ChatFormatting.GRAY).getVisualOrderText(),
				Component.literal("farbigen Rahmen, ähnliche nicht.").withStyle(ChatFormatting.GRAY).getVisualOrderText()),
				mouseX, mouseY);
		} else if (hovered >= 0 && mouseX < this.markX()) {
			// A HugoSMP item shows its own tooltip, like in the game: name, lore, model.
			Row row = this.rows.get(hovered);
			if (row.priceKey() != null && row.priceKey().startsWith("custom:") && !row.icon().isEmpty()) {
				graphics.setTooltipForNextFrame(this.font, CustomItems.tooltipCopy(row.icon()), mouseX, mouseY);
			}
		}
		if (this.rows.isEmpty()) {
			graphics.centeredText(this.font, "Tippe, um Items zu finden. Letzte Suchen erscheinen hier.", this.width / 2,
				this.rowsTop + 8, 0xFF777777);
		} else if (this.field.getValue().strip().length() > 0 && this.rows.size() == 1) {
			graphics.centeredText(this.font, "Kein passendes Item, du kannst trotzdem danach suchen.", this.width / 2,
				this.rowsTop + ROW + 8, 0xFF777777);
		}

		int footerY = this.height - 20;
		if (System.currentTimeMillis() < this.tooShortUntil) {
			graphics.text(this.font, "Mindestens " + AuctionSearch.MIN_LENGTH + " Zeichen.", this.left, footerY, 0xFFFF5555);
		} else {
			graphics.text(this.font, "Enter sucht · Tab übernimmt", this.left, footerY, 0xFF777777);
		}
	}

	private void renderRow(GuiGraphicsExtractor graphics, Row row, int y, boolean active, boolean starHovered, boolean markHovered) {
		int x = this.left;
		int height = ROW - 2;
		HugoStyle.drawBox(graphics, x, y, this.panelWidth, height, active ? HugoStyle.State.HOVER : HugoStyle.State.NORMAL);
		boolean marked = MarkedItems.isMarked(row.priceKey()) && row.kind() != Kind.QUERY;
		if (marked && ModConfigManager.CONFIG.auctionMarkHighlight) {
			// The same frame the item gets in the auction house.
			int iconY = y + (height - 16) / 2;
			graphics.fill(x + 3, iconY - 1, x + 21, iconY + 17, MarkedItems.FILL);
			graphics.outline(x + 3, iconY - 1, 18, 18, MarkedItems.COLOR);
		}
		graphics.item(row.icon(), x + 4, y + (height - 16) / 2);

		int textX = x + 25;
		int right = x + this.panelWidth - 6;
		if (row.kind() != Kind.QUERY) {
			right = this.starX() - 4;
			boolean favorite = AuctionSearch.isFavorite(row.send());
			int starColor = favorite ? 0xFFFFD84A : starHovered ? 0xFFDDDDDD : 0xFF666666;
			graphics.text(this.font, favorite ? "★" : "☆", this.starX() + 4, y + (height - 8) / 2, starColor);
			if (this.canMark(row)) {
				right = this.markX() - 2;
				int boxX = this.markX() + 2;
				int boxY = y + (height - 9) / 2;
				int frame = marked ? MarkedItems.COLOR : markHovered ? 0xFFDDDDDD : 0xFF666666;
				graphics.outline(boxX, boxY, 9, 9, frame);
				if (marked) {
					graphics.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7, MarkedItems.COLOR);
				} else if (markHovered) {
					graphics.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7, 0x60FFFFFF);
				}
			}
		}

		String priceText = null;
		String ageText = null;
		AuctionPriceMemory.Seen seen = AuctionPriceMemory.get(row.priceKey(), this.market);
		AuctionPriceMemory.Seen perHour = row.limited() && ModConfigManager.CONFIG.limitedItemsPerHour
			? AuctionPriceMemory.getPerHour(row.priceKey(), this.market) : null;
		if (perHour != null && this.market == AuctionMarket.AUCTION) {
			// For limited items what counts is the price per hour of time left.
			priceText = "ab " + LimitedOffers.perHour(perHour.min);
			ageText = ageText(perHour.minAt);
			int priceWidth = Math.max(this.font.width(priceText), this.font.width(ageText));
			graphics.text(this.font, priceText, right - this.font.width(priceText), y + 3, 0xFFFFAA00);
			graphics.text(this.font, ageText, right - this.font.width(ageText), y + 12, 0xFF777777);
			right -= priceWidth + 6;
		} else if (seen != null) {
			// Buying in the auction house, the cheapest offer counts; selling to orders, the best one.
			boolean orders = this.market == AuctionMarket.ORDERS;
			priceText = orders ? "bis " + formatPrice(seen.max) : "ab " + formatPrice(seen.min);
			ageText = ageText(orders ? seen.maxAt : seen.minAt);
			int priceWidth = Math.max(this.font.width(priceText), this.font.width(ageText));
			graphics.text(this.font, priceText, right - this.font.width(priceText), y + 3, 0xFFFFAA00);
			graphics.text(this.font, ageText, right - this.font.width(ageText), y + 12, 0xFF777777);
			right -= priceWidth + 6;
		}

		int titleY = row.subtitle() == null ? y + (height - 8) / 2 : y + 3;
		String title = this.font.plainSubstrByWidth(row.title(), right - textX);
		int color = row.kind() == Kind.QUERY ? 0xFFFFFFFF : row.color() != 0 ? row.color() : 0xFFE0E0E0;
		if (row.highlight() >= 0 && row.highlight() + row.highlightLength() <= title.length()) {
			String before = title.substring(0, row.highlight());
			String match = title.substring(row.highlight(), row.highlight() + row.highlightLength());
			int at = textX;
			graphics.text(this.font, before, at, titleY, color);
			at += this.font.width(before);
			graphics.text(this.font, match, at, titleY, 0xFFFFFF55);
			at += this.font.width(match);
			graphics.text(this.font, title.substring(row.highlight() + row.highlightLength()), at, titleY, color);
		} else {
			graphics.text(this.font, title, textX, titleY, color);
		}
		if (row.subtitle() != null) {
			graphics.text(this.font, this.font.plainSubstrByWidth(row.subtitle(), right - textX), textX, y + 12, 0xFF888888);
		}
	}

	static String formatPrice(double price) {
		// Grouped like HugoSMP writes prices: $7.000, $12,50.
		return price >= 100 ? String.format(Locale.GERMANY, "$%,.0f", price) : String.format(Locale.GERMANY, "$%,.2f", price);
	}

	static String ageText(long at) {
		long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
		if (minutes < 60) {
			return minutes <= 1 ? "gerade eben" : "vor " + minutes + " Min.";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return "vor " + hours + " Std.";
		}
		long days = hours / 24;
		return days == 1 ? "vor 1 Tag" : "vor " + days + " Tagen";
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		// Dim the world so the rows and the hint stay readable over the HUD.
		graphics.fill(0, 0, this.width, this.height, 0x90000000);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
