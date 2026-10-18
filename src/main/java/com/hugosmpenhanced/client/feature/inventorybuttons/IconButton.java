package com.hugosmpenhanced.client.feature.inventorybuttons;

import java.util.function.Consumer;

import com.hugosmpenhanced.client.gui.HugoStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A square button in {@link HugoStyle} showing an item, or a short text symbol when no item is
 * set. With a press handler, a mouse click hands the button to that handler (which decides between
 * click and drag) instead of pressing it right away.
 */
public class IconButton extends Button {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int SELECTED = 0xFFFFFFFF;

	private ItemStack icon;
	private final String symbol;
	private Consumer<IconButton> pressHandler;
	private boolean selected;
	private Tooltip tooltip;
	private boolean tooltipHidden;

	public IconButton(ItemStack icon, Component narration, OnPress onPress) {
		this(icon, "", narration, onPress);
	}

	public IconButton(String symbol, Component narration, OnPress onPress) {
		this(ItemStack.EMPTY, symbol, narration, onPress);
	}

	private IconButton(ItemStack icon, String symbol, Component narration, OnPress onPress) {
		super(0, 0, InventoryButtonsFeature.SIZE, InventoryButtonsFeature.SIZE, narration, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		this.symbol = symbol;
	}

	public void setIcon(ItemStack icon) {
		this.icon = icon;
	}

	public void setPressHandler(Consumer<IconButton> pressHandler) {
		this.pressHandler = pressHandler;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	@Override
	public void setTooltip(Tooltip tooltip) {
		this.tooltip = tooltip;
		super.setTooltip(this.tooltipHidden ? null : tooltip);
	}

	/** Hides the tooltip without forgetting it, e.g. while the button is dragged. */
	public void setTooltipHidden(boolean hidden) {
		this.tooltipHidden = hidden;
		super.setTooltip(hidden ? null : this.tooltip);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (this.pressHandler != null) {
			this.pressHandler.accept(this);
		} else {
			super.onClick(event, doubleClick);
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.getX();
		int y = this.getY();
		HugoStyle.State state = !this.active ? HugoStyle.State.DISABLED
			: this.isHoveredOrFocused() ? HugoStyle.State.HOVER : HugoStyle.State.NORMAL;
		HugoStyle.drawBox(graphics, x, y, this.width, this.height, state);
		if (this.selected) {
			HugoStyle.drawOutline(graphics, x, y, this.width, this.height, SELECTED);
		}

		if (!this.icon.isEmpty()) {
			graphics.item(this.icon, x + (this.width - 16) / 2, y + (this.height - 16) / 2);
		} else if (!this.symbol.isEmpty()) {
			var font = Minecraft.getInstance().font;
			graphics.centeredText(font, this.symbol, x + this.width / 2, y + (this.height - 8) / 2, TEXT);
		}
	}
}
