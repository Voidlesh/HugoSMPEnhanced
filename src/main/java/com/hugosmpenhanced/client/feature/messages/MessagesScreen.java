package com.hugosmpenhanced.client.feature.messages;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.feature.tpa.TpaState;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.settings.Theme;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? else
import org.lwjgl.glfw.GLFW;
//? end

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The messages tab: every private conversation as a list on the left, the chat with the selected
 * player on the right as speech bubbles, a field to answer and buttons for a TPA request.
 */
public class MessagesScreen extends Screen implements StyledScreen {
	private static final int ACCENT = 0xFF7CC4FF;
	private static final int OUT_BUBBLE = 0xFF1F4D2D;
	private static final int IN_BUBBLE = 0xFF2C2C2C;
	private static final int ROW_H = 26;
	private static final int HEAD_H = 32;
	private static final int INPUT_H = 18;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd.MM.");
	private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

	private enum Kind {
		CHAT, ONLINE, NEW
	}

	private record ListRow(Kind kind, String name, MessageLog.Conversation chat) {
	}

	/** One thing in the message area: a bubble, a day mark or a note of the mod. */
	private static final class Item {
		int kind; // 0 bubble, 1 day, 2 note
		MessageLog.Message message;
		List<FormattedCharSequence> lines = new ArrayList<>();
		String label = "";
		int width;
		int height;
		int y;
		int lastLineWidth;
		boolean timeOnOwnLine;
	}

	private final Screen parent;
	private final String initial;
	private String selected;
	private EditBox search;
	private EditBox input;
	private Button send;
	private Button tpa;
	private Button tpaHere;
	private Button accept;
	private Button delete;
	private boolean confirmDelete;
	private long confirmDeleteAt;
	private String note = "";
	private long noteAt;

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listWidth;
	private double listScroll;
	private double scroll;
	private List<Item> items = new ArrayList<>();
	private int itemsHeight;
	private String layoutKey = "";
	private int areaTop;
	private int areaBottom;
	private int paneLeft;
	private int paneRight;
	private int listTop;
	private int listBottom;

	public MessagesScreen(Screen parent) {
		this(parent, null);
	}

	public MessagesScreen(Screen parent, String open) {
		super(Component.literal("Nachrichten"));
		this.parent = parent;
		this.initial = open;
	}

	// ------------------------------------------------------------------ set-up

	@Override
	protected void init() {
		this.panelWidth = Math.min(500, this.width - 16);
		this.panelHeight = Math.min(this.height - 16, 300);
		this.left = (this.width - this.panelWidth) / 2;
		this.top = (this.height - this.panelHeight) / 2;
		this.listWidth = Math.max(110, Math.min(150, this.panelWidth * 3 / 10));
		this.paneLeft = this.left + this.listWidth + 1;
		this.paneRight = this.left + this.panelWidth;
		this.listTop = this.top + 52;
		this.listBottom = this.top + this.panelHeight - 6;

		String oldSearch = this.search != null ? this.search.getValue() : "";
		String oldInput = this.input != null ? this.input.getValue() : "";
		this.search = new EditBox(this.font, this.left + 8, this.top + 30, this.listWidth - 16, 16, Component.literal("Suchen"));
		this.search.setHint(Component.literal("Suchen oder Name…").withColor(Theme.FAINT));
		this.search.setMaxLength(24);
		this.search.setValue(oldSearch);
		this.search.setResponder(value -> this.listScroll = 0);
		this.addRenderableWidget(this.search);

		int paneWidth = this.paneRight - this.paneLeft;
		boolean narrow = paneWidth < 330;
		int by = this.top + 8;
		int x = this.paneRight - 8;
		this.delete = this.addRenderableWidget(Button.builder(Component.literal("×"), b -> this.onDelete())
			.bounds(x - 18, by, 18, 16).build());
		x -= 22;
		int hereWidth = narrow ? 42 : 70;
		this.tpaHere = this.addRenderableWidget(Button.builder(Component.literal(narrow ? "Zu mir" : "TPA hierher"), b -> this.onTpa(true))
			.bounds(x - hereWidth, by, hereWidth, 16).build());
		x -= hereWidth + 4;
		this.tpa = this.addRenderableWidget(Button.builder(Component.literal("TPA"), b -> this.onTpa(false))
			.bounds(x - 36, by, 36, 16).build());
		x -= 40;
		this.accept = this.addRenderableWidget(Button.builder(Component.literal("Annehmen"), b -> this.onAccept())
			.bounds(x - 54, by, 54, 16).build());

		int inputY = this.top + this.panelHeight - INPUT_H - 8;
		this.send = this.addRenderableWidget(Button.builder(Component.literal("Senden"), b -> this.onSend())
			.bounds(this.paneRight - 8 - 50, inputY, 50, INPUT_H).build());
		this.input = new EditBox(this.font, this.paneLeft + 8, inputY, paneWidth - 16 - 54, INPUT_H, Component.literal("Nachricht"));
		this.input.setValue(oldInput);
		this.addRenderableWidget(this.input);
		this.areaTop = this.top + HEAD_H + 4;
		this.areaBottom = inputY - 6;

		if (this.selected == null) {
			this.selected = this.initial != null ? this.initial : this.defaultChat();
			if (this.initial != null) {
				MessageLog.open(this.initial);
			}
		}
		this.select(this.selected, false);
	}

