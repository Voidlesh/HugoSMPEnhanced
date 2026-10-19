package com.hugosmpenhanced.client.feature.colorcodes;

import com.hugosmpenhanced.client.util.Formats;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;


import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.mixin.EditBoxAccessor;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Editor for /rename and /sign texts: the raw text with codes, a live preview of the result, the 16
 * chat colours and formats, a colour picker with gradients, and saved templates on the left whose
 * text can be loaded or whose look can be put on the current text. Without a selection, colours
 * and formats apply from the cursor to the end (or to everything with the cursor at either end).
 */
public class ColorCodeScreen extends Screen implements StyledScreen {
	private static final int ROW = 16;
	private static final int UNDO_LIMIT = 100;
	private static final long TYPING_MERGE_MS = 700;

	private static final ChatFormatting[] PALETTE = {
		ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
		ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
		ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
		ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE};
	private static final String[] PALETTE_NAMES = {
		"Schwarz", "Dunkelblau", "Dunkelgrün", "Dunkeltürkis", "Dunkelrot", "Lila", "Gold", "Grau",
		"Dunkelgrau", "Blau", "Grün", "Türkis", "Rot", "Pink", "Gelb", "Weiß"};
	private static final int[] FORMAT_BITS = {ColorText.BOLD, ColorText.ITALIC, ColorText.UNDERLINE, ColorText.STRIKE,
		ColorText.OBFUSCATED};
	private static final String[] FORMAT_NAMES = {"Fett", "Kursiv", "Unterstrichen", "Durchgestrichen", "Verschleiert"};
	private static final String[] FORMAT_LETTERS = {"B", "I", "U", "S", "?"};

	private static final String[] BRUSH = {"...##", "..###", ".###.", "##...", "#...."};
	private static final String[] SWAP = {".#...", "#####", ".#..#", "...##", "#####", "...#."};

	private static final int SLOT_COLOR = 0;
	private static final int SLOT_FROM = 1;
	private static final int SLOT_TO = 2;
	private static final int[] SLOTS = {0xFFAA00, 0xFF8C1A, 0xD14BFF};
	private static int lastSlot;
	/** Gradient ends when only the 16 chat colours are used. */
	private static final ChatFormatting[] LEGACY_SLOTS = {ChatFormatting.DARK_RED, ChatFormatting.YELLOW};
	/** The legacy gradient end that the next palette click sets, or -1. */
	private int pickingEnd = -1;

	private final Screen parent;
	/** The chat input to go back to on cancel, or null when not opened from chat. */
	private final String chatOrigin;
	private String command;
	private String raw;

	private EditBox input;
	private EditBox hexField;
	private EditBox nameField;
	private boolean naming;
	private boolean settingValue;
	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private long lastTyped;

	private int slot = lastSlot;
	private float hue;
	private float sat;
	private float val;
	private int dragging;

	private int templateScroll;
	private int deleteArmed = -1;
	private long deleteUntil;
	private String flash;
	private long flashUntil;

	// layout, recomputed in init()
	private int fx;
	private int fy;
	private int fw;
	private int fh;
	private int listWidth;
	private int cx;
	private int cw;
	private int swatch;
	private int pickerW;
	private int pickerH;

	public ColorCodeScreen(Screen parent, String command, String text, String chatOrigin) {
		super(Component.literal("Farbcode-Editor"));
		this.parent = parent;
		this.command = command;
		this.raw = text;
		this.chatOrigin = chatOrigin;
		this.loadSlot();
	}

	/** Opened by key or from the settings: /rename with the held item's current name. */
	public static ColorCodeScreen forHeldItem(Screen parent) {
		return new ColorCodeScreen(parent, ColorCodes.commands().get(0), ColorCodes.heldItemName(), null);
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		this.fw = Math.min(this.width - 12, 540);
		this.fh = Math.min(this.height - 12, 300);
		this.fx = (this.width - this.fw) / 2;
		this.fy = (this.height - this.fh) / 2;
		this.listWidth = this.fw < 420 ? 96 : 120;
		this.cx = this.fx + this.listWidth + 10;
		this.cw = this.fx + this.fw - 10 - this.cx;
		int formats = FORMAT_BITS.length + 1;
		this.swatch = Mth.clamp((this.cw - 8 - formats * 13) / 16, 8, 16);
		this.pickerW = Mth.clamp(this.cw / 4, 64, 96);
		this.pickerH = this.fh >= 280 ? 56 : 44;

		this.input = new EditBox(this.font, this.cx + 4, this.inputY() + 4, this.cw - 8, 10, Component.literal("Text"));
		this.input.setBordered(false);
		this.input.setMaxLength(1024);
		this.input.setHint(Component.literal("Text eingeben, z. B. &6Hugo&eSMP").withStyle(ChatFormatting.DARK_GRAY));
		this.input.addFormatter(this::highlightCodes);
		this.settingValue = true;
		this.input.setValue(this.raw);
		this.settingValue = false;
		this.input.setResponder(this::onTyped);
		this.addRenderableWidget(this.input);

		this.hexField = new EditBox(this.font, this.cx + 4, this.hexY() + 3, this.pickerW - 8, 10, Component.literal("Hex"));
		this.hexField.setBordered(false);
		this.hexField.setMaxLength(9);
		this.hexField.setValue(String.format("#%06X", SLOTS[this.slot]));
		this.hexField.setResponder(value -> {
			Integer rgb = ColorCodes.parseHex(value);
			if (rgb != null && this.hexField.isFocused()) {
				SLOTS[this.slot] = rgb;
				this.loadSlot();
			}
		});
		this.hexField.visible = !ColorCodes.legacy();
		this.addRenderableWidget(this.hexField);

		this.nameField = new EditBox(this.font, this.fx + 10, this.saveY() + 3, this.listWidth - 34, 10, Component.literal("Name"));
		this.nameField.setBordered(false);
		this.nameField.setMaxLength(32);
		this.nameField.setHint(Component.literal("Name").withStyle(ChatFormatting.DARK_GRAY));
		this.nameField.visible = this.naming;
		this.addRenderableWidget(this.nameField);

		this.setInitialFocus(this.naming ? this.nameField : this.input);
	}

	private int previewY() {
		return this.fy + 28;
	}

	private int previewH() {
		return this.fh >= 280 ? 40 : 30;
	}

	private int inputY() {
		return this.previewY() + this.previewH() + 5;
	}

	private int infoY() {
		return this.inputY() + 20;
	}

	private int paletteY() {
		return this.infoY() + 14;
	}

	private int pickerY() {
		return this.paletteY() + this.swatch + 9;
	}

	private int hueY() {
		return this.pickerY() + this.pickerH + 3;
	}

	private int hexY() {
		return this.hueY() + 10;
	}

	private int bottomY() {
		return this.fy + this.fh - 22;
	}

	private int saveY() {
		return this.fy + this.fh - 22;
	}

	private int listTop() {
		return this.fy + 40;
	}

	private int listRows() {
		return Math.max(1, (this.saveY() - 4 - this.listTop()) / ROW);
	}

	// ------------------------------------------------------------------ text state

	private void onTyped(String value) {
		// Moving the cursor also calls the responder, with the same text.
		if (this.settingValue || value.equals(this.raw)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - this.lastTyped > TYPING_MERGE_MS || this.undo.isEmpty()) {
			this.pushUndo(this.raw);
		}
		this.lastTyped = now;
		this.redo.clear();
		this.raw = value;
	}

