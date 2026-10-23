package com.hugosmpenhanced.client.feature.messages;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.hud.HudElement;
import com.hugosmpenhanced.client.hud.HudPos;
import com.hugosmpenhanced.client.hud.HudText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The second chat window on the HUD. Placed and scaled in the HUD editor. */
public final class SideChatHud implements HudElement {
	public static final SideChatHud INSTANCE = new SideChatHud();
	private static final int BG = 0xB00E0E0E;
	private static final int BORDER = 0xFF464646;
	private static final int LINE = 10;
	private static final int HEADER = 12;

	private SideChatHud() {
	}

	@Override
	public String id() {
		return "sidechat";
	}

	@Override
	public String label() {
		return "Zweiter Chat";
	}

	@Override
	public boolean enabled() {
		return ModConfigManager.CONFIG.sideChatEnabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		ModConfigManager.CONFIG.sideChatEnabled = enabled;
	}

	@Override
	public HudPos defaultPos() {
		return new HudPos(0, 0.3);
	}

	private static boolean chatOpen() {
		return Minecraft.getInstance().screen instanceof ChatScreen;
	}

	@Override
	public boolean visible() {
		return !(Minecraft.getInstance().screen instanceof MessagesScreen) && !SideChat.recent(1, chatOpen()).isEmpty();
	}

	private static int width() {
		return Math.max(120, Math.min(320, ModConfigManager.CONFIG.sideChatWidth));
	}

	private static List<SideChat.Entry> sample() {
		long now = System.currentTimeMillis();
		return List.of(
			new SideChat.Entry(SideChat.Channel.SERVER, now, "Der Server startet in 5 Minuten neu."),
			new SideChat.Entry(SideChat.Channel.PRIVATE, now, "Hugo: Hast du kurz Zeit für einen Handel?"),
			new SideChat.Entry(SideChat.Channel.PRIVATE, now, "Du → Hugo: Klar, TPA kommt."));
	}

	/** The wrapped lines to draw, newest last, cut to the configured line count. */
	private List<FormattedCharSequence> lines(Font font, boolean preview) {
		List<SideChat.Entry> entries = preview ? sample() : SideChat.recent(16, chatOpen());
		int inner = width() - 10;
		int max = Math.max(3, Math.min(12, ModConfigManager.CONFIG.sideChatLines));
		// Whole entries from the newest back; only a single entry longer than the box is cut.
		List<List<FormattedCharSequence>> blocks = new ArrayList<>();
		int total = 0;
		for (int i = entries.size() - 1; i >= 0; i--) {
			SideChat.Entry entry = entries.get(i);
			Component tag = Component.literal(entry.channel().label + " ").withColor(entry.channel().color);
			Component text = Component.literal(entry.text()).withColor(HudText.TEXT);
			List<FormattedCharSequence> block = font.split(Component.empty().append(tag).append(text), inner);
			if (total + block.size() > max) {
				if (blocks.isEmpty()) {
					blocks.add(new ArrayList<>(block.subList(0, max)));
				}
				break;
			}
			blocks.add(0, block);
			total += block.size();
		}
		List<FormattedCharSequence> all = new ArrayList<>();
		blocks.forEach(all::addAll);
		return all;
	}

	private String header() {
		int unread = MessageLog.unreadTotal();
		return unread > 0 ? "Nachrichten · " + unread + " ungelesen" : "Nachrichten";
	}

	@Override
	public int width(Font font, boolean preview) {
		return width();
	}

	@Override
	public int height(Font font, boolean preview) {
		return HEADER + this.lines(font, preview).size() * LINE + 4;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, boolean preview) {
		List<FormattedCharSequence> lines = this.lines(font, preview);
		int width = width();
		int height = HEADER + lines.size() * LINE + 4;
		graphics.fill(1, 0, width - 1, height, BG);
		graphics.fill(0, 1, width, height - 1, BG);
		graphics.fill(1, 0, width - 1, 1, BORDER);
		graphics.fill(1, height - 1, width - 1, height, BORDER);
		graphics.fill(0, 1, 1, height - 1, BORDER);
		graphics.fill(width - 1, 1, width, height - 1, BORDER);
		boolean unread = MessageLog.unreadTotal() > 0;
		graphics.drawString(font, preview ? "Nachrichten" : this.header(), 5, 3, unread && !preview ? HudText.GOLD : HudText.MUTED, false);
		graphics.fill(4, HEADER - 1, width - 4, HEADER, 0x30FFFFFF);
		int y = HEADER + 2;
		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, 5, y, HudText.TEXT, true);
			y += LINE;
		}
	}
}
