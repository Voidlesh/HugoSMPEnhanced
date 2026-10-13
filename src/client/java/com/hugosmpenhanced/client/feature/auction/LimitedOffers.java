package com.hugosmpenhanced.client.feature.auction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Watches the open menu (auction house, orders, any chest-like menu on HugoSMP): remembers the
 * custom items in it and, for limited items with a price, works out the price per hour of time
 * left. The cheapest per hour of each kind is framed in gold, the tooltip shows the numbers and a
 * line under the menu sums it up.
 */
public final class LimitedOffers {
	private static final int GOLD = 0xFFFFC83A;
	private static final int GOLD_FILL = 0x50FFC83A;

	/** Lets tests run it outside HugoSMP. */
	public static boolean forceActive;

	/** When each stack runs out, fixed the first time it is seen: a countdown in the lore does not tick. */
	private static final Map<ItemStack, Optional<LimitedItems.Expiry>> EXPIRY = new WeakHashMap<>();

	private static AbstractContainerScreen<?> screen;
	private static List<ItemStack> lastContents = new ArrayList<>();
	private static final Map<ItemStack, Offer> OFFERS = new IdentityHashMap<>();
	private static Summary summary;

	/** A limited item offered for a price, and how it compares with the others of its kind on screen. */
	public record Offer(String group, double perItem, long expiresAt, double perHour, boolean best, int rank, int ofKind,
		double bestPerHour) {
	}

	/** The best offer of each kind on screen, cheapest per hour first. */
	private record Summary(int offers, List<Offer> bestPerKind, List<String> names) {
	}

	private LimitedOffers() {
	}

	public static boolean active() {
		return forceActive || ServerCheck.isHugoSmp();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, opened, width, height) -> {
			if (!(opened instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			ScreenEvents.beforeRender(container).register((s, graphics, mouseX, mouseY, delta) -> update(container));
			ScreenEvents.afterRender(container).register((s, graphics, mouseX, mouseY, delta) -> renderSummary(container, graphics));
			ScreenEvents.remove(container).register(s -> {
				if (screen == container) {
					screen = null;
					OFFERS.clear();
					lastContents.clear();
					summary = null;
				}
				CustomItems.saveIfDirty();
				AuctionPriceMemory.saveIfDirty();
			});
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addTooltip(stack, lines));
	}

	/** Re-reads the menu when anything in it changed (a new page, a bought item...). */
	public static void update(AbstractContainerScreen<?> container) {
		if (!active()) {
			return;
		}
		List<Slot> slots = container.getMenu().slots;
		boolean changed = screen != container || slots.size() != lastContents.size();
		for (int i = 0; !changed && i < slots.size(); i++) {
			changed = slots.get(i).getItem() != lastContents.get(i);
		}
		if (!changed) {
			return;
		}
		screen = container;
		lastContents = new ArrayList<>(slots.size());
		for (Slot slot : slots) {
			lastContents.add(slot.getItem());
		}
		scan(slots);
	}

	private static void scan(List<Slot> slots) {
		OFFERS.clear();
		summary = null;
		long now = System.currentTimeMillis();
		long lifetime = CustomItems.lifetimeMs();
		Map<String, List<ItemStack>> byKind = new HashMap<>();
		Map<ItemStack, Offer> raw = new IdentityHashMap<>();
		for (Slot slot : slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			if (slot.container instanceof Inventory) {
				CustomItems.learn(stack, false);
				continue;
			}
			Optional<AuctionLore.Price> price = priceOf(stack);
			// Only offers: menu buttons often have their own model too, but never a price.
			if (price.isEmpty()) {
				continue;
			}
			String custom = CustomItems.learn(stack, true);
			double perItem = price.get().perItem() ? price.get().amount() : price.get().amount() / Math.max(1, stack.getCount());
			// Every offer on the page counts for the search's prices, not only the hovered ones.
			if (ModConfigManager.CONFIG.auctionPriceMemoryEnabled) {
				AuctionPriceMemory.record(stack, perItem, AuctionMarket.current());
			}
			Optional<LimitedItems.Expiry> expiry = expiryOf(stack, now, lifetime);
			if (expiry.isEmpty()) {
				continue;
			}
			long left = expiry.get().remainingMs(now);
			// An expired item is worth nothing per hour; it never wins.
			double perHour = left <= 0 ? Double.POSITIVE_INFINITY : perItem / Math.max(left / 3_600_000.0, 1 / 60.0);
			String group = custom != null ? custom : kindOf(stack);
			byKind.computeIfAbsent(group, g -> new ArrayList<>()).add(stack);
			raw.put(stack, new Offer(group, perItem, expiry.get().expiresAt(), perHour, false, 0, 0, perHour));
		}
		List<ItemStack> bests = new ArrayList<>();
		for (List<ItemStack> kind : byKind.values()) {
			kind.sort((a, b) -> Double.compare(raw.get(a).perHour(), raw.get(b).perHour()));
			Offer first = raw.get(kind.get(0));
			boolean anyValid = Double.isFinite(first.perHour());
			for (int i = 0; i < kind.size(); i++) {
				ItemStack stack = kind.get(i);
				Offer offer = raw.get(stack);
				Offer ranked = new Offer(offer.group(), offer.perItem(), offer.expiresAt(), offer.perHour(), i == 0 && anyValid, i + 1,
					kind.size(), first.perHour());
				OFFERS.put(stack, ranked);
			}
			if (anyValid) {
				AuctionPriceMemory.recordPerHour(kind.get(0), first.perHour(), AuctionMarket.current());
				bests.add(kind.get(0));
			}
		}
		if (!bests.isEmpty()) {
			bests.sort((a, b) -> Double.compare(OFFERS.get(a).perHour(), OFFERS.get(b).perHour()));
			summary = new Summary(OFFERS.size(), bests.stream().map(OFFERS::get).toList(),
				bests.stream().map(stack -> stack.getHoverName().getString()).toList());
		}
	}

	/** Same kind for the comparison: same item and same name. */
	private static String kindOf(ItemStack stack) {
		return stack.getItem() + "|" + AuctionSearch.fold(stack.getHoverName().getString());
	}

	static Optional<AuctionLore.Price> priceOf(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return Optional.empty();
		}
		for (Component line : lore.lines()) {
			Optional<AuctionLore.Price> price = AuctionLore.parse(line.getString());
			if (price.isPresent()) {
				return price;
			}
		}
		return Optional.empty();
	}