	private String defaultChat() {
		List<MessageLog.Conversation> chats = MessageLog.conversations();
		for (MessageLog.Conversation chat : chats) {
			if (chat.unread > 0) {
				return chat.name;
			}
		}
		return chats.isEmpty() ? null : chats.get(0).name;
	}

	private void select(String name, boolean focus) {
		this.selected = name;
		MessageLog.setViewing(name);
		this.scroll = 0;
		this.layoutKey = "";
		this.confirmDelete = false;
		if (name != null) {
			this.input.setMaxLength(MessageFeature.maxTextLength(name));
			this.input.setHint(Component.literal("Nachricht an " + name + "…").withColor(Theme.FAINT));
			if (focus || this.getFocused() == null) {
				this.setFocused(this.input);
			}
		}
		this.updateWidgets();
	}

	private void updateWidgets() {
		boolean any = this.selected != null;
		this.input.visible = any;
		this.input.active = any;
		this.send.visible = any;
		this.tpa.visible = any;
		this.tpaHere.visible = any;
		this.delete.visible = any && MessageLog.find(this.selected) != null;
		boolean pending = any && this.selected.equalsIgnoreCase(TpaState.pendingPlayer());
		this.accept.visible = pending;
		int wait = MessageFeature.tpaWait();
		this.tpa.active = wait == 0;
		this.tpaHere.active = wait == 0;
		this.tpa.setMessage(Component.literal(wait > 0 ? "TPA (" + wait + ")" : "TPA"));
		if (any) {
			this.tpa.setTooltip(Tooltip.create(Component.literal("Sendet /tpa " + this.selected + ": du willst zu ihm teleportieren")));
			this.tpaHere.setTooltip(Tooltip.create(Component.literal("Sendet /tpahere " + this.selected + ": er soll zu dir kommen")));
			this.accept.setTooltip(Tooltip.create(Component.literal("Nimmt die TPA-Anfrage von " + this.selected + " an")));
		}
		this.delete.setMessage(Component.literal(this.confirmDelete ? "Löschen?" : "×"));
		this.delete.setWidth(this.confirmDelete ? 46 : 18);
		this.delete.setX(this.paneRight - 8 - this.delete.getWidth());
		int shift = this.delete.getWidth() - 18;
		this.tpaHere.setX(this.paneRight - 8 - 22 - this.tpaHere.getWidth() - shift);
		this.tpa.setX(this.tpaHere.getX() - 4 - this.tpa.getWidth());
		this.accept.setX(this.tpa.getX() - 4 - this.accept.getWidth());
		this.delete.setTooltip(Tooltip.create(Component.literal(this.confirmDelete ? "Noch einmal klicken, um den Verlauf zu löschen"
			: "Verlauf mit " + this.selected + " löschen")));
		this.send.active = any && !this.input.getValue().isBlank();
	}

	// ------------------------------------------------------------------ actions

	private void onSend() {
		if (this.selected == null) {
			return;
		}
		String text = this.input.getValue();
		if (MessageFeature.send(this.selected, text)) {
			this.input.setValue("");
			this.scroll = 0;
			this.setFocused(this.input);
			this.say("Gesendet");
		} else if (!text.isBlank()) {
			this.say("Nicht gesendet");
		}
	}

	private void onTpa(boolean here) {
		if (this.selected != null && MessageFeature.tpa(this.selected, here)) {
			this.scroll = 0;
			this.say(here ? "/tpahere gesendet" : "/tpa gesendet");
		}
	}

