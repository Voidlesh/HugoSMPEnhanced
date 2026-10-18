package com.hugosmpenhanced.client.feature.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.feature.keyboard.KeyboardLayout.KeyDef;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Shows every key binding on a drawn keyboard: bound keys are tinted, keys with more than one
 * binding are red. Clicking a key selects it; the panel below lists what is on it (with a button to
 * remove each) and a searchable list of all actions, where a click puts that action on the selected
 * key. Keys of actions matching the search are outlined on the keyboard.
 */
public class KeyboardScreen extends Screen implements StyledScreen {
	private static final int MAX_UNIT = 22;
	private static final int TOP = 18;
	private static final int PANEL_GAP = 6;
	private static final int MIN_PANEL = 64;
	private static final int ROW = 20;

	private static final int BOUND_FILL = 0xC0285A32;
	private static final int BOUND_BORDER = 0xFF4CAF50;
	private static final int CONFLICT_FILL = 0xC0642323;
	private static final int CONFLICT_BORDER = 0xFFE05050;
	private static final int SELECTED_BORDER = 0xFFFFFFFF;
	private static final int MATCH_BORDER = 0xFFFFD54F;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF888888;

	private static String query = "";

	private final Screen parent;
	private final List<KeyMapping> mappings;
	private InputConstants.Key selected;
	private final List<Button> rows = new ArrayList<>();
	private List<KeyMapping> filtered = List.of();
	private int scroll;

	private int unit;
	private int originX;
	private int panelTop;
	private int listX;
	private int listWidth;
	private EditBox search;

	public KeyboardScreen(Screen parent) {
		super(Component.literal("Tastatur-Ansicht"));
		this.parent = parent;
		KeyMapping[] all = this.minecraftOptions().keyMappings.clone();
		Arrays.sort(all);
		this.mappings = List.of(all);
	}

	private net.minecraft.client.Options minecraftOptions() {
		return net.minecraft.client.Minecraft.getInstance().options;
	}

	private static InputConstants.Key boundKey(KeyMapping mapping) {
		return KeyBindingHelper.getBoundKeyOf(mapping);
	}

	/**
	 * Debug bindings other than the F3 key itself only fire together with F3, so they never clash
	 * with a normal binding on the same key; they are listed as combinations instead.
	 */
	private static boolean isCombination(KeyMapping mapping) {
		return mapping.getCategory() == KeyMapping.Category.DEBUG && !"key.debug.overlay".equals(mapping.getName());
	}

	/** Spectator bindings only work in spectator mode, so sharing a key with one is no clash. */
	private static boolean isConflict(List<KeyMapping> on) {
		return on.stream().filter(m -> m.getCategory() != KeyMapping.Category.SPECTATOR).count() > 1;
	}

	/** The bindings on a key, without F3 combinations. */
	private List<KeyMapping> on(InputConstants.Key key) {
		return this.bound(key, false);
	}

	private List<KeyMapping> bound(InputConstants.Key key, boolean combinations) {
		List<KeyMapping> result = new ArrayList<>();
		for (KeyMapping mapping : this.mappings) {
			if (!mapping.isUnbound() && isCombination(mapping) == combinations && boundKey(mapping).equals(key)) {
				result.add(mapping);
			}
		}
		return result;
	}

	@Override
	protected void init() {
		this.rows.clear();
		int byWidth = (int) ((this.width - 20) / KeyboardLayout.WIDTH);
		int byHeight = (int) ((this.height - TOP - PANEL_GAP - MIN_PANEL - 6) / KeyboardLayout.HEIGHT);
		this.unit = Math.max(10, Math.min(MAX_UNIT, Math.min(byWidth, byHeight)));
		int keyboardWidth = (int) Math.round(KeyboardLayout.WIDTH * this.unit);
		this.originX = (this.width - keyboardWidth) / 2;
		this.panelTop = TOP + (int) Math.round(KeyboardLayout.HEIGHT * this.unit) + PANEL_GAP;

		int infoWidth = Math.min(160, keyboardWidth * 2 / 5);
		this.listX = this.originX + infoWidth + 8;
		this.listWidth = keyboardWidth - infoWidth - 8;
		int bottom = this.height - 6;

		// Left column: what is on the selected key, then Fertig at the bottom.
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
			.bounds(this.originX, bottom - ROW, infoWidth, ROW).build());
		if (this.selected != null) {
			int y = this.panelTop + 12;
			for (KeyMapping mapping : this.on(this.selected)) {
				if (y + ROW > bottom - ROW - 4) {
					break;
				}
				Button remove = Button.builder(Component.literal("✕"), b -> this.assign(mapping, InputConstants.UNKNOWN))
					.bounds(this.originX + infoWidth - ROW, y, ROW, ROW).build();
				remove.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Von dieser Taste entfernen")));
				this.addRenderableWidget(remove);
				y += ROW + 2;
			}
		}

