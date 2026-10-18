package com.hugosmpenhanced.client.feature.streamer;

import java.util.ArrayList;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.Theme;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * What viewers see on stream: the auction item big in the middle, turning like a dropped item,
 * with the countdown above, the bids on the right and how to bid on the left, all see-through.
 * After the end the winner banner and confetti. While a giveaway is open, a small pill with the
 * number of participants and how to join.
 */
public final class StreamerOverlay {
	private static final int MAX_ROWS = 6;
	private static final int ROW = 13;
	private static final int WINNER_H = 44;
	private static final Confetti CONFETTI = new Confetti();

	private static long seenEndAt;
	private static String shimmerFor;
	private static long shimmerAt;
	private static long joinFlashAt;

	private StreamerOverlay() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hugosmpenhanced", "streamer_overlay"),
			(graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.hud.isHidden() || client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		// A full panel on top would only show the overlay as clutter behind it.
		boolean covered = client.gui.screen() instanceof WheelScreen || StreamerScreen.coversHud(client.gui.screen());
		if (DonationAuction.state() != DonationAuction.State.IDLE && !covered) {
			renderAuction(graphics, client.font);
		} else {
			CONFETTI.clear();
		}
		if (Giveaway.open() && !covered) {
			renderGiveawayPill(graphics, client.font);
		}
		if (ChatPatterns.learning() != ChatPatterns.Learning.NONE && !(client.gui.screen() instanceof StreamerScreen)) {
			renderLearningPill(graphics, client.font);
		}
	}

	// ------------------------------------------------------------------ auction

	private static void renderAuction(GuiGraphicsExtractor graphics, Font font) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		long now = System.currentTimeMillis();
		boolean ended = DonationAuction.state() == DonationAuction.State.ENDED;
		float appear = StreamerUi.easeOut(StreamerUi.progress(DonationAuction.startedAt(), 600));

		// --- top: title, countdown, bar
		int top = 5;
		String label = DonationAuction.demo() ? "SPENDEN-AUKTION  ·  TESTLAUF" : "SPENDEN-AUKTION";
		StreamerUi.centered(graphics, font, label, width / 2f, top - (1 - appear) * 20, 1f, StreamerUi.alpha(Theme.GOLD, appear), true);
		float timerScale = height >= 300 ? 3f : 2.25f;
		int timerY = top + 11;
		if (!ended) {
			long left = Math.max(0, DonationAuction.endsAt() - now);
			String time = clock(left);
			boolean urgent = left <= 10_000;
			float pulse = urgent ? 1 + 0.08f * (float) Math.max(0, Math.sin(now / 1000.0 * Math.PI * 2)) : 1;
			int color = urgent ? StreamerUi.mix(Theme.WHITE, 0xFFFF5050, 0.5f + 0.5f * (float) Math.sin(now / 160.0)) : Theme.WHITE;
			float s = timerScale * pulse;
			StreamerUi.centered(graphics, font, time, width / 2f, timerY - (s - timerScale) * 4, s, StreamerUi.alpha(color, appear), true);
			int sidePanel = Mth.clamp((width - 120) / 2 - 8, 110, 160) + 6;
			int barW = Math.min(150, width - 2 * sidePanel - 16);
			int barX = width / 2 - barW / 2;
			int barY = timerY + (int) (timerScale * 9) + 2;
			graphics.fill(barX, barY, barX + barW, barY + 3, 0x50FFFFFF);
			float frac = Mth.clamp(left / (float) DonationAuction.durationMs(), 0f, 1f);
			graphics.fill(barX, barY, barX + Math.round(barW * frac), barY + 3, urgent ? 0xFFFF6464 : Theme.GOLD);
			for (DonationAuction.Event event : DonationAuction.events()) {
				if (event.kind() == DonationAuction.Event.Kind.EXTENDED && now - event.at() < 1800) {
					float t = (now - event.at()) / 1800f;
					String plus = "+" + event.amount() + " s";
					StreamerUi.text(graphics, font, plus, width / 2f + font.width(time) * timerScale / 2 + 6, timerY + 4 - t * 10, 1.25f,
						StreamerUi.alpha(StreamerUi.GREEN, 1 - t), true);
				}
			}
		} else {
			if (seenEndAt != DonationAuction.endedAt()) {
				seenEndAt = DonationAuction.endedAt();
				if (DonationAuction.winner() != null) {
					CONFETTI.burst(width / 2f, height * 0.42f, 140, 230);
				}
			}
			float pop = StreamerUi.easeBack(StreamerUi.progress(DonationAuction.endedAt(), 450));
			String text = DonationAuction.winner() != null ? "VERKAUFT" : DonationAuction.cancelled() ? "ABGEBROCHEN" : "KEINE GEBOTE";
			int color = DonationAuction.winner() != null ? StreamerUi.GREEN : StreamerUi.RED_TEXT;
			float s = timerScale * 0.8f * pop;
			StreamerUi.centered(graphics, font, text, width / 2f, timerY + (timerScale * 0.8f - s) * 4, Math.max(0.01f, s), color, true);
		}