	static Optional<LimitedItems.Expiry> expiryOf(ItemStack stack, long now, long lifetime) {
		return EXPIRY.computeIfAbsent(stack, s -> LimitedItems.expiry(s, now, lifetime));
	}

	/** The offer for a stack in the open menu, or null. */
	public static Offer offer(ItemStack stack) {
		return OFFERS.get(stack);
	}

	/** Gold background and frame behind the best offers; called for every slot before its item is drawn. */
	public static void renderSlotBackground(GuiGraphics graphics, Slot slot) {
		if (!ModConfigManager.CONFIG.limitedItemsHighlightBest || OFFERS.isEmpty()) {
			return;
		}
		Offer offer = OFFERS.get(slot.getItem());
		if (offer == null || !offer.best()) {
			return;
		}
		graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GOLD_FILL);
		graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, GOLD);
	}

	/**
	 * One line under the menu (above it if there is no room below): the best offer per hour, or the
	 * best of each kind when several limited items are on the page. Left out on screens too small for it.
	 */
	private static void renderSummary(AbstractContainerScreen<?> container, GuiGraphics graphics) {
		if (summary == null || screen != container || !ModConfigManager.CONFIG.limitedItemsPerHour) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) container;
		int maxWidth = container.width - 16;
		String text = summaryText(font, maxWidth - 8);
		int width = font.width(text) + 8;
		int x = Math.max(4, accessor.hugosmpenhanced$getLeftPos() + (accessor.hugosmpenhanced$getImageWidth() - width) / 2);
		int y = accessor.hugosmpenhanced$getTopPos() + accessor.hugosmpenhanced$getImageHeight() + 3;
		if (y + 12 > container.height) {
			y = accessor.hugosmpenhanced$getTopPos() - 15;
			if (y < 0) {
				return;
			}
		}
		graphics.fill(x, y, x + width, y + 12, 0xE0101010);
		graphics.renderOutline(x, y, width, 12, 0xFF7A6020);
		graphics.drawString(font, text, x + 4, y + 2, 0xFFFFD866);
	}

	private static String summaryText(Font font, int maxWidth) {
		List<Offer> bests = summary.bestPerKind();
		long now = System.currentTimeMillis();
		if (bests.size() == 1) {
			Offer best = bests.get(0);
			String text = "⌛ Bestes Angebot: " + perHour(best.perHour()) + " · noch " + LimitedItems.formatRemaining(best.expiresAt() - now);
			if (summary.offers() > 1 && font.width(text + " · von " + summary.offers()) <= maxWidth) {
				text += " · von " + summary.offers();
			}
			return text;
		}
		StringBuilder text = new StringBuilder("⌛ Bestes je Sorte:");
		for (int i = 0; i < bests.size(); i++) {
			String part = " " + summary.names().get(i) + " " + perHour(bests.get(i).perHour()) + (i < bests.size() - 1 ? " ·" : "");
			String rest = " +" + (bests.size() - i) + " mehr";
			if (font.width(text + part) > maxWidth - (i < bests.size() - 1 ? font.width(rest) : 0)) {
				return text + rest;
			}
			text.append(part);
		}
		return text.toString();
	}

	private static void addTooltip(ItemStack stack, List<Component> lines) {
		if (!active() || !ModConfigManager.CONFIG.limitedItemsPerHour) {
			return;
		}
		Offer offer = OFFERS.get(stack);
		// A remembered item in the search is one example of its kind; its time left says nothing about the offers.
		if (offer == null && Minecraft.getInstance().screen instanceof AuctionSearchScreen) {
			return;
		}
		long now = System.currentTimeMillis();
		Optional<LimitedItems.Expiry> expiry;
		if (offer != null) {
			expiry = Optional.of(new LimitedItems.Expiry(offer.expiresAt(), ""));
		} else if (stack.has(DataComponents.LORE) || stack.has(DataComponents.CUSTOM_DATA)) {
			expiry = expiryOf(stack, now, CustomItems.lifetimeMs());
			if (expiry.isEmpty() && LimitedItems.isLimited(stack, CustomItems.lifetimeMs())) {
				lines.add(Component.literal("⌛ Limitiert, Ablauf nicht erkannt").withStyle(ChatFormatting.DARK_GRAY));
				return;
			}
		} else {
			return;
		}
		if (expiry.isEmpty()) {
			return;
		}
		long left = expiry.get().remainingMs(now);
		List<Component> added = new ArrayList<>();
		added.add(Component.literal("⌛ ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(left <= 0 ? "Abgelaufen" : "Läuft noch " + LimitedItems.formatRemaining(left))
				.withStyle(left <= 0 ? ChatFormatting.RED : ChatFormatting.AQUA)));
		if (offer != null && Double.isFinite(offer.perHour())) {
			added.add(Component.literal("Pro Stunde: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(perHour(offer.perHour())).withStyle(ChatFormatting.GOLD)));
			if (offer.ofKind() > 1) {
				if (offer.best()) {
					added.add(Component.literal("★ Günstigstes pro Stunde hier (von " + offer.ofKind() + ")").withStyle(ChatFormatting.YELLOW));
				} else {
					int more = (int) Math.round((offer.perHour() / offer.bestPerHour() - 1) * 100);
					added.add(Component.literal("Platz " + offer.rank() + " von " + offer.ofKind() + " · bestes " + perHour(offer.bestPerHour())
						+ " (+" + more + "%)").withStyle(ChatFormatting.DARK_GRAY));
				}
			}
		}
		lines.addAll(insertAt(lines), added);
	}

	/** Right after the price line (and the per item line below it), or at the end. */
	private static int insertAt(List<Component> lines) {
		for (int i = 0; i < lines.size(); i++) {
			if (AuctionLore.parse(lines.get(i).getString()).isPresent()) {
				int at = i + 1;
				if (at < lines.size() && lines.get(at).getString().startsWith("Pro Item: ")) {
					at++;
				}
				return at;
			}
		}
		return lines.size();
	}

	static String perHour(double value) {
		return AuctionSearchScreen.formatPrice(value) + "/Std";
	}
}
