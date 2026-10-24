package com.hugosmpenhanced.client.feature.aliases;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.PanelScreen;
import com.hugosmpenhanced.client.settings.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The list of chat aliases: a row per alias, a click edits it, "Neu" adds one. */
public class ChatAliasScreen extends PanelScreen {
	private static final int ROW = 18;

	public ChatAliasScreen(Screen parent) {
		super(Component.literal("Kürzel"), parent, Theme.GOLD, 380);
		this.searchable("Suchen…");
		this.footer("Neu", () -> Minecraft.getInstance().setScreen(new ChatAliasEditScreen(this, null)));
	}

	@Override
	protected String subtitle() {
		return "Tippe nur das Kürzel in den Chat, Enter sendet den Text dahinter.";
	}

	@Override
	protected String status() {
		return ModConfigManager.CONFIG.chatAliases.size() + " Kürzel";
	}

	@Override
	protected int rowHeight() {
		return ROW;
	}

	@Override
	protected String emptyText() {
		return "Noch kein Kürzel. Mit „Neu“ legst du eins an, z. B. werbung1.";
	}

	@Override
	protected List<AliasRow> rows() {
		List<AliasRow> rows = new ArrayList<>();
		for (ChatAlias alias : ModConfigManager.CONFIG.chatAliases) {
			if (alias == null) {
				continue;
			}
			if (this.query.isEmpty() || alias.name.toLowerCase(Locale.ROOT).contains(this.query)
				|| alias.text.toLowerCase(Locale.ROOT).contains(this.query)) {
				rows.add(new AliasRow(alias));
			}
		}
		return rows;
	}

	private final class AliasRow implements Row {
		private final ChatAlias alias;

		private AliasRow(ChatAlias alias) {
			this.alias = alias;
		}

		@Override
		public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, boolean hovered) {
			int textY = y + (height - 8) / 2;
			boolean usable = ChatAliases.isUsable(this.alias);
			String name = this.alias.name.isBlank() ? "(ohne Kürzel)" : this.alias.name;
			int nameWidth = Math.min(font.width(name), width / 2);
			graphics.drawString(font, font.plainSubstrByWidth(name, nameWidth), x + 6, textY,
				usable ? Theme.GOLD : Theme.RED_TEXT, false);
			int textX = x + 12 + nameWidth;
			String text = usable ? this.alias.text.trim() : "Kein Text oder ungültiges Kürzel, bitte ändern";
			int maxText = width - (textX - x) - 10;
			String shown = font.width(text) <= maxText ? text : font.plainSubstrByWidth(text, Math.max(0, maxText - font.width("…"))) + "…";
			graphics.drawString(font, shown, textX, textY, usable ? Theme.TEXT_DIM : Theme.RED_TEXT, false);
		}

		@Override
		public boolean click(int button) {
			if (button != 0) {
				return false;
			}
			Minecraft.getInstance().setScreen(new ChatAliasEditScreen(ChatAliasScreen.this, this.alias));
			return true;
		}
	}
}
