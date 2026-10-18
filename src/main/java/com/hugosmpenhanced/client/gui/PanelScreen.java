package com.hugosmpenhanced.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The frame the mod's overview screens share (payments, money, spawners, farm): a dark panel
 * with a title, optional tabs and search, a summary block and a scrolling list of rows, with
 * buttons along the bottom. Subclasses fill in the summary and the rows.
 */
public abstract class PanelScreen extends Screen implements StyledScreen {
	protected static final int HEADER = 36;
	protected static final int TAB_H = 14;
	protected static final int FOOTER = 30;
	protected static final int PAD = 10;

	/** One line in the list. */
	public interface Row {
		void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean hovered);

		/** Tooltip lines for the mouse, or an empty list. */
		default List<Component> tooltip() {
			return List.of();
		}

		/** A click on the row; true when it did something. */
		default boolean click(int button) {
			return false;
		}
	}

	private record Footer(Supplier<String> label, Runnable action) {
	}

	protected final Screen parent;
	private final int accent;
	private final int preferredWidth;
	private final List<String> tabs = new ArrayList<>();
	private Consumer<Integer> onTab = index -> { };
	protected int tab;
	private final List<Footer> footers = new ArrayList<>();
	private final java.util.Map<Button, Footer> footerButtons = new java.util.HashMap<>();
	private boolean searchable;
	private String searchHint = "Suchen…";
	protected EditBox search;
	protected String query = "";
	private double scroll;
	private boolean draggingBar;

	protected int left;
	protected int top;
	protected int panelWidth;
	protected int panelHeight;
	private int listTop;
	private int listBottom;

	protected PanelScreen(Component title, Screen parent, int accent, int width) {
		super(title);
		this.parent = parent;
		this.accent = accent;
		this.preferredWidth = width;
	}

	// ------------------------------------------------------------------ set-up for subclasses

	protected void tabs(List<String> labels, Consumer<Integer> onChange) {
		this.tabs.clear();
		this.tabs.addAll(labels);
		this.onTab = onChange;
	}

	protected void searchable(String hint) {
		this.searchable = true;
		this.searchHint = hint;
	}

	protected void footer(String label, Runnable action) {
		this.footers.add(new Footer(() -> label, action));
	}

	/** A footer button whose text can change, e.g. "Wirklich leeren?". */
	protected void footer(Supplier<String> label, Runnable action) {
		this.footers.add(new Footer(label, action));
	}

	protected abstract String subtitle();

	/** Height of the block between the tabs and the list. */
	protected int summaryHeight() {
		return 0;
	}

	protected void renderSummary(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
	}

	protected abstract List<? extends Row> rows();

	protected int rowHeight() {
		return 16;
	}

	protected String emptyText() {
		return "Noch nichts da.";
	}

	/** Small grey text right of the title, e.g. a status. */
	protected String status() {
		return "";
	}

	protected int statusColor() {
		return Theme.MUTED;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		this.panelWidth = Math.min(this.preferredWidth, this.width - 16);
		this.panelHeight = Math.min(this.height - 16, 300);
		this.left = (this.width - this.panelWidth) / 2;
		this.top = (this.height - this.panelHeight) / 2;
		int y = this.top + HEADER;
		if (!this.tabs.isEmpty() || this.searchable) {
			if (this.searchable) {
				int width = Math.min(130, this.panelWidth / 3);
				String old = this.search != null ? this.search.getValue() : "";
				this.search = new EditBox(this.font, this.left + this.panelWidth - PAD - width, y, width, TAB_H + 2, Component.literal(this.searchHint));
				this.search.setHint(Component.literal(this.searchHint).withColor(Theme.FAINT));
				this.search.setValue(old);
				this.search.setResponder(value -> {
					this.query = value.trim().toLowerCase(java.util.Locale.ROOT);
					this.scroll = 0;
				});
				this.addRenderableWidget(this.search);
			}
			y += TAB_H + 6;
		}
		this.listTop = y + this.summaryHeight();
		this.listBottom = this.top + this.panelHeight - FOOTER - 2;

		this.footerButtons.clear();
		List<Footer> buttons = this.footers;
		int count = buttons.size() + 1;
		int gap = 4;
		int width = Math.min(110, (this.panelWidth - 2 * PAD - (count - 1) * gap) / count);
		int x = this.left + this.panelWidth - PAD - count * width - (count - 1) * gap;
		int by = this.top + this.panelHeight - FOOTER + 6;
		for (Footer footer : buttons) {
			Button button = this.addRenderableWidget(Button.builder(Component.literal(footer.label().get()), b -> {
				footer.action().run();
				b.setMessage(Component.literal(footer.label().get()));
			}).bounds(x, by, width, 18).build());
			this.footerButtons.put(button, footer);
			x += width + gap;
		}
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose()).bounds(x, by, width, 18).build());
	}

	protected int listTop() {
		return this.listTop;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(this.left, this.top, this.left + this.panelWidth, this.top + this.panelHeight, Theme.FRAME);
		HugoStyle.drawOutline(graphics, this.left, this.top, this.panelWidth, this.panelHeight, Theme.FRAME_BORDER);
		graphics.fill(this.left + 1, this.top + 1, this.left + this.panelWidth - 1, this.top + 3, this.accent);

		graphics.pose().pushMatrix();
		graphics.pose().translate(this.left + PAD, this.top + 9);
		graphics.pose().scale(1.5f, 1.5f);
		graphics.drawString(this.font, this.title, 0, 0, Theme.WHITE, false);
		graphics.pose().popMatrix();
		graphics.drawString(this.font, this.subtitle(), this.left + PAD, this.top + 24, Theme.MUTED, false);
		String status = this.status();
		if (!status.isEmpty()) {
			graphics.drawString(this.font, status, this.left + this.panelWidth - PAD - this.font.width(status), this.top + 12, this.statusColor(), false);
		}

		this.renderTabs(graphics, mouseX, mouseY);
		int summary = this.summaryHeight();
		if (summary > 0) {
			this.renderSummary(graphics, this.left + PAD, this.listTop - summary, this.panelWidth - 2 * PAD, mouseX, mouseY);
		}
		graphics.fill(this.left + 6, this.listTop - 2, this.left + this.panelWidth - 6, this.listTop - 1, Theme.SECTION_BORDER);
		Row hovered = this.renderList(graphics, mouseX, mouseY);
		this.footerButtons.forEach((button, footer) -> button.setMessage(Component.literal(footer.label().get())));
		super.render(graphics, mouseX, mouseY, partialTick);
		if (hovered != null && !hovered.tooltip().isEmpty()) {
			graphics.setComponentTooltipForNextFrame(this.font, hovered.tooltip(), mouseX, mouseY);
		}
	}

	private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
		int x = this.left + PAD;
		int y = this.top + HEADER;
		for (int i = 0; i < this.tabs.size(); i++) {
			String label = this.tabs.get(i);
			int width = this.font.width(label) + 12;
			boolean selected = i == this.tab;
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_H;
			graphics.fill(x, y, x + width, y + TAB_H, selected ? Theme.GOLD_FILL : over ? 0xF0303030 : 0xF0181818);
			HugoStyle.drawOutline(graphics, x, y, width, TAB_H, selected ? Theme.GOLD : Theme.CHIP_BORDER);
			graphics.drawString(this.font, label, x + 6, y + 3, selected ? Theme.WHITE : Theme.TEXT_DIM, false);
			x += width + 4;
		}
	}

	private Row renderList(GuiGraphics graphics, int mouseX, int mouseY) {
		List<? extends Row> rows = this.rows();
		int rowHeight = this.rowHeight();
		int visible = this.listBottom - this.listTop;
		int content = rows.size() * rowHeight;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, content - visible));
		if (rows.isEmpty()) {
			graphics.drawCenteredString(this.font, this.emptyText(), this.left + this.panelWidth / 2, this.listTop + visible / 2 - 4, Theme.MUTED);
			return null;
		}
		int x = this.left + 6;
		int width = this.panelWidth - 12 - (content > visible ? 6 : 0);
		Row hovered = null;
		graphics.enableScissor(this.left + 1, this.listTop, this.left + this.panelWidth - 1, this.listBottom);
		int first = (int) (this.scroll / rowHeight);
		for (int i = first; i < rows.size(); i++) {
			int y = this.listTop + i * rowHeight - (int) this.scroll;
			if (y >= this.listBottom) {
				break;
			}
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, this.listTop) && mouseY < Math.min(y + rowHeight, this.listBottom);
			if (i % 2 == 1) {
				graphics.fill(x, y, x + width, y + rowHeight, 0x0CFFFFFF);
			}
			if (over) {
				graphics.fill(x, y, x + width, y + rowHeight, Theme.ROW_HOVER);
				hovered = rows.get(i);
			}
			rows.get(i).render(graphics, this.font, x, y, width, rowHeight, over);
		}
		graphics.disableScissor();
		if (content > visible) {
			int barX = this.left + this.panelWidth - 10;
			int barHeight = Math.max(16, visible * visible / content);
			int barY = this.listTop + (int) ((visible - barHeight) * (this.scroll / (content - visible)));
			graphics.fill(barX, this.listTop, barX + 3, this.listBottom, 0x40FFFFFF);
			graphics.fill(barX, barY, barX + 3, barY + barHeight, this.draggingBar ? Theme.GOLD : 0xFFA0A0A0);
		}
		return hovered;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int x = this.left + PAD;
		int y = this.top + HEADER;
		for (int i = 0; i < this.tabs.size(); i++) {
			int width = this.font.width(this.tabs.get(i)) + 12;
			if (mx >= x && mx < x + width && my >= y && my < y + TAB_H) {
				this.tab = i;
				this.scroll = 0;
				this.onTab.accept(i);
				return true;
			}
			x += width + 4;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int visible = this.listBottom - this.listTop;
		int content = this.rows().size() * this.rowHeight();
		if (content > visible && mx >= this.left + this.panelWidth - 12 && mx < this.left + this.panelWidth - 4
			&& my >= this.listTop && my < this.listBottom) {
			this.draggingBar = true;
			this.dragBar(my);
			return true;
		}
		if (my >= this.listTop && my < this.listBottom && mx >= this.left + 6 && mx < this.left + this.panelWidth - 6) {
			int index = (int) ((my - this.listTop + this.scroll) / this.rowHeight());
			List<? extends Row> rows = this.rows();
			if (index >= 0 && index < rows.size()) {
				return rows.get(index).click(event.button());
			}
		}
		return false;
	}

	private void dragBar(double mouseY) {
		int visible = this.listBottom - this.listTop;
		int content = this.rows().size() * this.rowHeight();
		double share = (mouseY - this.listTop) / visible;
		this.scroll = Mth.clamp(share * content - visible / 2.0, 0, Math.max(0, content - visible));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.draggingBar) {
			this.dragBar(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.draggingBar = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= this.listTop && mouseY < this.listBottom) {
			this.scroll -= scrollY * this.rowHeight() * 2;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	protected boolean matches(String... texts) {
		if (this.query.isEmpty()) {
			return true;
		}
		for (String text : texts) {
			if (text != null && text.toLowerCase(java.util.Locale.ROOT).contains(this.query)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ helpers for rows

	/** Draws text cut to a width with an ellipsis. */
	public static void clipped(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color) {
		if (font.width(text) > width) {
			text = font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
		}
		graphics.drawString(font, text, x, y, color, false);
	}

	public static void right(GuiGraphics graphics, Font font, String text, int rightX, int y, int color) {
		graphics.drawString(font, text, rightX - font.width(text), y, color, false);
	}
}