	private void pushUndo(String value) {
		if (!this.undo.isEmpty() && this.undo.peek().equals(value)) {
			return;
		}
		this.undo.push(value);
		while (this.undo.size() > UNDO_LIMIT) {
			this.undo.removeLast();
		}
	}

	/** Replaces the text as one undo step and selects the given raw range. */
	private void replace(String value, int selectFrom, int selectTo) {
		if (!value.equals(this.raw)) {
			this.pushUndo(this.raw);
			this.redo.clear();
			this.lastTyped = 0;
		}
		this.raw = value;
		this.settingValue = true;
		this.input.setValue(value);
		this.settingValue = false;
		this.input.moveCursorTo(Mth.clamp(selectFrom, 0, value.length()), false);
		this.input.moveCursorTo(Mth.clamp(selectTo, 0, value.length()), true);
		this.focusInput();
	}

	private void undo(boolean back) {
		Deque<String> from = back ? this.undo : this.redo;
		Deque<String> to = back ? this.redo : this.undo;
		if (from.isEmpty()) {
			return;
		}
		to.push(this.raw);
		this.raw = from.pop();
		this.settingValue = true;
		this.input.setValue(this.raw);
		this.settingValue = false;
		this.input.moveCursorToEnd(false);
		this.lastTyped = 0;
	}

	private void focusInput() {
		this.hexField.setFocused(false);
		this.nameField.setFocused(false);
		this.setFocused(this.input);
		this.input.setFocused(true);
	}

	private int[] selection() {
		int cursor = this.input.getCursorPosition();
		int other = ((EditBoxAccessor) this.input).hugosmpenhanced$getHighlightPos();
		return new int[] {Math.min(cursor, other), Math.max(cursor, other)};
	}

	/**
	 * Runs a change on the selected characters, or without a selection on everything from the cursor
	 * to the end (all of it with the cursor at the start or end). An empty text gets the codes
	 * inserted so typing continues in that look.
	 */
	private void apply(RangeEdit edit, String insertWhenEmpty) {
		ColorText text = ColorText.parse(this.raw);
		int[] sel = this.selection();
		int from = text.glyphIndexAt(sel[0]);
		int to = text.glyphIndexAt(sel[1]);
		if (from == to) {
			if (text.glyphs.isEmpty()) {
				if (insertWhenEmpty != null) {
					String value = this.raw + insertWhenEmpty;
					this.replace(value, value.length(), value.length());
				}
				return;
			}
			to = text.glyphs.size();
			if (from >= to) {
				from = 0;
			}
		}
		edit.run(text, from, to);
		String value = text.serialize(ColorCodes.hexStyle());
		boolean all = from == 0 && to == text.glyphs.size();
		if (all && sel[0] == sel[1]) {
			this.replace(value, value.length(), value.length());
		} else {
			ColorText.Glyph last = text.glyphs.get(to - 1);
			this.replace(value, text.glyphs.get(from).rawStart, last.rawStart + last.text.length());
		}
	}

	private interface RangeEdit {
		void run(ColorText text, int from, int to);
	}

	private void applyLegacy(ChatFormatting formatting) {
		ColorText.Color color = ColorText.Color.legacy(formatting);
		this.apply((text, from, to) -> text.setColor(from, to, color), "&" + Formats.code(formatting));
	}

	private void applyHex(int rgb) {
		ColorText.Color color = ColorText.Color.hex(rgb);
		ColorCodes.addRecent(rgb);
		this.apply((text, from, to) -> text.setColor(from, to, color), ColorText.colorCode(color, ColorCodes.hexStyle()));
	}

	/**
	 * A colour per letter where it fits into one command; otherwise neighbouring letters share a
	 * colour, as few as needed, so a long name still gets a gradient that can be sent.
	 */
	private void applyGradient() {
		if (ColorCodes.legacy()) {
			this.applyLegacyGradient();
			return;
		}
		int start = SLOTS[SLOT_FROM];
		int end = SLOTS[SLOT_TO];
		ColorCodes.addRecent(end);
		ColorCodes.addRecent(start);
		if (ColorText.parse(this.raw).glyphs.isEmpty()) {
			this.showFlash("Erst Text eingeben, dann Verlauf");
			return;
		}
		int room = ColorCodes.textLimit(this.command);
		int[] usedStep = {1};
		this.apply((text, from, to) -> {
			int letters = to - from;
			for (int step = 1; step <= Math.max(1, letters); step++) {
				text.steppedGradient(from, to, step, start, end);
				usedStep[0] = step;
				if (text.serialize(ColorCodes.hexStyle()).length() <= room) {
					return;
				}
			}
			// Even one colour per word is too long: fall back to a colour per letter.
			text.steppedGradient(from, to, 1, start, end);
			usedStep[0] = 1;
		}, null);
		if (usedStep[0] > 1) {
			this.showFlash("Je " + usedStep[0] + " Buchstaben eine Farbe, damit es passt");
		}
	}

	/**
	 * Gradient with the 16 chat colours: the colours between the two ends, each on a block of
	 * letters. Uses fewer in-between colours when the text would get too long for the limit.
	 */
	private void applyLegacyGradient() {
		if (ColorText.parse(this.raw).glyphs.isEmpty()) {
			this.showFlash("Erst Text eingeben, dann Verlauf");
			return;
		}
		ChatFormatting start = LEGACY_SLOTS[0];
		ChatFormatting end = LEGACY_SLOTS[1];
		int room = ColorCodes.textLimit(this.command);
		int[] used = {0};
		this.apply((text, from, to) -> {
			int most = ColorText.legacyPath(start, end).size();
			for (int colors = most; colors >= 2; colors--) {
				used[0] = text.legacyGradient(from, to, start, end, colors);
				if (text.serialize(HexStyle.LEGACY_ONLY).length() <= room) {
					return;
				}
			}
		}, null);
		this.showFlash(used[0] + " Farben im Verlauf");
	}

	private void toggleFormat(int bit) {
		this.apply((text, from, to) -> text.toggleFormat(from, to, bit), ColorText.formatCode(bit));
	}

	private void clearStyle() {
		this.apply(ColorText::clearStyle, "&r");
	}

	private void applyTemplateStyle(ColorTemplate template) {
		ColorText style = ColorText.parse(template.text);
		if (ColorText.parse(this.raw).glyphs.isEmpty()) {
			this.loadTemplate(template);
			return;
		}
		this.apply((text, from, to) -> text.applyStyleOf(style, from, to), null);
		this.showFlash("Stil von \"" + template.name + "\" übernommen");
	}

	private void loadTemplate(ColorTemplate template) {
		String value = ColorText.parse(template.text).serialize(ColorCodes.hexStyle());
		this.replace(value, value.length(), value.length());
		this.showFlash("\"" + template.name + "\" geladen");
	}

	private void showFlash(String message) {
		if (message != null) {
			this.flash = message;
			this.flashUntil = System.currentTimeMillis() + 2000;
		}
	}

	// ------------------------------------------------------------------ picker state

	private void loadSlot() {
		float[] hsv = ColorMath.toHsv(SLOTS[this.slot]);
		if (hsv[1] > 0 && hsv[2] > 0) {
			this.hue = hsv[0];
		}
		if (hsv[2] > 0) {
			this.sat = hsv[1];
		}
		this.val = hsv[2];
	}

	private void selectSlot(int slot) {
		this.slot = slot;
		lastSlot = slot;
		this.loadSlot();
		this.syncHexField();
	}

