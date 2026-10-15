package com.hugosmpenhanced.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A HUD element drawn as a {@link HudText} box; subclasses only say what goes in it. */
public abstract class TextHudElement implements HudElement {
	/** The content right now, the example content with {@code preview}, or null for nothing. */
	protected abstract HudText build(boolean preview);

	@Override
	public boolean visible() {
		HudText text = this.build(false);
		return text != null && !text.isEmpty();
	}

	@Override
	public int width(Font font, boolean preview) {
		HudText text = this.build(preview);
		return text == null ? 0 : text.width(font);
	}

	@Override
	public int height(Font font, boolean preview) {
		HudText text = this.build(preview);
		return text == null ? 0 : text.height();
	}

	@Override
	public void render(GuiGraphics graphics, Font font, boolean preview) {
		HudText text = this.build(preview);
		if (text != null) {
			text.render(graphics, font);
		}
	}
}
