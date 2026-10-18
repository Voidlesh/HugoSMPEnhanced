package com.hugosmpenhanced.client.settings.option;

import java.util.function.Supplier;

import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.Theme;
import com.mojang.blaze3d.platform.InputConstants;


import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Shows and changes a key binding in place: click, then press a key or a mouse button other than
 * the left one. Escape or a left click cancels, Backspace or Delete removes the binding. A key used by another binding is shown in red.
 */
public class KeyBindOption extends SettingsOption {
	private final Supplier<KeyMapping> mapping;
	private boolean listening;

	public KeyBindOption(String label, String description, Supplier<KeyMapping> mapping) {
		super(label, description);
		this.mapping = mapping;
	}

	private String keyText() {
		if (this.listening) {
			return "> Taste drücken <";
		}
		KeyMapping key = this.mapping.get();
		return key == null || key.isUnbound() ? "Keine" : key.getTranslatedKeyMessage().getString();
	}

	/** Another binding on the same key, or null. */
	private KeyMapping conflict() {
		KeyMapping key = this.mapping.get();
		if (key == null || key.isUnbound()) {
			return null;
		}
		Options options = Minecraft.getInstance().options;
		for (KeyMapping other : options.keyMappings) {
			// F3 combinations are key bindings too since 1.21.9, but only fire together with F3; the
			// hotbar activators only act together with a number key in creative mode.
			if (other != key && other.same(key) && !other.getName().startsWith("key.debug.")
				&& other != options.keySaveHotbarActivator && other != options.keyLoadHotbarActivator) {
				return other;
			}
		}
		return null;
	}

	@Override
	protected int controlWidth(Font font) {
		return font.width(this.keyText()) + 10;
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor graphics, Font font, int right, int centerY, int mouseX, int mouseY) {
		int w = this.controlWidth(font);
		int left = right - w;
		boolean over = this.inside(mouseX, mouseY, left, centerY - 7, w, 15);
		int border = this.listening || over ? Theme.GOLD : 0xFF646464;
		Theme.panel(graphics, left, centerY - 7, w, 15, 0xFF282828, border);
		graphics.fill(left + 1, centerY + 6, left + w - 1, centerY + 7, 0xFF141414);
		KeyMapping conflict = this.listening ? null : this.conflict();
		int color = this.listening ? Theme.GOLD : conflict != null ? Theme.RED_TEXT : Theme.WHITE;
		graphics.text(font, this.keyText(), left + 5, centerY - 3, color, false);
		if (conflict != null && over) {
			graphics.setTooltipForNextFrame(font, Component.literal("Auch belegt: ").append(Component.translatable(conflict.getName()))
				.append(" (" + conflict.getCategory().label().getString() + ")"),
				mouseX, mouseY);
		}
	}

	@Override
	protected boolean clickControl(double mouseX, double mouseY, int button) {
		if (button != 0 || this.mapping.get() == null) {
			return false;
		}
		this.listening = !this.listening;
		return true;
	}

	@Override
	public boolean capturing() {
		return this.listening;
	}

	@Override
	public void stopCapturing() {
		this.listening = false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.listening) {
			return false;
		}
		this.listening = false;
		if (event.key() == InputConstants.KEY_ESCAPE) {
			return true;
		}
		boolean clear = event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE;
		this.apply(clear ? InputConstants.UNKNOWN : InputConstants.getKey(event));
		return true;
	}

	@Override
	public boolean captureMouse(int button) {
		if (!this.listening || button == InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		this.listening = false;
		this.apply(InputConstants.Type.MOUSE.getOrCreate(button));
		return true;
	}

	private void apply(InputConstants.Key key) {
		KeyMapping mapping = this.mapping.get();
		mapping.setKey(key);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	@Override
	public boolean hasDefault() {
		return this.mapping.get() != null;
	}

	@Override
	public boolean isDefault() {
		KeyMapping mapping = this.mapping.get();
		return mapping == null || mapping.isDefault();
	}

	@Override
	public void reset() {
		this.apply(this.mapping.get().getDefaultKey());
	}
}