	private void setPicked(int rgb) {
		SLOTS[this.slot] = rgb;
		this.syncHexField();
	}

	private void syncHexField() {
		if (this.hexField != null) {
			this.hexField.setValue(String.format("#%06X", SLOTS[this.slot]));
		}
	}

	// ------------------------------------------------------------------ sending

	private String fullCommand() {
		return "/" + this.command + " " + this.raw;
	}

	private int remaining() {
		return ColorCodes.textLimit(this.command) - this.raw.length();
	}

	private boolean canSend() {
		return this.minecraft.player != null && !ColorText.parse(this.raw).plain().isBlank() && this.remaining() >= 0;
	}

	private void toChat() {
		if (this.minecraft.player == null) {
			return;
		}
		ModConfigManager.save();
		this.minecraft.setScreenAndShow(new ChatScreen(this.fullCommand(), false));
	}

	private void send() {
		if (!this.canSend()) {
			return;
		}
		String full = this.fullCommand();
		ModConfigManager.save();
		this.minecraft.gui.hud.getChat().addRecentChat(full);
		this.minecraft.setScreenAndShow(null);
		this.minecraft.player.connection.sendCommand(full.substring(1));
	}

	@Override
	public void onClose() {
		ModConfigManager.save();
		if (this.chatOrigin != null && this.minecraft.player != null) {
			this.minecraft.setScreenAndShow(new ChatScreen(this.chatOrigin, false));
		} else {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}

	// ------------------------------------------------------------------ templates

	private List<ColorTemplate> templates() {
		return ModConfigManager.CONFIG.colorTemplates;
	}

	private void startNaming() {
		if (ColorText.parse(this.raw).plain().isBlank()) {
			this.showFlash("Erst Text eingeben, dann speichern");
			return;
		}
		if (this.templates().size() >= ColorCodes.MAX_TEMPLATES) {
			this.showFlash("Höchstens " + ColorCodes.MAX_TEMPLATES + " Vorlagen");
			return;
		}
		this.naming = true;
		this.nameField.visible = true;
		String plain = ColorText.parse(this.raw).plain().strip();
		this.nameField.setValue(plain.length() > 32 ? plain.substring(0, 32) : plain);
		this.nameField.moveCursorToEnd(false);
		this.nameField.moveCursorToStart(true);
		this.input.setFocused(false);
		this.setFocused(this.nameField);
		this.nameField.setFocused(true);
	}

	private void finishNaming(boolean save) {
		if (save) {
			String name = this.nameField.getValue().strip();
			if (name.isEmpty()) {
				name = "Vorlage " + (this.templates().size() + 1);
			}
			this.templates().add(0, new ColorTemplate(name, this.raw));
			this.templateScroll = 0;
			ModConfigManager.save();
			this.showFlash("Vorlage \"" + name + "\" gespeichert");
		}
		this.naming = false;
		this.nameField.visible = false;
		this.focusInput();
	}

	/** The template's name in the template's own look. */
	private Component templateLabel(ColorTemplate template, int width) {
		String name = this.font.plainSubstrByWidth(template.name, width);
		ColorText label = ColorText.parse(name);
		ColorText look = ColorText.parse(template.text);
		if (look.hasStyle()) {
			label.applyStyleOf(look, 0, label.glyphs.size());
		}
		Component component = label.toComponent();
		// Bold letters are wider; shorten again if the look made the name too long.
		while (this.font.width(component) > width && name.length() > 1) {
			name = name.substring(0, name.length() - 1);
			label = ColorText.parse(name);
			if (look.hasStyle()) {
				label.applyStyleOf(look, 0, label.glyphs.size());
			}
			component = label.toComponent();
		}
		return component;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Theme.panel(graphics, this.fx, this.fy, this.fw, this.fh, Theme.FRAME, Theme.FRAME_BORDER);
		this.renderHeader(graphics, mouseX, mouseY);
		this.renderTemplates(graphics, mouseX, mouseY);
		this.renderPreview(graphics);
		this.renderInput(graphics, mouseX, mouseY);
		this.renderPalette(graphics, mouseX, mouseY);
		this.renderPicker(graphics, mouseX, mouseY);
		this.renderButtons(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(this.fx + 1, this.fy + 1, this.fx + this.listWidth, this.fy + this.fh - 1, Theme.SIDEBAR);
		graphics.fill(this.fx + this.listWidth, this.fy + 1, this.fx + this.listWidth + 1, this.fy + this.fh - 1, Theme.FRAME_BORDER);
		graphics.text(this.font, "Farb", this.fx + 8, this.fy + 9, 0xFFFF5F6D);
		graphics.text(this.font, "codes", this.fx + 8 + this.font.width("Farb"), this.fy + 9, Theme.GOLD);

		int tx = this.cx + 2;
		for (String cmd : ColorCodes.commands().subList(0, Math.min(4, ColorCodes.commands().size()))) {
			String label = "/" + cmd;
			int w = this.font.width(label);
			boolean selected = cmd.equals(this.command);
			boolean over = inside(mouseX, mouseY, tx - 5, this.fy + 6, w + 10, 16);
			if (selected) {
				Theme.panel(graphics, tx - 5, this.fy + 6, w + 10, 16, 0xFF282828, 0xFF5A5A5A);
				graphics.fill(tx - 3, this.fy + 19, tx + w + 3, this.fy + 20, Theme.GOLD);
			} else if (over) {
				Theme.panel(graphics, tx - 5, this.fy + 6, w + 10, 16, 0xFF1E1E1E, 0);
			}
			graphics.text(this.font, label, tx, this.fy + 10, selected ? Theme.WHITE : over ? Theme.TEXT : Theme.MUTED, selected);
			tx += w + 16;
		}

		int closeX = this.closeX();
		boolean overClose = inside(mouseX, mouseY, closeX, this.fy + 6, 17, 17);
		Theme.panel(graphics, closeX, this.fy + 6, 17, 17, overClose ? 0xFF3A3A3A : 0xFF1E1E1E, overClose ? 0xFFB4B4B4 : Theme.CONTROL_BORDER);
		Theme.glyph(graphics, Theme.CROSS, closeX + 6, this.fy + 12, Theme.TEXT);
		graphics.fill(this.cx - 4, this.fy + 24, this.fx + this.fw - 8, this.fy + 25, Theme.FRAME_BORDER);
	}

	private int closeX() {
		return this.fx + this.fw - 23;
	}

	private void renderTemplates(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = this.fx + 4;
		int width = this.listWidth - 8;
		graphics.text(this.font, "VORLAGEN", left + 4, this.fy + 28, 0xFFA0A0A0, false);
		List<ColorTemplate> templates = this.templates();
		int rows = this.listRows();
		this.templateScroll = Mth.clamp(this.templateScroll, 0, Math.max(0, templates.size() - rows));
		if (System.currentTimeMillis() > this.deleteUntil) {
			this.deleteArmed = -1;
		}
		if (templates.isEmpty()) {
			graphics.textWithWordWrap(this.font, Component.literal("Noch keine. Text eingeben und unten speichern."),
				left + 4, this.listTop() + 2, width - 6, Theme.FAINT, false);
		}
		for (int r = 0; r < rows && this.templateScroll + r < templates.size(); r++) {
			int index = this.templateScroll + r;
			ColorTemplate template = templates.get(index);
			int y = this.listTop() + r * ROW;
			boolean over = inside(mouseX, mouseY, left, y, width, ROW - 1) && !this.naming;
			if (over) {
				graphics.fill(left, y, left + width, y + ROW - 1, Theme.ROW_HOVER);
			}
			int textRoom = width - (over ? 30 : 8);
			graphics.text(this.font, this.templateLabel(template, textRoom), left + 4, y + 4, Theme.TEXT);
			if (over) {
				int brushX = left + width - 26;
				int crossX = left + width - 12;
				boolean overBrush = inside(mouseX, mouseY, brushX - 2, y, 12, ROW - 1);
				boolean overCross = inside(mouseX, mouseY, crossX - 3, y, 12, ROW - 1);
				boolean armed = this.deleteArmed == index;
				Theme.glyph(graphics, BRUSH, brushX, y + 5, overBrush ? Theme.GOLD : Theme.TEXT_DIM);
				Theme.glyph(graphics, Theme.CROSS, crossX, y + 5, armed || overCross ? Theme.RED_TEXT : Theme.TEXT_DIM);
				Component tip;
				if (overBrush) {
					tip = Component.literal("Nur den Stil übernehmen\n").append(Component.literal("Färbt deinen Text (oder die Markierung) wie diese Vorlage")
						.withStyle(ChatFormatting.GRAY));
				} else if (overCross) {
					tip = Component.literal(armed ? "Nochmal klicken zum Löschen" : "Löschen");
				} else {
					tip = Component.literal("Klick: Text laden\n").append(ColorText.parse(template.text).toComponent());
				}
				graphics.setTooltipForNextFrame(this.font, this.font.split(tip, 200), mouseX, mouseY);
			}
		}
		if (templates.size() > rows) {
			int barX = left + width + 1;
			int top = this.listTop();
			int height = rows * ROW;
			graphics.fill(barX, top, barX + 2, top + height, 0xFF2A2A2A);
			int barHeight = Math.max(10, height * rows / templates.size());
			int barY = top + (height - barHeight) * this.templateScroll / Math.max(1, templates.size() - rows);
			graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFF6E6E6E);
		}

		int y = this.saveY();
		if (this.naming) {
			Theme.panel(graphics, left + 2, y, width - 22, 16, Theme.FIELD, 0xFFC8C8C8);
			boolean overOk = inside(mouseX, mouseY, left + width - 18, y, 18, 16);
			Theme.panel(graphics, left + width - 18, y, 18, 16, overOk ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
			graphics.centeredText(this.font, "OK", left + width - 9, y + 4, Theme.WHITE);
		} else {
			boolean over = inside(mouseX, mouseY, left + 2, y, width - 4, 16);
			Theme.panel(graphics, left + 2, y, width - 4, 16, over ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
			Theme.glyph(graphics, Theme.PLUS, left + 8, y + 6, Theme.WHITE);
			graphics.text(this.font, "Speichern", left + 17, y + 4, Theme.WHITE);
			if (over) {
				graphics.setTooltipForNextFrame(this.font, Component.literal("Aktuellen Text als Vorlage speichern"), mouseX, mouseY);
			}
		}
	}

	private void renderPreview(GuiGraphicsExtractor graphics) {
		int x = this.cx;
		int y = this.previewY();
		int w = this.cw;
		int h = this.previewH();
		// Tooltip look, the way the result shows up on an item.
		graphics.fill(x + 1, y, x + w - 1, y + h, 0xF0100010);
		graphics.fill(x, y + 1, x + 1, y + h - 1, 0xF0100010);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, 0xF0100010);
		graphics.fillGradient(x + 1, y + 1, x + 2, y + h - 1, 0x505000FF, 0x5028007F);
		graphics.fillGradient(x + w - 2, y + 1, x + w - 1, y + h - 1, 0x505000FF, 0x5028007F);
		graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x505000FF);
		graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x5028007F);

		ItemStack stack = this.minecraft.player != null ? this.minecraft.player.getMainHandItem() : ItemStack.EMPTY;
		boolean rename = "rename".equals(this.command);
		if (stack.isEmpty()) {
			stack = new ItemStack(rename ? Items.NAME_TAG : Items.PAPER);
		}
		graphics.item(stack, x + 7, y + (h - 16) / 2);

		ColorText text = ColorText.parse(this.raw);
		Component result = text.glyphs.isEmpty()
			? Component.literal(rename ? "Vorschau des Namens" : "Vorschau").withStyle(ChatFormatting.DARK_GRAY)
			: text.toComponent();
		int textX = x + 30;
		int room = w - 36;
		if (rename) {
			this.drawFitted(graphics, result, textX, y + h / 2 - 4, room);
		} else {
			Component name = stack.getHoverName();
			graphics.text(this.font, this.font.plainSubstrByWidth(name.getString(), room), textX, y + h / 2 - 9, 0xFFFFFFFF);
			this.drawFitted(graphics, result, textX, y + h / 2 + 2, room);
		}
	}

