package com.hugosmpenhanced.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.StyledScreen;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The mod's settings menu: categories with icons on the left, tabs on top, sections of rows in a
 * scrolling area, and a search over every option. Everything shown comes from {@link ModSettings}.
 * Changes are saved immediately, so there is no "Done" button; Escape or the open key closes it.
 */
public class SettingsScreen extends Screen implements StyledScreen {
	private static final int SECTION_TITLE = 16;
	private static final int SECTION_GAP = 6;
	private static final int CATEGORY_ROW = 24;

	private static int lastCategory;
	private static int lastTab;

	private final Screen parent;
	private int category;
	private int tab;
	private double scroll;

	private EditBox search;
	private String searchValue = "";
	private SettingsOption active;

	// layout, recomputed in init()
	private int fx;
	private int fy;
	private int fw;
	private int fh;
	private int sidebarWidth;
	private boolean compact;
	private int contentLeft;
	private int contentWidth;
	private int contentTop;
	private int contentBottom;
	private int contentHeight;
	private int searchX;
	private int closeX;
	private final List<int[]> tabBoxes = new ArrayList<>();

	public SettingsScreen(Screen parent) {
		super(Component.literal("HugoSMP Enhanced"));
		this.parent = parent;
		this.category = Mth.clamp(lastCategory, 0, ModSettings.categories().size() - 1);
		this.tab = lastTab;
	}

	/** Opens on the given category and tab (by name); unknown names keep the last position. */
	public SettingsScreen select(String categoryName, String tabName) {
		List<SettingsCategory> categories = ModSettings.categories();
		for (int i = 0; i < categories.size(); i++) {
			if (categories.get(i).name().equals(categoryName)) {
				this.category = i;
				this.tab = 0;
				List<SettingsTab> tabs = categories.get(i).tabs();
				for (int t = 0; t < tabs.size(); t++) {
					if (tabs.get(t).name().equals(tabName)) {
						this.tab = t;
					}
				}
			}
		}
		this.scroll = 0;
		return this;
	}

	private SettingsCategory current() {
		return ModSettings.categories().get(this.category);
	}

	private boolean searching() {
		return !this.searchValue.isBlank();
	}

	@Override
	protected void init() {
		this.fw = Math.min(this.width - 12, 620);
		this.fh = Math.min(this.height - 12, 360);
		this.fx = (this.width - this.fw) / 2;
		this.fy = (this.height - this.fh) / 2;
		this.compact = this.fw < 380;
		int widest = 0;
		for (SettingsCategory cat : ModSettings.categories()) {
			widest = Math.max(widest, this.font.width(cat.name()));
		}
		this.sidebarWidth = this.compact ? 30 : Math.max(112, widest + 40);
		this.contentLeft = this.fx + this.sidebarWidth + 10;
		this.contentWidth = this.fx + this.fw - 14 - this.contentLeft;
		this.contentTop = this.fy + 58;
		this.contentBottom = this.fy + this.fh - 16;
		this.closeX = this.fx + this.fw - 24;
		int searchWidth = Math.min(130, Math.max(70, this.contentWidth / 3));
		this.searchX = this.closeX - 4 - searchWidth;

		this.search = new EditBox(this.font, this.searchX + 17, this.fy + 12, searchWidth - 21, 10, Component.literal("Suchen"));
		this.search.setBordered(false);
		this.search.setMaxLength(40);
		this.search.setHint(Component.literal("Suchen...").withStyle(ChatFormatting.DARK_GRAY));
		this.search.setValue(this.searchValue);
		this.search.setResponder(value -> {
			this.searchValue = value;
			this.scroll = 0;
		});
		this.addRenderableWidget(this.search);
		this.clampTab();
	}

	private void clampTab() {
		this.tab = Mth.clamp(this.tab, 0, this.current().tabs().size() - 1);
		lastCategory = this.category;
		lastTab = this.tab;
	}

