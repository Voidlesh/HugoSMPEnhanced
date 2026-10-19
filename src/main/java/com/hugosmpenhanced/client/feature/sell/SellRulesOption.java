package com.hugosmpenhanced.client.feature.sell;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;


import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The rule list of the sell assistant inside the settings menu: one line per item with its reserve
 * ("Behalten"), its minimum amount ("Ab Menge"), an on/off switch and a cross. Numbers are edited in
 * place; new rules come from the item in hand or from a small item search (German or English name,
 * or the item id).
 */
public class SellRulesOption extends SettingsOption {
	private static final int ROW = 20;
	private static final int HEADER = 11;
	private static final int FIELD_W = 40;
	private static final int H = 13;

	private int editRule = -1;
	private int editColumn;
	private StringBuilder editText;
	private StringBuilder search;
	private int[] origin;
	private int innerWidth;

	public SellRulesOption() {
		super("Regeln", "Nur Items mit einer Regel werden verkauft. Behalten zählt das ganze Inventar mit Hotbar.");
	}

	private static List<SellRule> rules() {
		return ModConfigManager.CONFIG.sellRules;
	}

	@Override
	public boolean matches(String query) {
		return super.matches(query) || "verkaufen sell regel".contains(query.toLowerCase(Locale.ROOT));
	}

	@Override
	protected int controlWidth(Font font) {
		return 0;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
	}

	@Override
	protected int belowHeight(Font font, int width) {
		int rows = rules().size();
		return (rows > 0 ? HEADER + rows * ROW : 14) + 4 + H;
	}

	// Column positions relative to the right edge.
	private int deleteX(int right) {
		return right - 9;
	}

	private int toggleX(int right) {
		return right - 9 - 8 - 22;
	}

	private int minX(int right) {
		return this.toggleX(right) - 10 - FIELD_W;
	}

	private int keepX(int right) {
		return this.minX(right) - 8 - FIELD_W;
	}