		// Right column: search and the list of actions.
		this.search = new EditBox(this.font, this.listX, this.panelTop, this.listWidth, ROW, Component.literal("Suche"));
		this.search.setHint(Component.literal("Aktion suchen …"));
		this.search.setValue(query);
		this.search.setResponder(value -> {
			query = value;
			this.scroll = 0;
			this.refreshRows();
		});
		this.addRenderableWidget(this.search);

		int visible = Math.max(1, (bottom - (this.panelTop + ROW + 2)) / ROW);
		for (int i = 0; i < visible; i++) {
			final int index = i;
			Button row = Button.builder(Component.empty(), b -> {
					int at = this.scroll + index;
					if (this.selected != null && at < this.filtered.size()) {
						this.assign(this.filtered.get(at), this.selected);
					}
				})
				.bounds(this.listX, this.panelTop + ROW + 2 + i * ROW, this.listWidth, ROW).build();
			this.rows.add(this.addRenderableWidget(row));
		}
		this.refreshRows();
	}

	private void refreshRows() {
		String needle = query.strip().toLowerCase(Locale.ROOT);
		// Actions whose name matches come first, then those whose category matches.
		List<KeyMapping> result = new ArrayList<>();
		List<KeyMapping> byCategory = new ArrayList<>();
		for (KeyMapping mapping : this.mappings) {
			if (needle.isEmpty() || name(mapping).toLowerCase(Locale.ROOT).contains(needle)) {
				result.add(mapping);
			} else if (mapping.getCategory().label().getString().toLowerCase(Locale.ROOT).contains(needle)) {
				byCategory.add(mapping);
			}
		}
		result.addAll(byCategory);
		this.filtered = result;
		this.scroll = Math.max(0, Math.min(this.scroll, result.size() - this.rows.size()));

		for (int i = 0; i < this.rows.size(); i++) {
			Button row = this.rows.get(i);
			int at = this.scroll + i;
			row.visible = at < result.size();
			if (!row.visible) {
				continue;
			}
			KeyMapping mapping = result.get(at);
			String key = mapping.isUnbound() ? "—" : mapping.getTranslatedKeyMessage().getString();
			String label = name(mapping);
			int room = this.listWidth - 12 - this.font.width(" [" + key + "]");
			label = this.font.plainSubstrByWidth(label, Math.max(10, room));
			row.setMessage(Component.literal(label).append(Component.literal(" [" + key + "]").withStyle(ChatFormatting.GRAY)));
			row.active = this.selected != null;
			row.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(name(mapping))
				.append(Component.literal("\n" + mapping.getCategory().label().getString()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(this.selected == null ? "\nErst oben eine Taste anklicken." : "\nKlick: auf die gewählte Taste legen")
					.withStyle(ChatFormatting.DARK_GRAY))));
		}
	}

	private static String name(KeyMapping mapping) {
		return Component.translatable(mapping.getName()).getString();
	}

	private void assign(KeyMapping mapping, InputConstants.Key key) {
		mapping.setKey(key);
		KeyMapping.resetMapping();
		this.minecraftOptions().save();
		boolean searchFocused = this.getFocused() == this.search;
		this.rebuildWidgets();
		if (searchFocused) {
			this.setFocused(this.search);
		}
	}

	/** Selects a key for the panel below, or deselects it; Escape cannot be bound and is ignored. */
	public void select(InputConstants.Key key) {
		if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			return;
		}
		this.selected = key.equals(this.selected) ? null : key;
		this.rebuildWidgets();
	}

	private int keyX(KeyDef def) {
		return this.originX + (int) Math.round(def.x() * this.unit);
	}

	private int keyY(KeyDef def) {
		return TOP + (int) Math.round(def.y() * this.unit);
	}

	private int keyW(KeyDef def) {
		return (int) Math.round((def.x() + def.w()) * this.unit) - (int) Math.round(def.x() * this.unit) - 2;
	}

	private int keyH(KeyDef def) {
		return (int) Math.round((def.y() + def.h()) * this.unit) - (int) Math.round(def.y() * this.unit) - 2;
	}

	private KeyDef keyAt(double mouseX, double mouseY) {
		for (KeyDef def : KeyboardLayout.KEYS) {
			int x = this.keyX(def);
			int y = this.keyY(def);
			if (mouseX >= x && mouseX < x + this.keyW(def) && mouseY >= y && mouseY < y + this.keyH(def)) {
				return def;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			KeyDef def = this.keyAt(event.x(), event.y());
			if (def != null) {
				this.select(def.key());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= this.listX && mouseY > this.panelTop + ROW) {
			int max = Math.max(0, this.filtered.size() - this.rows.size());
			this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
			this.refreshRows();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, "— TASTATUR-ANSICHT —", this.width / 2, 5, 0xFFAAAAAA);

		String needle = query.strip().toLowerCase(Locale.ROOT);
		KeyDef hovered = this.keyAt(mouseX, mouseY);
		for (KeyDef def : KeyboardLayout.KEYS) {
			this.renderKey(graphics, def, def == hovered, needle);
		}

		this.renderInfo(graphics);
		if (this.filtered.size() > this.rows.size()) {
			String page = (this.scroll + 1) + "–" + Math.min(this.filtered.size(), this.scroll + this.rows.size())
				+ " von " + this.filtered.size();
			graphics.drawString(this.font, page, this.listX + this.listWidth - this.font.width(page), this.panelTop - 10, TEXT_DIM);
		}

		if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hovered.label()).withStyle(ChatFormatting.WHITE));
			List<KeyMapping> on = this.on(hovered.key());
			if (on.isEmpty()) {
				lines.add(Component.literal("frei").withStyle(ChatFormatting.GRAY));
			}
			for (KeyMapping mapping : on) {
				lines.add(Component.literal(name(mapping)).withStyle(isConflict(on) ? ChatFormatting.RED : ChatFormatting.GREEN)
					.append(Component.literal("  " + mapping.getCategory().label().getString()).withStyle(ChatFormatting.DARK_GRAY)));
			}
			for (KeyMapping mapping : this.bound(hovered.key(), true)) {
				lines.add(Component.literal("F3 + " + hovered.label() + ": " + name(mapping)).withStyle(ChatFormatting.DARK_GRAY));
			}
			graphics.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	private void renderKey(GuiGraphics graphics, KeyDef def, boolean hovered, String needle) {
		int x = this.keyX(def);
		int y = this.keyY(def);
		int w = this.keyW(def);
		int h = this.keyH(def);
		List<KeyMapping> on = this.on(def.key());

		if (on.isEmpty()) {
			HugoStyle.drawBox(graphics, x, y, w, h, hovered ? HugoStyle.State.HOVER : HugoStyle.State.NORMAL);
		} else {
			boolean conflict = isConflict(on);
			graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, conflict ? CONFLICT_FILL : BOUND_FILL);
			int border = conflict ? CONFLICT_BORDER : BOUND_BORDER;
			HugoStyle.drawOutline(graphics, x, y, w, h, hovered ? 0xFFFFFFFF : border);
		}
		if (!needle.isEmpty()) {
			for (KeyMapping mapping : on) {
				if (name(mapping).toLowerCase(Locale.ROOT).contains(needle)) {
					HugoStyle.drawOutline(graphics, x - 1, y - 1, w + 2, h + 2, MATCH_BORDER);
					break;
				}
			}
		}
		if (def.key().equals(this.selected)) {
			HugoStyle.drawOutline(graphics, x - 1, y - 1, w + 2, h + 2, SELECTED_BORDER);
			HugoStyle.drawOutline(graphics, x, y, w, h, SELECTED_BORDER);
		}

		int textWidth = this.font.width(def.label());
		float scale = Math.min(1.0F, Math.min((w - 3) / (float) Math.max(1, textWidth), (h - 2) / 9.0F));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + w / 2.0F, y + h / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.drawString(this.font, def.label(), -textWidth / 2, -4, on.isEmpty() ? TEXT_DIM : TEXT, false);
		graphics.pose().popMatrix();
	}

	private void renderInfo(GuiGraphics graphics) {
		int x = this.originX;
		int infoWidth = this.listX - 8 - x;
		if (this.selected == null) {
			graphics.drawString(this.font, "Klick auf eine Taste,", x, this.panelTop + 2, TEXT_DIM);
			graphics.drawString(this.font, "um sie zu belegen.", x, this.panelTop + 12, TEXT_DIM);
			graphics.drawString(this.font, "Grün = belegt", x, this.panelTop + 28, BOUND_BORDER);
			graphics.drawString(this.font, "Rot = doppelt belegt", x, this.panelTop + 38, CONFLICT_BORDER);
			return;
		}
		KeyDef def = KeyboardLayout.KEYS.stream().filter(d -> d.key().equals(this.selected)).findFirst().orElse(null);
		String title = "Taste: " + (def != null ? def.label() : this.selected.getDisplayName().getString());
		graphics.drawString(this.font, title, x, this.panelTop + 2, 0xFFFFFFFF);
		List<KeyMapping> on = this.on(this.selected);
		int y = this.panelTop + 12;
		if (on.isEmpty()) {
			graphics.drawString(this.font, "frei – rechts eine Aktion wählen", x, y + 6, TEXT_DIM);
		}
		for (KeyMapping mapping : on) {
			if (y + ROW > this.height - 6 - ROW - 4) {
				break;
			}
			String label = this.font.plainSubstrByWidth(name(mapping), infoWidth - ROW - 4);
			graphics.drawString(this.font, label, x, y + 6, isConflict(on) ? CONFLICT_BORDER : TEXT);
			y += ROW + 2;
		}
	}
}