	/** Sections to show: the current tab, or one section per match group while searching. */
	private List<SettingsSection> sections() {
		if (!this.searching()) {
			return this.current().tabs().get(this.tab).sections();
		}
		String query = this.searchValue.strip();
		List<SettingsSection> results = new ArrayList<>();
		for (SettingsCategory cat : ModSettings.categories()) {
			for (SettingsTab t : cat.tabs()) {
				for (SettingsSection section : t.sections()) {
					List<SettingsOption> hits = new ArrayList<>();
					for (SettingsOption option : section.options()) {
						if (option.matches(query)) {
							hits.add(option);
						}
					}
					if (!hits.isEmpty()) {
						String crumb = cat.name() + " > " + t.name();
						if (!section.title().equalsIgnoreCase(t.name())) {
							crumb += " > " + section.title();
						}
						results.add(new SettingsSection(crumb, hits));
					}
				}
			}
		}
		return results;
	}

	private int resultCount(List<SettingsSection> sections) {
		int count = 0;
		for (SettingsSection section : sections) {
			count += section.options().size();
		}
		return count;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Theme.panel(graphics, this.fx, this.fy, this.fw, this.fh, Theme.FRAME, Theme.FRAME_BORDER);
		this.renderSidebar(graphics, mouseX, mouseY);
		List<SettingsSection> sections = this.sections();
		this.renderHeader(graphics, mouseX, mouseY, sections);
		this.renderContent(graphics, mouseX, mouseY, sections);
		String footer = "Änderungen werden sofort gespeichert  -  ESC schließt";
		if (this.font.width(footer) > this.contentWidth) {
			footer = "Wird sofort gespeichert  -  ESC schließt";
		}
		graphics.drawString(this.font, footer, this.contentLeft, this.fy + this.fh - 12, Theme.FAINT, false);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
		int left = this.fx + 1;
		int right = this.fx + this.sidebarWidth;
		graphics.fill(left, this.fy + 1, right, this.fy + this.fh - 1, Theme.SIDEBAR);
		graphics.fill(right, this.fy + 1, right + 1, this.fy + this.fh - 1, Theme.FRAME_BORDER);

		Theme.icon(graphics, "brand", this.fx + (this.compact ? 5 : 7), this.fy + 7, 20, Theme.WHITE);
		if (!this.compact) {
			int tx = this.fx + 31;
			graphics.drawString(this.font, "Hugo", tx, this.fy + 8, 0xFFFF4646);
			graphics.drawString(this.font, "SMP", tx + this.font.width("Hugo"), this.fy + 8, 0xFFC496FF);
			graphics.drawString(this.font, "ENHANCED", tx, this.fy + 18, Theme.GOLD);
		}

		List<SettingsCategory> categories = ModSettings.categories();
		for (int i = 0; i < categories.size(); i++) {
			SettingsCategory cat = categories.get(i);
			int y = this.categoryY(i);
			boolean selected = i == this.category && !this.searching();
			boolean over = this.inside(mouseX, mouseY, left + 3, y, right - left - 6, CATEGORY_ROW - 2);
			if (selected) {
				graphics.fill(left + 3, y, right - 3, y + CATEGORY_ROW - 2, Theme.SELECTED);
				graphics.fill(left + 3, y, left + 5, y + CATEGORY_ROW - 2, Theme.GOLD);
			} else if (over) {
				graphics.fill(left + 3, y, right - 3, y + CATEGORY_ROW - 2, Theme.ROW_HOVER);
			}
			int iconX = this.compact ? left + 6 : left + 9;
			Theme.icon(graphics, cat.icon(), iconX, y + 3, 16, selected || over ? Theme.ICON_ON : Theme.ICON_OFF);
			if (!this.compact) {
				graphics.drawString(this.font, this.font.plainSubstrByWidth(cat.name(), right - left - 34), left + 30, y + 7,
					selected ? Theme.WHITE : Theme.TEXT_DIM);
			} else if (over) {
				graphics.setTooltipForNextFrame(this.font, Component.literal(cat.name()), mouseX, mouseY);
			}
		}

		if (!this.compact) {
			int fy2 = this.fy + this.fh - 32;
			graphics.fill(left + 7, fy2 - 4, right - 7, fy2 - 3, Theme.FRAME_BORDER);
			Theme.icon(graphics, "key", left + 8, fy2 - 3, 16, 0xFF969696);
			graphics.drawString(this.font, "Öffnen", left + 28, fy2 + 1, Theme.MUTED, false);
			String key = SettingsFeature.openKey == null || SettingsFeature.openKey.isUnbound()
				? "-" : SettingsFeature.openKey.getTranslatedKeyMessage().getString();
			int kx = left + 32 + this.font.width("Öffnen");
			int kw = this.font.width(key) + 8;
			Theme.panel(graphics, kx, fy2 - 2, kw, 13, 0xFF282828, 0xFF646464);
			graphics.drawString(this.font, key, kx + 4, fy2 + 1, Theme.WHITE, false);
			graphics.drawString(this.font, "v" + version(), left + 8, fy2 + 16, Theme.FAINT, false);
		}
	}