	@Override
	protected void renderBelow(GuiGraphicsExtractor graphics, Font font, int left, int top, int width, int mouseX, int mouseY) {
		this.origin = new int[] {left, top};
		this.innerWidth = width;
		int right = left + width;
		List<SellRule> rules = rules();
		int y = top;
		if (rules.isEmpty()) {
			graphics.text(font, "Noch keine Regeln. Alt + Klick auf ein Item im Inventar legt eine an.", left, y + 2, Theme.FAINT, false);
			y += 14;
		} else {
			graphics.text(font, "Item", left + 20, y, Theme.FAINT, false);
			graphics.text(font, "Behalten", this.keepX(right), y, Theme.FAINT, false);
			graphics.text(font, "Ab Menge", this.minX(right), y, Theme.FAINT, false);
			y += HEADER;
			for (int i = 0; i < rules.size(); i++) {
				SellRule rule = rules.get(i);
				graphics.fill(left, y - 1, right, y, Theme.DIVIDER);
				ItemStack stack = stackOf(rule.item);
				graphics.item(stack, left, y + 2);
				int nameW = this.keepX(right) - left - 26;
				String name = font.plainSubstrByWidth(stack.isEmpty() ? rule.item : stack.getHoverName().getString(), nameW);
				graphics.text(font, name, left + 20, y + 6, rule.enabled ? Theme.TEXT : Theme.MUTED, false);
				this.field(graphics, font, this.keepX(right), y + 3, i, 0, rule.keep, rule.enabled, mouseX, mouseY);
				this.field(graphics, font, this.minX(right), y + 3, i, 1, rule.minAmount, rule.enabled, mouseX, mouseY);
				boolean overToggle = this.inside(mouseX, mouseY, this.toggleX(right), y + 4, 22, 11);
				Theme.toggle(graphics, this.toggleX(right), y + 4, rule.enabled, overToggle);
				boolean overDelete = this.inside(mouseX, mouseY, this.deleteX(right) - 2, y + 3, 11, 13);
				Theme.glyph(graphics, Theme.CROSS, this.deleteX(right), y + 7, overDelete ? 0xFFFF4040 : Theme.RED_TEXT);
				y += ROW;
			}
		}
		y += 4;
		int bx = left;
		String hand = "+ Aus der Hand";
		int handW = font.width(hand) + 10;
		boolean overHand = this.inside(mouseX, mouseY, bx, y, handW, H);
		Theme.panel(graphics, bx, y, handW, H, 0xFF1E1E1E, overHand ? Theme.GOLD : 0xFF7A7A7A);
		graphics.text(font, hand, bx + 5, y + 3, Theme.WHITE, false);
		bx += handW + 4;
		if (this.search != null) {
			int fieldW = Math.min(110, right - bx);
			Theme.panel(graphics, bx, y, fieldW, H, Theme.FIELD, Theme.GOLD);
			String text = this.search.toString();
			String shown = font.plainSubstrByWidth(text, fieldW - 10, true);
			graphics.text(font, shown.isEmpty() ? "Item suchen ..." : shown, bx + 4, y + 3, shown.isEmpty() ? Theme.FAINT : Theme.WHITE, false);
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = bx + 4 + font.width(shown);
				graphics.fill(cx, y + 2, cx + 1, y + H - 2, Theme.WHITE);
			}
			Item match = bestMatch(text);
			if (match != null) {
				int mx = bx + fieldW + 6;
				graphics.item(new ItemStack(match), mx, y - 2);
				graphics.text(font, font.plainSubstrByWidth(new ItemStack(match).getHoverName().getString() + "  (Enter)", right - mx - 20), mx + 19, y + 3, Theme.MUTED, false);
			}
		} else {
			String find = "+ Item suchen";
			int findW = font.width(find) + 10;
			boolean overFind = this.inside(mouseX, mouseY, bx, y, findW, H);
			Theme.panel(graphics, bx, y, findW, H, 0xFF1E1E1E, overFind ? Theme.GOLD : 0xFF7A7A7A);
			graphics.text(font, find, bx + 5, y + 3, Theme.WHITE, false);
		}
	}

	private void field(GuiGraphicsExtractor graphics, Font font, int x, int y, int rule, int column, int value, boolean enabled,
		int mouseX, int mouseY) {
		boolean editing = this.editRule == rule && this.editColumn == column && this.editText != null;
		boolean over = this.inside(mouseX, mouseY, x, y, FIELD_W, H);
		Theme.panel(graphics, x, y, FIELD_W, H, Theme.FIELD, editing ? Theme.GOLD : over ? 0xFF8A8A8A : Theme.FIELD_BORDER);
		String text = editing ? this.editText.toString() : String.valueOf(value);
		graphics.text(font, text, x + 4, y + 3, enabled || editing ? Theme.WHITE : Theme.MUTED, false);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = x + 4 + font.width(text);
			graphics.fill(cx, y + 2, cx + 1, y + H - 2, Theme.WHITE);
		}
	}

	static ItemStack stackOf(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		return BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/** The item whose name or id fits the search best: exact, then starts with, then contains. */
	static Item bestMatch(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return null;
		}
		Item best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			String spaced = path.replace('_', ' ');
			int score;
			if (name.equals(q) || path.equals(q) || spaced.equals(q)) {
				score = 0;
			} else if (name.startsWith(q) || spaced.startsWith(q)) {
				score = 1;
			} else if (name.contains(q) || spaced.contains(q)) {
				score = 2;
			} else {
				continue;
			}
			score = score * 1000 + name.length();
			if (score < bestScore) {
				bestScore = score;
				best = item;
			}
		}
		return best;
	}

	private static boolean addRule(Item item) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		if (item == Items.AIR || rules().stream().anyMatch(rule -> rule.item.equals(id))) {
			return false;
		}
		rules().add(new SellRule(id));
		changed();
		return true;
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		if (this.origin == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		this.commitEdit();
		Font font = Minecraft.getInstance().font;
		int left = this.origin[0];
		int right = left + this.innerWidth;
		List<SellRule> rules = rules();
		int y = this.origin[1];
		if (!rules.isEmpty()) {
			y += HEADER;
			for (int i = 0; i < rules.size(); i++) {
				SellRule rule = rules.get(i);
				if (this.inside(mouseX, mouseY, this.deleteX(right) - 2, y + 3, 11, 13)) {
					rules.remove(i);
					changed();
					return true;
				}
				if (this.inside(mouseX, mouseY, this.toggleX(right), y + 4, 22, 11)) {
					rule.enabled = !rule.enabled;
					changed();
					return true;
				}
				for (int column = 0; column < 2; column++) {
					int fx = column == 0 ? this.keepX(right) : this.minX(right);
					if (this.inside(mouseX, mouseY, fx, y + 3, FIELD_W, H)) {
						this.editRule = i;
						this.editColumn = column;
						this.editText = new StringBuilder();
						this.search = null;
						return true;
					}
				}
				y += ROW;
			}
		} else {
			y += 14;
		}
		y += 4;
		int handW = font.width("+ Aus der Hand") + 10;
		if (this.inside(mouseX, mouseY, left, y, handW, H)) {
			var player = Minecraft.getInstance().player;
			if (player != null && !player.getMainHandItem().isEmpty()) {
				addRule(player.getMainHandItem().getItem());
			}
			return true;
		}
		int bx = left + handW + 4;
		int findW = this.search != null ? Math.min(110, right - bx) : font.width("+ Item suchen") + 10;
		if (this.inside(mouseX, mouseY, bx, y, findW, H)) {
			if (this.search == null) {
				this.search = new StringBuilder();
			}
			return true;
		}
		return false;
	}

	private void commitEdit() {
		if (this.editText == null) {
			return;
		}
		List<SellRule> rules = rules();
		if (this.editRule >= 0 && this.editRule < rules.size() && !this.editText.isEmpty()) {
			int value = (int) Math.min(100_000, Long.parseLong(this.editText.toString()));
			SellRule rule = rules.get(this.editRule);
			if (this.editColumn == 0) {
				rule.keep = value;
			} else {
				rule.minAmount = value;
			}
			changed();
		}
		this.editText = null;
		this.editRule = -1;
	}

	@Override
	public boolean capturing() {
		return this.editText != null || this.search != null;
	}

	@Override
	public void stopCapturing() {
		this.commitEdit();
		this.search = null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.editText != null) {
			switch (event.key()) {
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_TAB -> this.commitEdit();
				case InputConstants.KEY_ESCAPE -> {
					this.editText = null;
					this.editRule = -1;
				}
				case InputConstants.KEY_BACKSPACE -> {
					if (!this.editText.isEmpty()) {
						this.editText.setLength(this.editText.length() - 1);
					}
				}
				default -> {
				}
			}
			return true;
		}
		if (this.search != null) {
			switch (event.key()) {
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
					Item match = bestMatch(this.search.toString());
					if (match != null) {
						addRule(match);
					}
					this.search = null;
				}
				case InputConstants.KEY_ESCAPE -> this.search = null;
				case InputConstants.KEY_BACKSPACE -> {
					if (!this.search.isEmpty()) {
						this.search.setLength(this.search.length() - 1);
					}
				}
				default -> {
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.editText != null) {
			String typed = event.codepointAsString();
			if (typed.length() == 1 && Character.isDigit(typed.charAt(0)) && this.editText.length() < 6) {
				this.editText.append(typed);
			}
			return true;
		}
		if (this.search != null) {
			if (event.isAllowedChatCharacter() && this.search.length() < 40) {
				this.search.append(event.codepointAsString());
			}
			return true;
		}
		return false;
	}
}