	private void onAccept() {
		if (this.selected != null && MessageFeature.accept(this.selected)) {
			this.say("Angenommen");
		}
	}

	private void onDelete() {
		if (this.selected == null) {
			return;
		}
		if (!this.confirmDelete) {
			this.confirmDelete = true;
			this.confirmDeleteAt = System.currentTimeMillis();
			return;
		}
		MessageLog.delete(this.selected);
		this.say("Verlauf gelöscht");
		this.select(this.defaultChat(), false);
	}

	private void say(String text) {
		this.note = text;
		this.noteAt = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ data

	private String onlineName(String name) {
		var connection = Minecraft.getInstance().getConnection();
		if (connection == null || name == null) {
			return null;
		}
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(name)) {
				return info.getProfile().name();
			}
		}
		return null;
	}

	private PlayerInfo info(String name) {
		var connection = Minecraft.getInstance().getConnection();
		String online = this.onlineName(name);
		return connection == null || online == null ? null : connection.getPlayerInfo(online);
	}

	private List<ListRow> rows() {
		String query = this.search.getValue().trim().toLowerCase(Locale.ROOT);
		List<ListRow> rows = new ArrayList<>();
		for (MessageLog.Conversation chat : MessageLog.conversations()) {
			if (query.isEmpty() || chat.name.toLowerCase(Locale.ROOT).contains(query)) {
				rows.add(new ListRow(Kind.CHAT, chat.name, chat));
			}
		}
		if (this.selected != null && MessageLog.find(this.selected) == null && query.isEmpty()) {
			rows.add(0, new ListRow(Kind.NEW, this.selected, null));
		}
		if (!query.isEmpty()) {
			var connection = Minecraft.getInstance().getConnection();
			int shown = 0;
			if (connection != null) {
				for (PlayerInfo info : connection.getOnlinePlayers()) {
					String name = info.getProfile().name();
					if (shown < 6 && name.toLowerCase(Locale.ROOT).contains(query) && MessageLog.find(name) == null
						&& !name.equalsIgnoreCase(Minecraft.getInstance().player.getGameProfile().name())) {
						rows.add(new ListRow(Kind.ONLINE, name, null));
						shown++;
					}
				}
			}
			String typed = this.search.getValue().trim();
			// Only offered when nothing else matches, so a part of a known name does not add noise.
			if (rows.isEmpty() && typed.matches("[A-Za-z0-9_.]{3,17}")) {
				rows.add(new ListRow(Kind.NEW, typed, null));
			}
		}
		return rows;
	}

	private static String clock(long time) {
		LocalDate day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate today = LocalDate.now();
		if (day.equals(today)) {
			return TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
		}
		return day.equals(today.minusDays(1)) ? "Gestern" : SHORT_DAY.format(day);
	}

	private static String dayLabel(LocalDate day) {
		LocalDate today = LocalDate.now();
		if (day.equals(today)) {
			return "Heute";
		}
		return day.equals(today.minusDays(1)) ? "Gestern" : DAY.format(day);
	}

	// ------------------------------------------------------------------ message layout

	private void layout() {
		MessageLog.Conversation chat = MessageLog.find(this.selected);
		int maxBubble = Math.max(80, (this.paneRight - this.paneLeft - 24) * 72 / 100);
		String key = this.selected + "|" + maxBubble + "|" + (chat == null ? 0 : chat.messages.size())
			+ "|" + (chat == null ? 0 : chat.lastTime()) + "|" + (chat != null && !chat.messages.isEmpty() && chat.messages.get(chat.messages.size() - 1).failed);
		if (key.equals(this.layoutKey)) {
			return;
		}
		this.layoutKey = key;
		this.items = new ArrayList<>();
		this.itemsHeight = 0;
		if (chat == null) {
			return;
		}
		LocalDate lastDay = null;
		int y = 4;
		int timeWidth = Math.round(this.font.width("00:00") * 0.75f);
		for (MessageLog.Message message : chat.messages) {
			LocalDate day = Instant.ofEpochMilli(message.time).atZone(ZoneId.systemDefault()).toLocalDate();
			if (!day.equals(lastDay)) {
				lastDay = day;
				Item mark = new Item();
				mark.kind = 1;
				mark.label = dayLabel(day);
				mark.width = this.font.width(mark.label) + 12;
				mark.height = 13;
				mark.y = y;
				y += mark.height + 5;
				this.items.add(mark);
			}
			Item item = new Item();
			item.message = message;
			if (message.note) {
				item.kind = 2;
				item.label = message.text;
				item.width = Math.min(this.paneRight - this.paneLeft - 40, this.font.width(item.label) + 12);
				item.height = 13;
			} else {
				item.kind = 0;
				item.lines = this.font.split(Component.literal(message.text), maxBubble - 12);
				int widest = 0;
				for (FormattedCharSequence line : item.lines) {
					widest = Math.max(widest, this.font.width(line));
				}
				item.lastLineWidth = item.lines.isEmpty() ? 0 : this.font.width(item.lines.get(item.lines.size() - 1));
				item.timeOnOwnLine = item.lastLineWidth + 6 + timeWidth > maxBubble - 12;
				int inner = Math.max(widest, item.timeOnOwnLine ? timeWidth : item.lastLineWidth + 6 + timeWidth);
				item.width = inner + 12;
				item.height = 6 + item.lines.size() * 10 + (item.timeOnOwnLine ? 8 : 0) + (message.failed ? 10 : 0);
			}
			item.y = y;
			y += item.height + 3;
			this.items.add(item);
		}
		this.itemsHeight = y + 2;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (this.confirmDelete && System.currentTimeMillis() - this.confirmDeleteAt > 3500) {
			this.confirmDelete = false;
		}
		this.updateWidgets();
		this.layout();

		graphics.fill(this.left, this.top, this.left + this.panelWidth, this.top + this.panelHeight, 0xF6121212);
		graphics.fill(this.left + 1, this.top + 1, this.left + this.listWidth, this.top + this.panelHeight - 1, Theme.SIDEBAR);
		HugoStyle.drawOutline(graphics, this.left, this.top, this.panelWidth, this.panelHeight, Theme.FRAME_BORDER);
		graphics.fill(this.left + 1, this.top + 1, this.left + this.panelWidth - 1, this.top + 3, ACCENT);
		graphics.fill(this.left + this.listWidth, this.top + 3, this.left + this.listWidth + 1, this.top + this.panelHeight - 1, Theme.FRAME_BORDER);

		this.renderList(graphics, mouseX, mouseY);
		if (this.selected == null) {
			this.renderEmpty(graphics);
		} else {
			this.renderHeader(graphics);
			this.renderMessages(graphics, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		this.renderTooltips(graphics, mouseX, mouseY);
	}

	private void renderEmpty(GuiGraphicsExtractor graphics) {
		int cx = (this.paneLeft + this.paneRight) / 2;
		int cy = (this.top + this.panelHeight) / 2;
		graphics.centeredText(this.font, "Noch keine Unterhaltung", cx, cy - 14, Theme.TEXT);
		graphics.centeredText(this.font, "Schreib links einen Spielernamen ins Feld", cx, cy, Theme.MUTED);
		graphics.centeredText(this.font, "oder warte, bis dir jemand per /msg schreibt.", cx, cy + 10, Theme.MUTED);
		if (!MessageFeature.active()) {
			graphics.centeredText(this.font, "Nachrichten werden nur auf HugoSMP mitgelesen.", cx, cy + 28, Theme.GOLD);
		}
	}

	private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, "Nachrichten", this.left + 8, this.top + 10, Theme.WHITE, false);
		int unread = MessageLog.unreadTotal();
		if (unread > 0) {
			String badge = String.valueOf(unread);
			int bw = this.font.width(badge) + 8;
			int bx = this.left + 8 + this.font.width("Nachrichten") + 6;
			graphics.fill(bx, this.top + 8, bx + bw, this.top + 19, Theme.GREEN);
			graphics.text(this.font, badge, bx + 4, this.top + 10, Theme.WHITE, false);
		}
		List<ListRow> rows = this.rows();
		int content = rows.size() * ROW_H;
		int visible = this.listBottom - this.listTop;
		this.listScroll = Mth.clamp(this.listScroll, 0, Math.max(0, content - visible));
		if (rows.isEmpty()) {
			graphics.centeredText(this.font, this.search.getValue().isBlank() ? "Noch nichts" : "Keine Treffer", this.left + this.listWidth / 2, this.listTop + 12, Theme.MUTED);
			return;
		}
		graphics.enableScissor(this.left + 1, this.listTop, this.left + this.listWidth, this.listBottom);
		long now = System.currentTimeMillis();
		for (int i = 0; i < rows.size(); i++) {
			ListRow row = rows.get(i);
			int y = this.listTop + i * ROW_H - (int) this.listScroll;
			if (y + ROW_H < this.listTop || y > this.listBottom) {
				continue;
			}
			boolean over = mouseX >= this.left && mouseX < this.left + this.listWidth && mouseY >= Math.max(y, this.listTop)
				&& mouseY < Math.min(y + ROW_H, this.listBottom);
			boolean selected = row.name().equalsIgnoreCase(this.selected);
			if (selected) {
				graphics.fill(this.left + 1, y, this.left + this.listWidth, y + ROW_H, Theme.SELECTED);
				graphics.fill(this.left + 1, y, this.left + 3, y + ROW_H, Theme.GOLD);
			} else if (over) {
				graphics.fill(this.left + 1, y, this.left + this.listWidth, y + ROW_H, Theme.ROW_HOVER);
			}
			this.avatar(graphics, row.name(), this.left + 7, y + 4, 18);
			int textX = this.left + 29;
			int textWidth = this.left + this.listWidth - 6 - textX;
			String time = "";
			boolean hasUnread = row.chat() != null && row.chat().unread > 0;
			if (row.chat() != null && row.chat().lastTime() > 0) {
				time = clock(row.chat().lastTime());
			}
			int timeWidth = this.font.width(time);
			Component name = Component.literal(row.name());
			if (hasUnread) {
				name = name.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
			}
			clipped(graphics, this.font, name, textX, y + 4, textWidth - timeWidth - 4, hasUnread ? Theme.WHITE : Theme.TEXT);
			if (!time.isEmpty()) {
				graphics.text(this.font, time, this.left + this.listWidth - 6 - timeWidth, y + 4, hasUnread ? Theme.GREEN : Theme.FAINT, false);
			}
			String preview;
			int previewColor = Theme.MUTED;
			if (row.kind() == Kind.CHAT) {
				MessageLog.Message last = row.chat().last();
				preview = last == null ? "" : (last.out ? "Du: " : "") + last.text;
				if (last != null && last.failed) {
					previewColor = Theme.RED_TEXT;
				}
			} else if (row.kind() == Kind.ONLINE) {
				preview = "online";
				previewColor = Theme.GREEN;
			} else {
				preview = "Neue Unterhaltung";
				previewColor = Theme.GOLD;
			}
			int badgeWidth = 0;
			if (hasUnread) {
				String count = String.valueOf(Math.min(99, row.chat().unread));
				badgeWidth = this.font.width(count) + 7;
				int bx = this.left + this.listWidth - 6 - badgeWidth;
				graphics.fill(bx, y + 14, bx + badgeWidth, y + 24, Theme.GREEN);
				graphics.text(this.font, count, bx + 3, y + 15, Theme.WHITE, false);
			}
			clipped(graphics, this.font, Component.literal(preview), textX, y + 15, textWidth - badgeWidth - (badgeWidth > 0 ? 3 : 0), previewColor);
			graphics.fill(this.left + 6, y + ROW_H - 1, this.left + this.listWidth - 6, y + ROW_H, Theme.DIVIDER);
		}
		graphics.disableScissor();
		if (content > visible) {
			int barHeight = Math.max(14, visible * visible / content);
			int barY = this.listTop + (int) ((visible - barHeight) * (this.listScroll / (content - visible)));
			graphics.fill(this.left + this.listWidth - 3, barY, this.left + this.listWidth - 1, barY + barHeight, 0xFFA0A0A0);
		}
	}

	private void avatar(GuiGraphicsExtractor graphics, String name, int x, int y, int size) {
		PlayerInfo info = this.info(name);
		if (info != null) {
			//? if modern
			PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), x, y, size);
			//? else
			PlayerFaceRenderer.draw(graphics, info.getSkin(), x, y, size);
			//? end
			graphics.fill(x + size - 4, y + size - 4, x + size + 1, y + size + 1, Theme.FRAME);
			graphics.fill(x + size - 3, y + size - 3, x + size, y + size, Theme.GREEN);
			return;
		}
		int hash = name.toLowerCase(Locale.ROOT).hashCode();
		int color = 0xFF000000 | Mth.hsvToRgb(Math.floorMod(hash, 360) / 360f, 0.45f, 0.55f);
		graphics.fill(x, y, x + size, y + size, color);
		String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		graphics.text(this.font, letter, x + (size - this.font.width(letter)) / 2 + 1, y + (size - 8) / 2 + 1, Theme.WHITE, false);
	}

	private void renderHeader(GuiGraphicsExtractor graphics) {
		this.avatar(graphics, this.selected, this.paneLeft + 8, this.top + 8, 20);
		int x = this.paneLeft + 34;
		graphics.text(this.font, this.selected, x, this.top + 9, Theme.WHITE, false);
		String online = this.onlineName(this.selected);
		String status = online != null ? "online" : "offline";
		graphics.text(this.font, status, x, this.top + 20, online != null ? Theme.GREEN : Theme.FAINT, false);
		if (!this.note.isEmpty() && System.currentTimeMillis() - this.noteAt < 2500) {
			int noteX = x + Math.max(this.font.width(this.selected), this.font.width(status)) + 10;
			int limit = (this.accept.visible ? this.accept.getX() : this.tpa.getX()) - 4;
			if (noteX + this.font.width(this.note) <= limit) {
				graphics.text(this.font, this.note, noteX, this.top + 20, Theme.MUTED, false);
			}
		}
		graphics.fill(this.paneLeft + 4, this.top + HEAD_H, this.paneRight - 4, this.top + HEAD_H + 1, Theme.SECTION_BORDER);
	}

	private void renderMessages(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int height = this.areaBottom - this.areaTop;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.itemsHeight - height));
		if (this.items.isEmpty()) {
			int cx = (this.paneLeft + this.paneRight) / 2;
			graphics.centeredText(this.font, "Noch keine Nachrichten mit " + this.selected, cx, this.areaTop + height / 2 - 10, Theme.MUTED);
			graphics.centeredText(this.font, "Schreib unten die erste.", cx, this.areaTop + height / 2 + 2, Theme.FAINT);
			return;
		}
		int baseline = this.areaBottom - this.itemsHeight + (int) this.scroll;
		int center = (this.paneLeft + this.paneRight) / 2;
		graphics.enableScissor(this.paneLeft, this.areaTop, this.paneRight, this.areaBottom);
		for (Item item : this.items) {
			int y = baseline + item.y;
			if (y + item.height < this.areaTop || y > this.areaBottom) {
				continue;
			}
			if (item.kind != 0) {
				int x = center - item.width / 2;
				Theme.panel(graphics, x, y, item.width, item.height, item.kind == 1 ? 0xFF262626 : 0xFF1C1C1C, 0);
				graphics.text(this.font, item.label, x + 6, y + 3, item.kind == 1 ? Theme.TEXT_DIM : Theme.GOLD, false);
				continue;
			}
			boolean out = item.message.out;
			int x = out ? this.paneRight - 10 - item.width : this.paneLeft + 10;
			Theme.panel(graphics, x, y, item.width, item.height, out ? OUT_BUBBLE : IN_BUBBLE, 0);
			int ty = y + 4;
			for (FormattedCharSequence line : item.lines) {
				graphics.text(this.font, line, x + 6, ty, Theme.WHITE, false);
				ty += 10;
			}
			String time = TIME.format(Instant.ofEpochMilli(item.message.time).atZone(ZoneId.systemDefault()));
			int timeW = Math.round(this.font.width(time) * 0.75f);
			int timeX = x + item.width - 6 - timeW;
			int timeY = item.timeOnOwnLine ? ty - 2 : ty - 10 + 2;
			graphics.pose().pushMatrix();
			graphics.pose().translate(timeX, timeY + 1);
			graphics.pose().scale(0.75f, 0.75f);
			graphics.text(this.font, time, 0, 0, out ? 0xFF8FC59F : Theme.MUTED, false);
			graphics.pose().popMatrix();
			if (item.message.failed) {
				graphics.text(this.font, "Nicht zugestellt", x + 6, y + item.height - 11, Theme.RED_TEXT, false);
			}
		}
		graphics.disableScissor();
		if (this.itemsHeight > height) {
			int barHeight = Math.max(14, height * height / this.itemsHeight);
			double share = 1 - this.scroll / (this.itemsHeight - height);
			int barY = this.areaTop + (int) ((height - barHeight) * share);
			graphics.fill(this.paneRight - 4, barY, this.paneRight - 2, barY + barHeight, 0x70FFFFFF);
		}
		if (this.scroll > 8) {
			int bx = this.paneRight - 30;
			int by = this.areaBottom - 20;
			boolean over = mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 16;
			Theme.panel(graphics, bx, by, 20, 16, over ? 0xF0383838 : 0xF0222222, Theme.CHIP_BORDER);
			Theme.glyph(graphics, Theme.DOWN, bx + 7, by + 6, Theme.WHITE);
		}
	}

	private void renderTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (mouseX < this.paneLeft || mouseX >= this.paneRight || mouseY < this.areaTop || mouseY >= this.areaBottom) {
			return;
		}
		int baseline = this.areaBottom - this.itemsHeight + (int) this.scroll;
		for (Item item : this.items) {
			if (item.kind != 0) {
				continue;
			}
			int y = baseline + item.y;
			int x = item.message.out ? this.paneRight - 10 - item.width : this.paneLeft + 10;
			if (mouseX >= x && mouseX < x + item.width && mouseY >= y && mouseY < y + item.height) {
				graphics.setComponentTooltipForNextFrame(this.font, List.of(
					Component.literal(FULL.format(Instant.ofEpochMilli(item.message.time).atZone(ZoneId.systemDefault()))),
					Component.literal("Rechtsklick: Text kopieren").withColor(Theme.MUTED)), mouseX, mouseY);
				return;
			}
		}
	}

	private static void clipped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
		String plain = text.getString();
		if (font.width(plain) > width) {
			text = Component.literal(font.plainSubstrByWidth(plain, Math.max(0, width - font.width("…"))) + "…").withStyle(text.getStyle());
		}
		graphics.text(font, text, x, y, color, false);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		if (mx >= this.left && mx < this.left + this.listWidth && my >= this.listTop && my < this.listBottom) {
			List<ListRow> rows = this.rows();
			int index = (int) ((my - this.listTop + this.listScroll) / ROW_H);
			if (index >= 0 && index < rows.size()) {
				ListRow row = rows.get(index);
				if (row.kind() != Kind.CHAT) {
					MessageLog.open(row.name());
				}
				this.select(row.name(), true);
				if (row.kind() != Kind.CHAT) {
					this.search.setValue("");
				}
				return true;
			}
		}
		if (this.selected != null && this.scroll > 8 && mx >= this.paneRight - 30 && mx < this.paneRight - 10
			&& my >= this.areaBottom - 20 && my < this.areaBottom - 4) {
			this.scroll = 0;
			return true;
		}
		if (event.button() == 1 && this.selected != null && mx >= this.paneLeft && mx < this.paneRight && my >= this.areaTop && my < this.areaBottom) {
			int baseline = this.areaBottom - this.itemsHeight + (int) this.scroll;
			for (Item item : this.items) {
				if (item.kind != 0) {
					continue;
				}
				int y = baseline + item.y;
				int x = item.message.out ? this.paneRight - 10 - item.width : this.paneLeft + 10;
				if (mx >= x && mx < x + item.width && my >= y && my < y + item.height) {
					this.minecraft.keyboardHandler.setClipboard(item.message.text);
					this.say("Kopiert");
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < this.left + this.listWidth) {
			this.listScroll -= scrollY * ROW_H;
			return true;
		}
		this.scroll += scrollY * 20;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && this.getFocused() == this.input) {
			this.onSend();
			return true;
		}
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && this.getFocused() == this.search) {
			List<ListRow> rows = this.rows();
			if (!rows.isEmpty()) {
				ListRow row = rows.get(0);
				MessageLog.open(row.name());
				this.select(row.name(), true);
				this.search.setValue("");
				return true;
			}
		}
		//? if modern
		if (event.key() == InputConstants.KEY_PAGEUP) {
		//? else
		if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
		//? end
			this.scroll += 100;
			return true;
		}
		//? if modern
		if (event.key() == InputConstants.KEY_PAGEDOWN) {
		//? else
		if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
		//? end
			this.scroll -= 100;
			return true;
		}
		return super.keyPressed(event);
	}

	/** Game tests: type into the search field. */
	public void setSearchForTest(String text) {
		this.search.setValue(text);
	}

	/** Game tests: open a conversation. */
	public void selectForTest(String name) {
		this.select(name, true);
	}

	@Override
	public void onClose() {
		MessageLog.setViewing(null);
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void removed() {
		MessageLog.setViewing(null);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
