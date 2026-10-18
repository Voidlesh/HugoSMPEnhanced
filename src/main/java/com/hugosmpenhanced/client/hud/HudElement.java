package com.hugosmpenhanced.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One block of information on the HUD (FPS, pinned notes, money tracker ...). Elements draw at
 * 0,0 in their own coordinates; {@link HudManager} moves and scales them to where the player put
 * them in the {@link HudEditorScreen}.
 */
public interface HudElement {
	/** Key for the saved position, never changes. */
	String id();

	/** Name in the HUD editor. */
	String label();

	/** Turned on in the settings. */
	boolean enabled();

	void setEnabled(boolean enabled);

	/** Whether it has something to show right now (the sell status only while selling, ...). */
	boolean visible();

	/** Size of what {@link #render} draws; with {@code preview} the example content in the editor. */
	int width(Font font, boolean preview);

	int height(Font font, boolean preview);

	/** Draws at 0,0. {@code preview}: example content for the editor when there is nothing real yet. */
	void render(GuiGraphicsExtractor graphics, Font font, boolean preview);

	/** Where it sits until the player moves it. */
	HudPos defaultPos();
}