	/** Draws a line, shrunk to fit if it is wider than the room. */
	private void drawFitted(GuiGraphicsExtractor graphics, Component text, int x, int y, int room) {
		int width = this.font.width(text);
		if (width <= room) {
			graphics.text(this.font, text, x, y, 0xFFFFFFFF);
			return;
		}
		float scale = Math.max(0.5f, room / (float) width);
		// The scissor takes the current transform into account, so it is set before scaling.
		graphics.enableScissor(x, y - 2, x + room, y + 10);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + 4 * (1 - scale));
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, 0, 0, 0xFFFFFFFF);
		graphics.pose().popMatrix();
		graphics.disableScissor();
	}

	private void renderInput(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = this.inputY();
		boolean focused = this.input.isFocused();
		Theme.panel(graphics, this.cx, y, this.cw, 18, Theme.FIELD, focused ? 0xFFC8C8C8 : Theme.FIELD_BORDER);

		int infoY = this.infoY();
		int remaining = this.remaining();
		int limit = ColorCodes.textLimit(this.command);
		String count = this.raw.length() + " / " + limit;
		int countColor = remaining < 0 ? Theme.RED_TEXT : remaining < Math.max(3, limit / 10) ? Theme.GOLD : Theme.MUTED;
		int countX = this.cx + this.cw - this.font.width(count) - 2;
		graphics.text(this.font, count, countX, infoY, countColor, false);
		if (inside(mouseX, mouseY, countX, infoY - 1, this.font.width(count), 10)) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(
				"Zeichen mit allen Codes. Jeder Code wie &6 zählt 2 Zeichen mit. Das Limit stellst du unter Chat > Farben ein."),
				200), mouseX, mouseY);
		}

		String hint;
		int hintColor = Theme.FAINT;
		int[] sel = this.selection();
		if (remaining < 0) {
			hint = (-remaining) + " Zeichen zu lang";
			hintColor = Theme.RED_TEXT;
		} else if (System.currentTimeMillis() < this.flashUntil && this.flash != null) {
			hint = this.flash;
			hintColor = Theme.GOLD;
		} else if (sel[0] != sel[1]) {
			int letters = ColorText.parse(this.raw.substring(sel[0], sel[1])).glyphs.size();
			hint = letters + " Zeichen markiert";
			hintColor = Theme.TEXT_DIM;
		} else {
			hint = "Markieren färbt nur einen Teil  ·  Strg+Z";
		}
		graphics.text(this.font, this.font.plainSubstrByWidth(hint, countX - this.cx - 6), this.cx + 2, infoY, hintColor, false);
	}

	private void renderPalette(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = this.paletteY();
		int size = this.swatch;
		for (int i = 0; i < PALETTE.length; i++) {
			int x = this.cx + i * size;
			int color = 0xFF000000 | Formats.color(PALETTE[i]);
			boolean over = inside(mouseX, mouseY, x, y, size, size);
			graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
			HugoOutline.draw(graphics, x, y, size, size, over ? Theme.WHITE : 0xFF3A3A3A);
			if (over) {
				graphics.setTooltipForNextFrame(this.font, List.of(
					Component.literal(PALETTE_NAMES[i] + "  ").append(Component.literal("&" + Formats.code(PALETTE[i])).withStyle(ChatFormatting.GRAY)).getVisualOrderText(),
					Component.literal(this.pickingEnd >= 0 ? "Klick: als Verlaufsfarbe setzen" : ColorCodes.legacy() ? "Rechtsklick: als Verlaufsanfang" : "Rechtsklick: in den Farbwähler").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()),
					mouseX, mouseY);
			}
		}

		ColorText text = ColorText.parse(this.raw);
		int[] sel = this.selection();
		int from = text.glyphIndexAt(sel[0]);
		int to = text.glyphIndexAt(sel[1]);
		int fx = this.formatX();
		for (int i = 0; i <= FORMAT_BITS.length; i++) {
			int x = fx + i * 13;
			boolean over = inside(mouseX, mouseY, x, y, 12, size);
			boolean active = i < FORMAT_BITS.length && text.allHave(from, to, FORMAT_BITS[i]);
			Theme.panel(graphics, x, y, 12, size, active ? Theme.GOLD_FILL : over ? 0xFF3A3A3A : Theme.CONTROL,
				active ? Theme.GOLD : over ? 0xFFB4B4B4 : Theme.CONTROL_BORDER);
			Component letter;
			Component tip;
			if (i < FORMAT_BITS.length) {
				letter = Component.literal(FORMAT_LETTERS[i]).setStyle(ColorText.style(null, FORMAT_BITS[i] == ColorText.OBFUSCATED ? 0 : FORMAT_BITS[i]));
				tip = Component.literal(FORMAT_NAMES[i] + "  ").append(Component.literal(ColorText.formatCode(FORMAT_BITS[i])).withStyle(ChatFormatting.GRAY));
			} else {
				letter = Component.literal("R");
				tip = Component.literal("Ohne Farbe und Format  ").append(Component.literal("&r").withStyle(ChatFormatting.GRAY));
			}
			graphics.centeredText(this.font, letter, x + 6, y + (size - 8) / 2, Theme.WHITE);
			if (over) {
				graphics.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
			}
		}
	}

	private int formatX() {
		return this.cx + this.cw - (FORMAT_BITS.length + 1) * 13 + 1;
	}

	private void renderPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (ColorCodes.legacy()) {
			this.renderLegacyGradient(graphics, mouseX, mouseY);
			return;
		}
		int px = this.cx;
		int py = this.pickerY();
		int pw = this.pickerW;
		int ph = this.pickerH;
		HugoOutline.draw(graphics, px - 1, py - 1, pw + 2, ph + 2, Theme.FIELD_BORDER);
		for (int i = 0; i < pw; i++) {
			int top = 0xFF000000 | ColorMath.hsv(this.hue, i / (float) (pw - 1), 1);
			graphics.fillGradient(px + i, py, px + i + 1, py + ph, top, 0xFF000000);
		}
		int mx = px + Math.round(this.sat * (pw - 1));
		int my = py + Math.round((1 - this.val) * (ph - 1));
		int ring = this.val > 0.6 && this.sat < 0.5 ? 0xFF000000 : 0xFFFFFFFF;
		HugoOutline.draw(graphics, mx - 2, my - 2, 5, 5, ring);

		int hy = this.hueY();
		HugoOutline.draw(graphics, px - 1, hy - 1, pw + 2, 8, Theme.FIELD_BORDER);
		for (int i = 0; i < pw; i++) {
			graphics.fill(px + i, hy, px + i + 1, hy + 6, 0xFF000000 | ColorMath.hsv(i / (float) pw, 1, 1));
		}
		int hx = px + Math.round(this.hue * (pw - 1));
		graphics.fill(hx - 1, hy - 2, hx + 2, hy + 8, 0xFFFFFFFF);
		graphics.fill(hx, hy - 1, hx + 1, hy + 7, 0xFF000000);

		boolean hexFocused = this.hexField.isFocused();
		boolean hexValid = ColorCodes.parseHex(this.hexField.getValue()) != null;
		Theme.panel(graphics, px, this.hexY(), pw, 16, Theme.FIELD,
			!hexValid ? Theme.RED : hexFocused ? 0xFFC8C8C8 : Theme.FIELD_BORDER);

		// Colour row and gradient row, each with its action button on the right.
		int x2 = px + pw + 10;
		int buttonW = Math.max(this.font.width("Verlauf"), this.font.width("Färben")) + 16;
		int buttonX = this.cx + this.cw - buttonW;
		int rowA = py;
		int rowB = py + 22;

		this.slotSwatch(graphics, SLOT_COLOR, x2, rowA, mouseX, mouseY, "Farbe");
		graphics.text(this.font, "Farbe", x2 + 22, rowA + 4, Theme.TEXT_DIM, false);
		graphics.text(this.font, String.format("#%06X", SLOTS[SLOT_COLOR]), x2 + 26 + this.font.width("Farbe"), rowA + 4, Theme.FAINT, false);
		this.actionButton(graphics, buttonX, rowA, buttonW, "Färben", mouseX, mouseY,
			"Färbt die Markierung oder den Text mit dieser Farbe");

		int fromX = x2;
		int toX = buttonX - 8 - 16 - 14;
		this.slotSwatch(graphics, SLOT_FROM, fromX, rowB, mouseX, mouseY, "Verlauf von");
		this.slotSwatch(graphics, SLOT_TO, toX, rowB, mouseX, mouseY, "Verlauf bis");
		int barLeft = fromX + 19;
		int barRight = toX - 3;
		for (int i = barLeft; i < barRight; i++) {
			float t = (i - barLeft) / (float) Math.max(1, barRight - barLeft - 1);
			graphics.fill(i, rowB + 5, i + 1, rowB + 11, 0xFF000000 | ColorMath.mix(SLOTS[SLOT_FROM], SLOTS[SLOT_TO], t));
		}
		int swapX = toX + 19;
		boolean overSwap = inside(mouseX, mouseY, swapX - 1, rowB + 2, 10, 12);
		Theme.glyph(graphics, SWAP, swapX, rowB + 5, overSwap ? Theme.GOLD : Theme.TEXT_DIM);
		if (overSwap) {
			graphics.setTooltipForNextFrame(this.font, Component.literal("Richtung tauschen"), mouseX, mouseY);
		}
		this.actionButton(graphics, buttonX, rowB, buttonW, "Verlauf", mouseX, mouseY,
			"Lässt die Markierung oder den Text von der einen in die andere Farbe übergehen");

		// Recent colours.
		int rowC = py + 44;
		List<Integer> recent = ColorCodes.recent();
		if (!recent.isEmpty() && rowC + 10 <= this.hexY() + 16) {
			graphics.text(this.font, "Zuletzt", x2, rowC + 1, Theme.FAINT, false);
			int rx = x2 + this.font.width("Zuletzt") + 6;
			for (int rgb : recent) {
				if (rx + 10 > this.cx + this.cw) {
					break;
				}
				boolean over = inside(mouseX, mouseY, rx, rowC, 10, 10);
				graphics.fill(rx + 1, rowC + 1, rx + 9, rowC + 9, 0xFF000000 | rgb);
				HugoOutline.draw(graphics, rx, rowC, 10, 10, over ? Theme.WHITE : 0xFF3A3A3A);
				if (over) {
					graphics.setTooltipForNextFrame(this.font, Component.literal(String.format("#%06X", rgb)), mouseX, mouseY);
				}
				rx += 12;
			}
		}
	}

	private int legacyEndX(int end) {
		int buttonW = this.font.width("Verlauf") + 16;
		return end == 0 ? this.cx + this.font.width("Verlauf") + 8 : this.cx + this.cw - buttonW - 8 - 16 - 14;
	}

	/** The gradient row with only chat colours: two ends picked from the palette, a preview, the button. */
	private void renderLegacyGradient(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = this.pickerY();
		graphics.text(this.font, "Verlauf", this.cx, y + 4, Theme.TEXT_DIM, false);
		int fromX = this.legacyEndX(0);
		int toX = this.legacyEndX(1);
		for (int end = 0; end < 2; end++) {
			int x = end == 0 ? fromX : toX;
			boolean picking = this.pickingEnd == end;
			boolean over = inside(mouseX, mouseY, x, y, 16, 16);
			graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFF000000 | Formats.color(LEGACY_SLOTS[end]));
			HugoOutline.draw(graphics, x, y, 16, 16, picking ? Theme.GOLD : over ? Theme.WHITE : 0xFF4A4A4A);
			if (picking) {
				HugoOutline.draw(graphics, x + 1, y + 1, 14, 14, 0xFF000000);
			}
			if (over) {
				int index = paletteIndex(LEGACY_SLOTS[end]);
				graphics.setTooltipForNextFrame(this.font, List.of(
					Component.literal((end == 0 ? "Von  " : "Bis  ") + PALETTE_NAMES[index] + "  ")
						.append(Component.literal("&" + Formats.code(LEGACY_SLOTS[end])).withStyle(ChatFormatting.GRAY)).getVisualOrderText(),
					Component.literal("Klick, dann eine Farbe oben wählen. Mausrad wechselt.").withStyle(ChatFormatting.DARK_GRAY)
						.getVisualOrderText()), mouseX, mouseY);
			}
		}
		// The colours the gradient will use, each as one block.
		List<ChatFormatting> path = ColorText.legacyPath(LEGACY_SLOTS[0], LEGACY_SLOTS[1]);
		int barLeft = fromX + 20;
		int barRight = toX - 4;
		int span = barRight - barLeft;
		for (int i = 0; i < path.size(); i++) {
			int left = barLeft + span * i / path.size();
			int right = barLeft + span * (i + 1) / path.size();
			graphics.fill(left, y + 5, right, y + 11, 0xFF000000 | Formats.color(path.get(i)));
		}
		int swapX = toX + 19;
		boolean overSwap = inside(mouseX, mouseY, swapX - 1, y + 2, 10, 12);
		Theme.glyph(graphics, SWAP, swapX, y + 5, overSwap ? Theme.GOLD : Theme.TEXT_DIM);
		if (overSwap) {
			graphics.setTooltipForNextFrame(this.font, Component.literal("Richtung tauschen"), mouseX, mouseY);
		}
		int[] button = this.bounds("gradient");
		this.actionButton(graphics, button[0], button[1], button[2], "Verlauf", mouseX, mouseY,
			"Färbt die Markierung oder den Text von der einen zur anderen Farbe, mit den Chat-Farben dazwischen");
		String hint = this.pickingEnd >= 0 ? "Jetzt oben die Farbe für " + (this.pickingEnd == 0 ? "\"Von\"" : "\"Bis\"") + " anklicken"
			: "Nur Chat-Farben (&0 bis &f), jeder Code kostet 2 Zeichen";
		graphics.text(this.font, this.font.plainSubstrByWidth(hint, this.cw), this.cx, y + 24,
			this.pickingEnd >= 0 ? Theme.GOLD : Theme.FAINT, false);
	}

	private static int paletteIndex(ChatFormatting formatting) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == formatting) {
				return i;
			}
		}
		return 0;
	}

	private boolean clickLegacyGradient(double mx, double my) {
		int y = this.pickerY();
		for (int end = 0; end < 2; end++) {
			if (inside(mx, my, this.legacyEndX(end), y, 16, 16)) {
				this.pickingEnd = this.pickingEnd == end ? -1 : end;
				return true;
			}
		}
		if (inside(mx, my, this.legacyEndX(1) + 18, y + 2, 10, 12)) {
			ChatFormatting from = LEGACY_SLOTS[0];
			LEGACY_SLOTS[0] = LEGACY_SLOTS[1];
			LEGACY_SLOTS[1] = from;
			return true;
		}
		int[] button = this.bounds("gradient");
		if (inside(mx, my, button[0], button[1], button[2], button[3])) {
			this.pickingEnd = -1;
			this.applyGradient();
			return true;
		}
		return false;
	}

	private void slotSwatch(GuiGraphicsExtractor graphics, int slot, int x, int y, int mouseX, int mouseY, String name) {
		boolean selected = this.slot == slot;
		boolean over = inside(mouseX, mouseY, x, y, 16, 16);
		graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFF000000 | SLOTS[slot]);
		HugoOutline.draw(graphics, x, y, 16, 16, selected ? Theme.GOLD : over ? Theme.WHITE : 0xFF4A4A4A);
		if (selected) {
			HugoOutline.draw(graphics, x + 1, y + 1, 14, 14, 0xFF000000);
		}
		if (over) {
			graphics.setTooltipForNextFrame(this.font, List.of(
				Component.literal(name + "  ").append(Component.literal(String.format("#%06X", SLOTS[slot])).withStyle(ChatFormatting.GRAY)).getVisualOrderText(),
				Component.literal(selected ? "Wird gerade im Farbwähler bearbeitet" : "Klick: im Farbwähler bearbeiten")
					.withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()), mouseX, mouseY);
		}
	}

	private void actionButton(GuiGraphicsExtractor graphics, int x, int y, int w, String label, int mouseX, int mouseY, String tip) {
		boolean over = inside(mouseX, mouseY, x, y, w, 16);
		Theme.panel(graphics, x, y, w, 16, over ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
		graphics.centeredText(this.font, label, x + w / 2, y + 4, Theme.WHITE);
		if (over) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(tip), 180), mouseX, mouseY);
		}
	}

	private void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = this.bottomY();
		int[] send = this.sendBox();
		int[] chat = this.chatBox();
		int[] cancel = this.cancelBox();
		boolean ingame = this.minecraft.player != null;
		boolean sendable = this.canSend();

		boolean overCancel = inside(mouseX, mouseY, cancel[0], y, cancel[2], 16);
		Theme.panel(graphics, cancel[0], y, cancel[2], 16, overCancel ? 0xFF3A3A3A : Theme.CONTROL, overCancel ? 0xFFB4B4B4 : Theme.CONTROL_BORDER);
		graphics.centeredText(this.font, "Abbrechen", cancel[0] + cancel[2] / 2, y + 4, Theme.TEXT);

		boolean overChat = ingame && inside(mouseX, mouseY, chat[0], y, chat[2], 16);
		Theme.panel(graphics, chat[0], y, chat[2], 16, overChat ? 0xFF3A3A3A : Theme.CONTROL,
			!ingame ? 0xFF333333 : overChat ? 0xFFB4B4B4 : Theme.CONTROL_BORDER);
		graphics.centeredText(this.font, "In den Chat", chat[0] + chat[2] / 2, y + 4, ingame ? Theme.TEXT : Theme.FAINT);
		if (inside(mouseX, mouseY, chat[0], y, chat[2], 16)) {
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(ingame
				? "Schreibt den Befehl in den Chat, ohne ihn abzuschicken (Enter)" : "Nur in einer Welt oder auf einem Server"), 180), mouseX, mouseY);
		}

		boolean overSend = sendable && inside(mouseX, mouseY, send[0], y, send[2], 16);
		if (sendable) {
			Theme.panel(graphics, send[0], y, send[2], 16, overSend ? 0x78FFC83D : Theme.GOLD_FILL, Theme.GOLD);
		} else {
			Theme.panel(graphics, send[0], y, send[2], 16, 0xFF1A1A1A, 0xFF333333);
		}
		String label = "/" + this.command + " senden";
		graphics.centeredText(this.font, this.font.plainSubstrByWidth(label, send[2] - 6), send[0] + send[2] / 2, y + 4,
			sendable ? Theme.WHITE : Theme.FAINT);
		if (inside(mouseX, mouseY, send[0], y, send[2], 16)) {
			String tip = !ingame ? "Nur in einer Welt oder auf einem Server"
				: this.remaining() < 0 ? "Zu lang: höchstens " + ColorCodes.textLimit(this.command) + " Zeichen mit Codes. Kürzen oder weniger Farben nutzen"
				: ColorText.parse(this.raw).plain().isBlank() ? "Erst Text eingeben"
				: "Schickt " + this.fullCommand();
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.literal(tip), 220), mouseX, mouseY);
		}
	}

	private int[] sendBox() {
		int w = Math.min(110, Math.max(80, this.font.width("/" + this.command + " senden") + 16));
		return new int[] {this.cx + this.cw - w, this.bottomY(), w};
	}

	private int[] chatBox() {
		int w = this.font.width("In den Chat") + 16;
		return new int[] {this.sendBox()[0] - 4 - w, this.bottomY(), w};
	}

	private int[] cancelBox() {
		return new int[] {this.cx, this.bottomY(), this.font.width("Abbrechen") + 16};
	}

	/** Colours the codes in the input in their own colour, so the raw text stays readable. */
	private FormattedCharSequence highlightCodes(String visible, int offset) {
		String value = this.input.getValue();
		MutableComponent out = Component.empty();
		int i = 0;
		while (i < value.length() && i < offset + visible.length()) {
			int code = ColorText.codeLength(value, i);
			int len = code > 0 ? code : 1;
			int end = Math.min(i + len, offset + visible.length());
			if (end > offset) {
				String part = value.substring(Math.max(i, offset), end);
				out.append(Component.literal(part).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(this.codeColor(value, i, code)))));
			}
			i += len;
		}
		return out.getVisualOrderText();
	}

	private int codeColor(String value, int i, int code) {
		if (code == 0) {
			return 0xE8E8E8;
		}
		ColorText parsed = ColorText.parse(value.substring(i, i + code) + "x");
		ColorText.Color color = parsed.glyphs.get(0).color;
		if (color == null) {
			return 0xC8A85A;
		}
		// Too dark to read on the black field: lift it towards white.
		return ColorMath.distance(color.rgb(), 0) < 0.12 ? ColorMath.mix(color.rgb(), 0xFFFFFF, 0.45f) : color.rgb();
	}

	/** Where a control is, as {x, y, w, h}; for the game test, which clicks like a player. */
	public int[] bounds(String control) {
		int buttonW = Math.max(this.font.width("Verlauf"), this.font.width("Färben")) + 16;
		int buttonX = this.cx + this.cw - buttonW;
		int left = this.fx + 4;
		int width = this.listWidth - 8;
		String[] parts = control.split(":");
		int index = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
		int rowY = this.listTop() + (index - this.templateScroll) * ROW;
		return switch (parts[0]) {
			case "color" -> new int[] {buttonX, this.pickerY(), buttonW, 16};
			case "gradient" -> ColorCodes.legacy()
				? new int[] {this.cx + this.cw - (this.font.width("Verlauf") + 16), this.pickerY(), this.font.width("Verlauf") + 16, 16}
				: new int[] {buttonX, this.pickerY() + 22, buttonW, 16};
			case "end" -> new int[] {this.legacyEndX(index), this.pickerY(), 16, 16};
			case "send" -> withHeight(this.sendBox());
			case "chat" -> withHeight(this.chatBox());
			case "cancel" -> withHeight(this.cancelBox());
			case "save" -> new int[] {left + 2, this.saveY(), width - 4, 16};
			case "input" -> new int[] {this.cx, this.inputY(), this.cw, 18};
			case "swatch" -> new int[] {this.cx + index * this.swatch, this.paletteY(), this.swatch, this.swatch};
			case "format" -> new int[] {this.formatX() + index * 13, this.paletteY(), 12, this.swatch};
			case "template" -> new int[] {left, rowY, width - 30, ROW - 1};
			case "brush" -> new int[] {left + width - 28, rowY, 12, ROW - 1};
			case "picker" -> new int[] {this.cx, this.pickerY(), this.pickerW, this.pickerH};
			default -> throw new IllegalArgumentException(control);
		};
	}

	private static int[] withHeight(int[] box) {
		return new int[] {box[0], box[1], box[2], 16};
	}

	public String text() {
		return this.raw;
	}

	// ------------------------------------------------------------------ input

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		if (inside(mx, my, this.closeX(), this.fy + 6, 17, 17)) {
			this.onClose();
			return true;
		}
		int tx = this.cx + 2;
		for (String cmd : ColorCodes.commands().subList(0, Math.min(4, ColorCodes.commands().size()))) {
			int w = this.font.width("/" + cmd);
			if (inside(mx, my, tx - 5, this.fy + 6, w + 10, 16)) {
				this.command = cmd;
				return true;
			}
			tx += w + 16;
		}

		if (this.clickTemplates(mx, my, button)) {
			return true;
		}
		if (this.naming && !inside(mx, my, this.fx, this.saveY(), this.listWidth, 16)) {
			this.finishNaming(false);
		}

		// palette
		int py = this.paletteY();
		for (int i = 0; i < PALETTE.length; i++) {
			if (inside(mx, my, this.cx + i * this.swatch, py, this.swatch, this.swatch)) {
				if (this.pickingEnd >= 0) {
					LEGACY_SLOTS[this.pickingEnd] = PALETTE[i];
					this.pickingEnd = -1;
				} else if (button == InputConstants.MOUSE_BUTTON_RIGHT && ColorCodes.legacy()) {
					LEGACY_SLOTS[0] = PALETTE[i];
				} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
					this.setPicked(Formats.color(PALETTE[i]) & 0xFFFFFF);
					this.loadSlot();
				} else {
					this.applyLegacy(PALETTE[i]);
				}
				return true;
			}
		}
		for (int i = 0; i <= FORMAT_BITS.length; i++) {
			if (inside(mx, my, this.formatX() + i * 13, py, 12, this.swatch)) {
				if (i < FORMAT_BITS.length) {
					this.toggleFormat(FORMAT_BITS[i]);
				} else {
					this.clearStyle();
				}
				return true;
			}
		}

		// picker
		if (ColorCodes.legacy()) {
			if (this.clickLegacyGradient(mx, my)) {
				return true;
			}
			this.pickingEnd = -1;
		} else if (inside(mx, my, this.cx, this.pickerY(), this.pickerW, this.pickerH)) {
			this.dragging = 1;
			this.dragPicker(mx, my);
			return true;
		}
		if (!ColorCodes.legacy()) {
			if (inside(mx, my, this.cx, this.hueY() - 2, this.pickerW, 10)) {
				this.dragging = 2;
				this.dragPicker(mx, my);
				return true;
			}
			if (this.clickPickerSide(mx, my)) {
				return true;
			}
		}

		// bottom buttons
		int y = this.bottomY();
		int[] cancel = this.cancelBox();
		if (inside(mx, my, cancel[0], y, cancel[2], 16)) {
			this.onClose();
			return true;
		}
		int[] chat = this.chatBox();
		if (inside(mx, my, chat[0], y, chat[2], 16)) {
			this.toChat();
			return true;
		}
		int[] send = this.sendBox();
		if (inside(mx, my, send[0], y, send[2], 16)) {
			this.send();
			return true;
		}

		if (inside(mx, my, this.cx, this.inputY(), this.cw, 18)) {
			this.hexField.setFocused(false);
			this.setFocused(this.input);
			this.input.setFocused(true);
			this.input.mouseClicked(event, doubleClick);
			return true;
		}
		if (!ColorCodes.legacy() && inside(mx, my, this.cx, this.hexY(), this.pickerW, 16)) {
			this.input.setFocused(false);
			this.setFocused(this.hexField);
			this.hexField.setFocused(true);
			this.hexField.mouseClicked(event, doubleClick);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickPickerSide(double mx, double my) {
		int x2 = this.cx + this.pickerW + 10;
		int py = this.pickerY();
		int buttonW = Math.max(this.font.width("Verlauf"), this.font.width("Färben")) + 16;
		int buttonX = this.cx + this.cw - buttonW;
		int rowB = py + 22;
		int toX = buttonX - 8 - 16 - 14;
		if (inside(mx, my, x2, py, 16, 16)) {
			this.selectSlot(SLOT_COLOR);
		} else if (inside(mx, my, x2, rowB, 16, 16)) {
			this.selectSlot(SLOT_FROM);
		} else if (inside(mx, my, toX, rowB, 16, 16)) {
			this.selectSlot(SLOT_TO);
		} else if (inside(mx, my, toX + 18, rowB + 2, 10, 12)) {
			int from = SLOTS[SLOT_FROM];
			SLOTS[SLOT_FROM] = SLOTS[SLOT_TO];
			SLOTS[SLOT_TO] = from;
			this.loadSlot();
			this.syncHexField();
		} else if (inside(mx, my, buttonX, py, buttonW, 16)) {
			this.applyHex(SLOTS[SLOT_COLOR]);
		} else if (inside(mx, my, buttonX, rowB, buttonW, 16)) {
			this.applyGradient();
		} else {
			int rowC = py + 44;
			int rx = x2 + this.font.width("Zuletzt") + 6;
			for (int rgb : ColorCodes.recent()) {
				if (inside(mx, my, rx, rowC, 10, 10)) {
					this.setPicked(rgb);
					this.loadSlot();
					return true;
				}
				rx += 12;
			}
			return false;
		}
		return true;
	}

	private boolean clickTemplates(double mx, double my, int button) {
		int left = this.fx + 4;
		int width = this.listWidth - 8;
		int y = this.saveY();
		if (this.naming) {
			if (inside(mx, my, left + width - 18, y, 18, 16)) {
				this.finishNaming(true);
				return true;
			}
			if (inside(mx, my, left + 2, y, width - 22, 16)) {
				this.setFocused(this.nameField);
				this.nameField.setFocused(true);
				return true;
			}
		} else if (inside(mx, my, left + 2, y, width - 4, 16)) {
			this.startNaming();
			return true;
		}
		List<ColorTemplate> templates = this.templates();
		int rows = this.listRows();
		for (int r = 0; r < rows && this.templateScroll + r < templates.size(); r++) {
			int index = this.templateScroll + r;
			int rowY = this.listTop() + r * ROW;
			if (!inside(mx, my, left, rowY, width, ROW - 1) || this.naming) {
				continue;
			}
			ColorTemplate template = templates.get(index);
			if (inside(mx, my, left + width - 15, rowY, 12, ROW - 1)) {
				if (this.deleteArmed == index) {
					templates.remove(index);
					this.deleteArmed = -1;
					ModConfigManager.save();
					this.showFlash("Vorlage gelöscht");
				} else {
					this.deleteArmed = index;
					this.deleteUntil = System.currentTimeMillis() + 3000;
				}
			} else if (inside(mx, my, left + width - 28, rowY, 12, ROW - 1) || button == InputConstants.MOUSE_BUTTON_RIGHT) {
				this.applyTemplateStyle(template);
			} else {
				this.loadTemplate(template);
			}
			return true;
		}
		return false;
	}

	private void dragPicker(double mx, double my) {
		if (this.dragging == 1) {
			this.sat = Mth.clamp((float) (mx - this.cx) / (this.pickerW - 1), 0, 1);
			this.val = 1 - Mth.clamp((float) (my - this.pickerY()) / (this.pickerH - 1), 0, 1);
		} else if (this.dragging == 2) {
			this.hue = Mth.clamp((float) (mx - this.cx) / (this.pickerW - 1), 0, 0.999f);
		}
		this.setPicked(ColorMath.hsv(this.hue, this.sat, this.val));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.dragging != 0) {
			this.dragPicker(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = 0;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < this.fx + this.listWidth) {
			this.templateScroll -= (int) Math.signum(scrollY);
			return true;
		}
		if (ColorCodes.legacy()) {
			for (int end = 0; end < 2; end++) {
				if (inside(mouseX, mouseY, this.legacyEndX(end), this.pickerY(), 16, 16)) {
					int next = Math.floorMod(paletteIndex(LEGACY_SLOTS[end]) - (int) Math.signum(scrollY), PALETTE.length);
					LEGACY_SLOTS[end] = PALETTE[next];
					return true;
				}
			}
		} else if (inside(mouseX, mouseY, this.cx, this.hueY() - 2, this.pickerW, 10)) {
			this.hue = (this.hue - (float) scrollY * 0.01f + 1) % 1f;
			this.setPicked(ColorMath.hsv(this.hue, this.sat, this.val));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean control = event.hasControlDownWithQuirk();
		boolean shift = event.hasShiftDown();
		if (this.naming && this.nameField.isFocused()) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				this.finishNaming(true);
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				this.finishNaming(false);
				return true;
			}
			return super.keyPressed(event);
		}
		if (this.hexField.isFocused() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
			Integer rgb = ColorCodes.parseHex(this.hexField.getValue());
			if (rgb != null) {
				SLOTS[this.slot] = rgb;
				this.loadSlot();
				this.syncHexField();
			}
			this.focusInput();
			return true;
		}
		if (control && key == InputConstants.KEY_Z) {
			this.undo(!shift);
			return true;
		}
		if (control && key == InputConstants.KEY_Y) {
			this.undo(false);
			return true;
		}
		if (this.input.isFocused() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
			this.toChat();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		ModConfigManager.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Thin one-pixel outline without cut corners, for swatches and the picker. */
	private static final class HugoOutline {
		static void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
			graphics.fill(x, y, x + w, y + 1, color);
			graphics.fill(x, y + h - 1, x + w, y + h, color);
			graphics.fill(x, y + 1, x + 1, y + h - 1, color);
			graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
		}
	}
}