		// --- middle: item, name and (after the end) the winner, stacked between the timer and the hotbar
		ItemStack stack = DonationAuction.item();
		int centerX = width / 2;
		int topEnd = timerY + (int) (timerScale * 9) + 10;
		// Above the hotbar, or above the streamer's control bar while it is open.
		int barTop = StreamerScreen.liveBarTop(Minecraft.getInstance().gui.screen());
		int bottom = barTop >= 0 ? Math.min(height - 46, barTop - 6) : height - 46;
		float nameScale = width >= 420 && height >= 260 ? 1.5f : 1.25f;
		String enchants = enchantLine(stack, font, width - 40);
		int nameH = Math.round(9 * nameScale) + (enchants.isEmpty() ? 0 : 12) + 6;
		float endT = ended && DonationAuction.winner() != null ? StreamerUi.easeOut(StreamerUi.progress(DonationAuction.endedAt(), 600)) : 0;
		int bannerH = Math.round(WINNER_H * endT);
		int room = bottom - topEnd - nameH - bannerH;
		int itemSize = Mth.clamp(Math.min((int) (height * 0.40f), room - 8), 32, 180);
		int spare = Math.max(0, room - itemSize - 8);
		int centerY = topEnd + spare / 3 + 4 + itemSize / 2;
		float pop = StreamerUi.easeBack(StreamerUi.progress(DonationAuction.startedAt(), 700));
		float bob = (float) Math.sin(now / 1000.0 * Math.PI * 0.8) * itemSize * 0.035f;
		StreamerUi.glow(graphics, centerX, centerY, (int) (itemSize * 0.75f), (int) (itemSize * 0.75f), 0x40FFC850);
		StreamerUi.glow(graphics, centerX, centerY + itemSize / 2 + 2, itemSize / 3, Math.max(3, itemSize / 14), 0x90000000);
		if (!stack.isEmpty() && pop > 0.02f) {
			int size = Math.max(8, Math.round(itemSize * pop));
			float yaw = (now % 4000L) / 4000f * 360f;
			graphics.guiRenderState.addPicturesInPictureState(
				ItemSpinRenderState.of(stack, yaw, 0, centerX, Math.round(centerY + bob), size, graphics.scissorStack.peek()));
		}
		int nameY = centerY + itemSize / 2 + 6;
		Component name = displayName(stack);
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - font.width(name) * nameScale / 2f, nameY);
		graphics.pose().scale(nameScale, nameScale);
		graphics.text(font, name, 0, 0, StreamerUi.alpha(Theme.WHITE, appear), true);
		graphics.pose().popMatrix();
		if (!enchants.isEmpty()) {
			StreamerUi.centered(graphics, font, enchants, centerX, nameY + 9 * nameScale + 3, 1f, StreamerUi.alpha(StreamerUi.LAVENDER, appear), true);
		}
		int bannerY = nameY + nameH;

		// --- right: bids
		int listW = Mth.clamp((width - 120) / 2 - 8, 110, 160);
		int listX = width - listW - 6 + Math.round((1 - appear) * (listW + 10));
		renderBids(graphics, font, listX, 34, listW, ended, now);

		// --- left: how to bid
		if (!ended && width >= 380) {
			int panelW = Math.min(150, listW);
			int x = 6 - Math.round((1 - appear) * (panelW + 10));
			renderHowTo(graphics, font, x, 34, panelW);
		}

		if (ended) {
			renderWinner(graphics, font, width, bannerY);
		}
		CONFETTI.render(graphics);
	}

	private static void renderBids(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, boolean ended, long now) {
		List<DonationAuction.Bidder> ranking = DonationAuction.ranking();
		int rows = Math.min(MAX_ROWS, ranking.size());
		int h = 20 + Math.max(1, rows) * ROW + 4 + (ranking.size() > MAX_ROWS ? 10 : 0) + (ended ? 0 : 12);
		StreamerUi.glass(graphics, x, y, w, h);
		graphics.text(font, "GEBOTE", x + 7, y + 6, 0xFFC8C8C8, true);
		String count = ranking.size() == 1 ? "1 Bieter" : ranking.size() + " Bieter";
		graphics.text(font, count, x + w - 7 - font.width(count), y + 6, 0xFFA0A0A0, true);
		int listTop = y + 18;
		if (ranking.isEmpty()) {
			String wait = ended ? "Niemand hat geboten" : "Warte auf das erste Gebot…";
			float blink = ended ? 1 : 0.55f + 0.45f * (float) Math.sin(now / 300.0);
			graphics.text(font, wait, x + 7, listTop + 3, StreamerUi.alpha(0xFFB4B4B4, blink), true);
		}
		String leader = DonationAuction.leader();
		if (leader != null && !leader.equals(shimmerFor)) {
			shimmerFor = leader;
			shimmerAt = now;
		}
		graphics.enableScissor(x + 1, listTop - 1, x + w - 1, listTop + rows * ROW + 1);
		for (int i = 0; i < ranking.size() && i < MAX_ROWS + 1; i++) {
			DonationAuction.Bidder bidder = ranking.get(i);
			// Rows glide to their new place, amounts count up.
			if (bidder.shownRow < 0) {
				bidder.shownRow = i + 0.6f;
			}
			bidder.shownRow += (i - bidder.shownRow) * 0.18f;
			bidder.shownTotal += (bidder.total - bidder.shownTotal) * 0.15f;
			if (Math.abs(bidder.total - bidder.shownTotal) < Math.max(1, bidder.total * 0.002f)) {
				bidder.shownTotal = bidder.total;
			}
			if (i >= MAX_ROWS) {
				continue;
			}
			int rowY = Math.round(listTop + bidder.shownRow * ROW);
			boolean first = bidder.name.equalsIgnoreCase(leader == null ? "" : leader);
			float fresh = 1 - Mth.clamp((now - bidder.lastAt) / 2500f, 0f, 1f);
			if (first) {
				Theme.panel(graphics, x + 3, rowY - 1, w - 6, ROW, ended ? 0x50FFC83D : 0x38FFC83D, 0xC8FFC83D);
				float sweep = (now - shimmerAt) / 900f;
				if (sweep < 1) {
					int sx = x + 3 + Math.round((w - 6) * sweep);
					graphics.fill(Math.max(x + 4, sx - 10), rowY, Math.min(x + w - 4, sx), rowY + ROW - 2, 0x50FFFFFF);
				}
			} else if (fresh > 0) {
				graphics.fill(x + 3, rowY - 1, x + w - 3, rowY + ROW - 1, StreamerUi.alpha(0x6028A03C, fresh));
			}
			int textY = rowY + 2;
			boolean numbers = w >= 150;
			int faceX = numbers ? x + 19 : x + 7;
			if (numbers) {
				graphics.text(font, (i + 1) + ".", x + 7, textY, first ? Theme.GOLD : 0xFFB4B4B4, true);
			}
			StreamerUi.face(graphics, bidder.name, faceX, rowY + 1, 9);
			int amountColor = first ? Theme.GOLD : Theme.TEXT;
			String amount = Money.format(Math.round(bidder.shownTotal));
			int amountX = x + w - 7 - font.width(amount);
			int nameX = faceX + 13;
			String shownName = font.plainSubstrByWidth(bidder.name, amountX - nameX - 4);
			graphics.text(font, shownName, nameX, textY, Theme.WHITE, true);
			graphics.text(font, amount, amountX, textY, amountColor, true);
			if (fresh > 0 && !ended) {
				String plus = "+" + Money.format(bidder.lastAmount);
				int px = amountX - 6 - font.width(plus);
				if (px > nameX + font.width(shownName) + 2) {
					Theme.panel(graphics, px - 3, rowY, font.width(plus) + 6, ROW - 2, StreamerUi.alpha(StreamerUi.GREEN_FILL, fresh), 0);
					graphics.text(font, plus, px, textY, StreamerUi.alpha(Theme.WHITE, Math.max(0.1f, fresh)), false);
				}
			}
		}
		graphics.disableScissor();
		int footY = listTop + Math.max(1, rows) * ROW + 3;
		if (ranking.size() > MAX_ROWS) {
			graphics.text(font, "+ " + (ranking.size() - MAX_ROWS) + " weitere", x + 7, footY, 0xFF8C8C8C, true);
			footY += 10;
		}
		if (!ended) {
			String next = "Nächstes Gebot ab " + Money.format(DonationAuction.nextMinimum());
			if (font.width(next) > w - 14) {
				next = "Ab " + Money.format(DonationAuction.nextMinimum()) + " vorn";
			}
			graphics.text(font, next, x + 7, footY, Theme.GOLD, true);
		}
		// A payment that was not enough to lead.
		for (DonationAuction.Event event : DonationAuction.events()) {
			if (event.kind() == DonationAuction.Event.Kind.TOO_LOW && now - event.at() < 3500 && !ended) {
				float t = 1 - (now - event.at()) / 3500f;
				String text = event.name() + " fehlen " + Money.format(event.amount());
				int boxY = y + h + 3;
				Theme.panel(graphics, x, boxY, w, 13, StreamerUi.alpha(StreamerUi.GLASS, t), 0);
				graphics.text(font, font.plainSubstrByWidth(text, w - 10), x + 5, boxY + 3, StreamerUi.alpha(0xFFE6B450, t), true);
			}
		}
	}

	private static void renderHowTo(GuiGraphicsExtractor graphics, Font font, int x, int y, int w) {
		String self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getGameProfile().name() : "Name";
		int h = 54;
		StreamerUi.glass(graphics, x, y, w, h);
		graphics.text(font, "SO BIETEST DU", x + 7, y + 6, 0xFFC8C8C8, true);
		String command = "/pay " + self + " <Betrag>";
		float scale = font.width(command) * 1.25f <= w - 14 ? 1.25f : 1f;
		StreamerUi.text(graphics, font, command, x + 7, y + 18, scale, Theme.WHITE, true);
		graphics.text(font, "Startgebot " + Money.format(DonationAuction.startBid()), x + 7, y + 31, Theme.GOLD, true);
		String rule = ModConfigManager.CONFIG.streamerExtendLateBids
			? "Spätes Gebot: +" + ModConfigManager.CONFIG.streamerExtendSeconds + " s" : "Höchste Summe gewinnt";
		graphics.text(font, rule, x + 7, y + 42, 0xFFB4B4B4, true);
	}

	private static void renderWinner(GuiGraphicsExtractor graphics, Font font, int width, int top) {
		String winner = DonationAuction.winner();
		if (winner == null) {
			return;
		}
		float slide = StreamerUi.easeOut(StreamerUi.progress(DonationAuction.endedAt() + 250, 600));
		int w = Math.min(260, width - 20);
		int h = WINNER_H - 4;
		int x = width / 2 - w / 2;
		int y = top + Math.round((1 - slide) * 30);
		int fade = StreamerUi.alpha(0xFFFFFFFF, slide);
		if (slide < 0.02f) {
			return;
		}
		StreamerUi.glass(graphics, x, y, w, h, StreamerUi.alpha(Theme.GOLD, slide));
		StreamerUi.face(graphics, winner, x + 7, y + 7, h - 14);
		graphics.text(font, "GEWONNEN", x + h, y + 6, StreamerUi.alpha(Theme.GOLD, slide), true);
		String amount = "mit " + Money.format(DonationAuction.total(winner));
		graphics.text(font, amount, x + w - 7 - font.width(amount), y + 6, StreamerUi.alpha(Theme.GOLD, slide), true);
		StreamerUi.text(graphics, font, font.plainSubstrByWidth(winner, (int) ((w - h - 8) / 1.5f)), x + h, y + 17, 1.5f, fade, true);
	}

	// ------------------------------------------------------------------ giveaway and learning pills

	private static void renderGiveawayPill(GuiGraphicsExtractor graphics, Font font) {
		long now = System.currentTimeMillis();
		var config = ModConfigManager.CONFIG;
		String self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getGameProfile().name() : "";
		List<String> ways = new ArrayList<>();
		if (config.streamerIngameJoin) {
			ways.add("/msg " + self + " " + config.streamerIngameJoinWord);
		}
		if (config.streamerTwitchJoin && !TwitchChat.normalize(config.streamerTwitchChannel).isEmpty()) {
			ways.add(config.streamerTwitchJoinWord + " im Twitch-Chat");
		}
		String head = "GIVEAWAY";
		String prize = Giveaway.prizeName();
		String count = Giveaway.count() == 1 ? "1 Teilnehmer" : Giveaway.count() + " Teilnehmer";
		String how = String.join("  ·  ", ways);
		int w = Math.max(font.width(head + "  " + prize) + 30, Math.max(font.width(count) + 30, font.width(how) + 14));
		boolean auctionShown = DonationAuction.state() != DonationAuction.State.IDLE;
		// Beside an auction it gets the side column, the item in the middle stays free.
		w = Math.min(w, auctionShown ? Mth.clamp((graphics.guiWidth() - 120) / 2 - 8, 110, 160) : graphics.guiWidth() / 2);
		// During an auction the left column holds it, under "how to bid" when that is shown, so it
		// never reaches down into the chat or the control bar.
		boolean howToShown = auctionShown && DonationAuction.state() != DonationAuction.State.ENDED && graphics.guiWidth() >= 380;
		int x = auctionShown ? 6 : graphics.guiWidth() - w - 6;
		int y = auctionShown ? (howToShown ? 34 + 54 + 6 : 34) : 6;
		if (Giveaway.lastJoinAt() != joinFlashAt && now - Giveaway.lastJoinAt() < 100) {
			joinFlashAt = Giveaway.lastJoinAt();
		}
		float flash = 1 - Mth.clamp((now - Giveaway.lastJoinAt()) / 1200f, 0f, 1f);
		StreamerUi.glass(graphics, x, y, w, 36, StreamerUi.mix(StreamerUi.GLASS_EDGE, Theme.GOLD, flash));
		graphics.text(font, head, x + 7, y + 6, Theme.GOLD, true);
		if (!prize.isEmpty()) {
			graphics.text(font, font.plainSubstrByWidth(prize, w - 30 - font.width(head)), x + 12 + font.width(head), y + 6, Theme.WHITE, true);
		}
		if (!Giveaway.prize().isEmpty()) {
			graphics.item(Giveaway.prize(), x + w - 21, y + 3);
		}
		float bump = 1 + 0.25f * flash;
		StreamerUi.text(graphics, font, count, x + 7, y + 16 - (bump - 1) * 4, bump, StreamerUi.mix(Theme.WHITE, StreamerUi.GREEN, flash), true);
		graphics.text(font, font.plainSubstrByWidth(how, w - 14), x + 7, y + 26, 0xFFA0A0A0, true);
		// The newest names drop in under the pill.
		List<Giveaway.Participant> list = Giveaway.participants();
		int shown = 0;
		for (int i = list.size() - 1; i >= 0 && shown < 3; i--, shown++) {
			Giveaway.Participant p = list.get(i);
			float age = (now - p.joinedAt()) / 1000f;
			if (age > 6) {
				break;
			}
			float alpha = Mth.clamp(Math.min(age * 4, (6 - age) * 2), 0f, 1f);
			int rowY = y + 39 + shown * 12 - Math.round((1 - StreamerUi.easeOut(age * 3)) * 8);
			Theme.panel(graphics, x, rowY, w, 11, StreamerUi.alpha(StreamerUi.GLASS, alpha), 0);
			if (p.source() == Giveaway.Source.TWITCH) {
				StreamerUi.twitchIcon(graphics, x + 4, rowY + 1, StreamerUi.alpha(StreamerUi.TWITCH, alpha));
			} else {
				StreamerUi.face(graphics, p.name(), x + 4, rowY + 1, 9);
			}
			graphics.text(font, p.name() + " ist dabei", x + 16, rowY + 2, StreamerUi.alpha(Theme.WHITE, alpha), true);
		}
	}

	private static void renderLearningPill(GuiGraphicsExtractor graphics, Font font) {
		String text = ChatPatterns.learning() == ChatPatterns.Learning.PAYMENT
			? "Lerne Zahlungs-Nachricht: lass dir von jemandem $1 schicken"
			: "Lerne /msg-Nachricht: lass dir \"" + ModConfigManager.CONFIG.streamerIngameJoinWord + "\" schreiben";
		int w = font.width(text) + 22;
		int x = graphics.guiWidth() / 2 - w / 2;
		int y = graphics.guiHeight() - 80;
		StreamerUi.glass(graphics, x, y, w, 15, Theme.GOLD);
		float blink = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
		graphics.fill(x + 6, y + 5, x + 10, y + 9, StreamerUi.alpha(Theme.GOLD, blink));
		graphics.text(font, text, x + 15, y + 4, Theme.WHITE, true);
	}

	// ------------------------------------------------------------------ helpers

	static String clock(long ms) {
		long seconds = (ms + 999) / 1000;
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}

	private static Component displayName(ItemStack stack) {
		String title = DonationAuction.title();
		if (stack.isEmpty() || title.equals(stack.getHoverName().getString())) {
			return stack.isEmpty() ? Component.literal(title) : stack.getStyledHoverName();
		}
		return Component.literal(title).withStyle(stack.getRarity().color());
	}

	static String enchantLine(ItemStack stack, Font font, int maxWidth) {
		List<String> names = new ArrayList<>();
		addEnchants(names, stack.getEnchantments());
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored != null) {
			addEnchants(names, stored);
		}
		String line = String.join("  ·  ", names);
		return font.width(line) > maxWidth ? font.plainSubstrByWidth(line, maxWidth - font.width("…")) + "…" : line;
	}

	private static void addEnchants(List<String> names, ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
	}
}