	private int categoryY(int index) {
		return this.fy + 38 + index * CATEGORY_ROW;
	}

	private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY, List<SettingsSection> sections) {
		String title;
		String description;
		if (this.searching()) {
			int count = this.resultCount(sections);
			title = "Suche";
			description = count == 0 ? "Keine Treffer für \"" + this.searchValue.strip() + "\""
				: count + " Treffer für \"" + this.searchValue.strip() + "\"";
		} else {
			title = this.current().name();
			description = this.current().description();
		}
		int textRoom = this.searchX - this.contentLeft - 8;
		graphics.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(title, textRoom)).withStyle(ChatFormatting.BOLD),
			this.contentLeft, this.fy + 9, Theme.WHITE);
		graphics.drawString(this.font, ellipsize(description, textRoom), this.contentLeft, this.fy + 21, Theme.MUTED, false);

		// search field
		int sw = this.closeX - 4 - this.searchX;
		boolean focused = this.search.isFocused();
		Theme.panel(graphics, this.searchX, this.fy + 8, sw, 17, Theme.FIELD, focused ? 0xFFC8C8C8 : Theme.FIELD_BORDER);
		Theme.icon(graphics, "search", this.searchX + 4, this.fy + 12, 9, focused ? Theme.WHITE : 0xFF8A8A8A);

		// close button
		boolean overClose = this.inside(mouseX, mouseY, this.closeX, this.fy + 8, 17, 17);
		Theme.panel(graphics, this.closeX, this.fy + 8, 17, 17, overClose ? 0xFF3A3A3A : 0xFF1E1E1E, overClose ? 0xFFB4B4B4 : Theme.CONTROL_BORDER);
		Theme.glyph(graphics, Theme.CROSS, this.closeX + 6, this.fy + 14, Theme.TEXT);

		// tabs
		this.tabBoxes.clear();
		if (!this.searching()) {
			List<SettingsTab> tabs = this.current().tabs();
			int tx = this.contentLeft + 2;
			int ty = this.fy + 37;
			int[] widths = new int[tabs.size()];
			for (int i = 0; i < tabs.size(); i++) {
				widths[i] = this.font.width(tabs.get(i).name());
			}
			int gap = this.fitTabs(widths, this.fx + this.fw - 13 - tx);
			for (int i = 0; i < tabs.size(); i++) {
				String full = tabs.get(i).name();
				int w = widths[i];
				String name = this.font.width(full) > w ? this.ellipsize(full, w) : full;
				w = this.font.width(name);
				boolean selected = i == this.tab;
				boolean over = this.inside(mouseX, mouseY, tx - 5, ty - 4, w + 10, 16);
				if (selected) {
					Theme.panel(graphics, tx - 5, ty - 4, w + 10, 16, 0xFF282828, 0xFF5A5A5A);
					graphics.fill(tx - 3, ty + 10, tx + w + 3, ty + 11, Theme.GOLD);
				} else if (over) {
					Theme.panel(graphics, tx - 5, ty - 4, w + 10, 16, 0xFF1E1E1E, 0);
				}
				graphics.drawString(this.font, name, tx, ty, selected ? Theme.WHITE : over ? Theme.TEXT : Theme.MUTED, selected);
				if (over && !name.equals(full)) {
					graphics.setTooltipForNextFrame(this.font, Component.literal(full), mouseX, mouseY);
				}
				this.tabBoxes.add(new int[] {tx - 5, ty - 4, w + 10, 16});
				tx += w + gap;
			}
		}
		graphics.fill(this.contentLeft - 4, this.fy + 53, this.fx + this.fw - 8, this.fy + 54, Theme.FRAME_BORDER);
	}

	private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, List<SettingsSection> sections) {
		this.contentHeight = this.measure(sections);
		int viewport = this.contentBottom - this.contentTop;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.contentHeight - viewport));
		boolean mouseInView = mouseY >= this.contentTop && mouseY < this.contentBottom;

		graphics.enableScissor(this.contentLeft - 2, this.contentTop, this.contentLeft + this.contentWidth + 2, this.contentBottom);
		int y = this.contentTop + 2 - (int) this.scroll;
		for (SettingsSection section : sections) {
			int height = this.sectionHeight(section);
			// Every section is drawn (the scissor clips it) so each row knows where it is for clicks.
			this.renderSection(graphics, section, y, height, mouseX, mouseY, mouseInView);
			y += height + SECTION_GAP;
		}
		if (sections.isEmpty() && this.searching()) {
			graphics.drawString(this.font, "Nichts gefunden. Versuch ein anderes Wort, z. B. \"Taste\" oder \"Chat\".",
				this.contentLeft, this.contentTop + 6, Theme.MUTED, false);
		}
		graphics.disableScissor();

		if (this.contentHeight > viewport) {
			int barX = this.contentLeft + this.contentWidth + 6;
			graphics.fill(barX, this.contentTop, barX + 2, this.contentBottom, 0xFF2A2A2A);
			int barHeight = Math.max(16, viewport * viewport / this.contentHeight);
			int barY = this.contentTop + (int) ((viewport - barHeight) * (this.scroll / (this.contentHeight - viewport)));
			graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFF6E6E6E);
		}
	}

	private int sectionHeight(SettingsSection section) {
		int height = SECTION_TITLE + 4;
		for (SettingsOption option : section.options()) {
			height += option.height(this.font, this.contentWidth - 4);
		}
		return height;
	}

	private int measure(List<SettingsSection> sections) {
		int total = 4;
		for (SettingsSection section : sections) {
			total += this.sectionHeight(section) + SECTION_GAP;
		}
		return total;
	}

	private void renderSection(GuiGraphics graphics, SettingsSection section, int y, int height, int mouseX, int mouseY, boolean mouseInView) {
		Theme.panel(graphics, this.contentLeft, y, this.contentWidth, height, Theme.SECTION, Theme.SECTION_BORDER);
		String title = this.searching() ? section.title() : section.title().toUpperCase(Locale.ROOT);
		graphics.drawString(this.font, this.font.plainSubstrByWidth(title, this.contentWidth - 16), this.contentLeft + 8, y + 5,
			this.searching() ? Theme.GOLD : 0xFFA0A0A0, false);
		int rowY = y + SECTION_TITLE;
		for (SettingsOption option : section.options()) {
			graphics.fill(this.contentLeft + 8, rowY - 1, this.contentLeft + this.contentWidth - 8, rowY, Theme.DIVIDER);
			option.render(graphics, this.contentLeft + 2, rowY, this.contentWidth - 4, mouseX, mouseY, mouseInView && this.active == null);
			rowY += option.height(this.font, this.contentWidth - 4);
		}
	}

	// ------------------------------------------------------------------ input

	private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private SettingsOption capturingOption() {
		for (SettingsOption option : ModSettings.allOptions()) {
			if (option.capturing()) {
				return option;
			}
		}
		return null;
	}

	private void stopCapturing(SettingsOption except) {
		for (SettingsOption option : ModSettings.allOptions()) {
			if (option != except && option.capturing()) {
				option.stopCapturing();
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		if (this.inside(mx, my, this.closeX, this.fy + 8, 17, 17)) {
			this.onClose();
			return true;
		}
		List<SettingsCategory> categories = ModSettings.categories();
		for (int i = 0; i < categories.size(); i++) {
			if (this.inside(mx, my, this.fx + 3, this.categoryY(i), this.sidebarWidth - 5, CATEGORY_ROW - 2)) {
				this.stopCapturing(null);
				this.category = i;
				this.tab = 0;
				this.scroll = 0;
				this.search.setValue("");
				this.search.setFocused(false);
				this.setFocused(null);
				this.clampTab();
				return true;
			}
		}
		for (int i = 0; i < this.tabBoxes.size(); i++) {
			int[] box = this.tabBoxes.get(i);
			if (this.inside(mx, my, box[0], box[1], box[2], box[3])) {
				this.stopCapturing(null);
				this.tab = i;
				this.scroll = 0;
				this.clampTab();
				return true;
			}
		}
		if (this.inside(mx, my, this.searchX, this.fy + 8, this.closeX - 4 - this.searchX, 17)) {
			this.stopCapturing(null);
			this.setFocused(this.search);
			this.search.setFocused(true);
			super.mouseClicked(event, doubleClick);
			return true;
		}
		this.search.setFocused(false);
		this.setFocused(null);
		if (my >= this.contentTop && my < this.contentBottom) {
			for (SettingsSection section : this.sections()) {
				for (SettingsOption option : section.options()) {
					if (option.mouseClicked(mx, my, button)) {
						this.stopCapturing(option);
						this.active = option;
						return true;
					}
				}
			}
		}
		this.stopCapturing(null);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.active != null && this.active.mouseDragged(event.x(), event.y())) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.active != null) {
			this.active.mouseReleased();
			this.active = null;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= this.contentTop && mouseY < this.contentBottom) {
			this.scroll -= scrollY * 24;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		SettingsOption capturing = this.capturingOption();
		if (capturing != null) {
			return capturing.keyPressed(event);
		}
		boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		if (control && event.key() == GLFW.GLFW_KEY_F) {
			this.setFocused(this.search);
			this.search.setFocused(true);
			return true;
		}
		if (this.search.isFocused()) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE && !this.searchValue.isEmpty()) {
				this.search.setValue("");
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.search.setFocused(false);
				this.setFocused(null);
				return true;
			}
			return super.keyPressed(event);
		}
		if (SettingsFeature.openKey != null && SettingsFeature.openKey.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		SettingsOption capturing = this.capturingOption();
		if (capturing != null) {
			return capturing.charTyped(event);
		}
		if (!this.search.isFocused() && event.isAllowedChatCharacter() && !Character.isWhitespace(event.codepoint())) {
			// Typing anywhere starts a search.
			this.setFocused(this.search);
			this.search.setFocused(true);
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		this.stopCapturing(null);
		ModConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.stopCapturing(null);
		ModConfigManager.save();
	}

	/**
	 * Makes the tab row fit {@code room}: first the gaps shrink from 16 to 10, then the longest names
	 * are cut (with "...") down to one common width, while the selected tab keeps its full name.
	 * {@code widths} is changed in place; returns the gap to use.
	 */
	private int fitTabs(int[] widths, int room) {
		int n = widths.length;
		int total = 0;
		for (int w : widths) {
			total += w;
		}
		for (int gap = 16; gap >= 10; gap--) {
			if (total + gap * (n - 1) <= room) {
				return gap;
			}
		}
		int gap = 10;
		int left = room - gap * (n - 1);
		int others = n;
		if (this.tab >= 0 && this.tab < n) {
			left -= widths[this.tab];
			others--;
		}
		if (others <= 0) {
			return gap;
		}
		// Largest common cap so that the capped names fit what is left.
		int min = this.font.width("W...");
		int cap = Math.max(min, left / others);
		for (int c = cap; ; c++) {
			int sum = 0;
			for (int i = 0; i < n; i++) {
				if (i != this.tab) {
					sum += Math.min(widths[i], c);
				}
			}
			if (sum > left || c > 400) {
				cap = Math.max(min, c - 1);
				break;
			}
		}
		for (int i = 0; i < n; i++) {
			if (i != this.tab) {
				widths[i] = Math.min(widths[i], cap);
			}
		}
		return gap;
	}

	private String ellipsize(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, width - this.font.width("...")).stripTrailing() + "...";
	}

	private static String version() {
		return FabricLoader.getInstance().getModContainer(HugoSmpEnhancedClient.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}
}
