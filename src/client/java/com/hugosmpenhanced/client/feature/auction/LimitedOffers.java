package com.hugosmpenhanced.client.feature.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
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
 * left.
 * <p>
 * Offers are remembered per page while browsing, so the best one is found across all pages seen:
 * it is framed in gold on its own page, and on any other page the arrow towards it lights up. The
 * page comes from the title ("Seite 2/5", "(2/5)"), from an item that names it, or from counting
 * clicks on the page arrows. Browsing starts over with a new search or after a few idle minutes.
 * <p>
 * For a limited item of one's own, the tooltip suggests a price that beats the best offer per hour.
 */
public final class LimitedOffers {
	private static final int GOLD = 0xFFFFC83A;
	private static final int GOLD_FILL = 0x50FFC83A;
	private static final long BROWSE_IDLE_MS = 5 * 60_000L;
	/** A best price per hour remembered from an earlier visit counts for the sell hint this long. */
	private static final long SELL_HINT_MEMORY_MS = 12 * 3_600_000L;

	private static final Pattern PAGE_IN_TITLE = Pattern.compile("(?:seite|page|s\\.)\\s*(\\d{1,3})|(\\d{1,3})\\s*/\\s*\\d{1,3}",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern NEXT = Pattern.compile("n(ä|ae)chste|weiter|vorw(ä|ae)rts|next|→|»|>>|▶|➡", Pattern.CASE_INSENSITIVE);
	private static final Pattern PREVIOUS = Pattern.compile("vorherige|zur(ü|ue)ck|previous|prev|back|←|«|<<|◀|⬅",
		Pattern.CASE_INSENSITIVE);

	/** Buttons that change the page's contents without turning it. */
	private static final Pattern OTHER_BUTTON = Pattern.compile(
		"aktualisier|neu laden|refresh|reload|sortier|sort|filter|kategor|suche|search|schlie(ß|ss)en|close|verkaufen|sell|eigene|my ",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	/** Lets tests run it outside HugoSMP. */
	public static boolean forceActive;

	/** When each stack runs out, fixed the first time it is seen: a countdown in the lore does not tick. */
	private static final Map<ItemStack, Optional<LimitedItems.Expiry>> EXPIRY = new WeakHashMap<>();

	private static AbstractContainerScreen<?> screen;
	private static List<ItemStack> lastContents = new ArrayList<>();
	private static final Map<ItemStack, Offer> OFFERS = new IdentityHashMap<>();
	private static Summary summary;

	// Browsing across pages.
	private static String browseKey;
	private static long browseSeenAt;
	private static int page = 1;
	private static int pendingStep;
	private static long pendingAt;
	/** A click on an unnamed button in the bottom row: right half probably turns forward, left half back. */
	private static int guessedStep;
	private static int guessedSlot = -1;
	private static int learnedNextSlot = -1;
	private static int learnedPreviousSlot = -1;
	private static final Map<Integer, List<Seen>> PAGES = new HashMap<>();
	private static ItemStack nextArrow = ItemStack.EMPTY;
	private static ItemStack previousArrow = ItemStack.EMPTY;
	private static final List<Seen> BETTER_AHEAD = new ArrayList<>();
	private static final List<Seen> BETTER_BEHIND = new ArrayList<>();

	/** A limited item offered for a price, compared with the others of its kind on all pages seen. */
	public record Offer(String group, double perItem, long expiresAt, double perHour, boolean best, int rank, int ofKind,
		double bestPerHour, int bestPage) {
	}

	/** One limited offer seen while browsing. */
	public record Seen(String group, String name, double perItem, long expiresAt, int page) {
		public double perHour(long now) {
			long left = this.expiresAt - now;
			return left <= 0 ? Double.POSITIVE_INFINITY : this.perItem / Math.max(left / 3_600_000.0, 1 / 60.0);
		}
	}

	/** The best offer of each kind over all pages, cheapest per hour first. */
	private record Summary(int offers, int pages, List<Seen> bestPerKind) {
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
			ScreenMouseEvents.allowMouseClick(container).register((s, event) -> {
				rememberArrowClick(container, event.x(), event.y());
				return true;
			});
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

	/** Forgets the pages seen so far, e.g. because a new search was sent. */
	public static void newBrowse() {
		PAGES.clear();
		page = 1;
		pendingStep = 0;
		browseKey = null;
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
		scan(container.getTitle().getString(), slots);
	}

	private static void scan(String title, List<Slot> slots) {
		OFFERS.clear();
		summary = null;
		nextArrow = ItemStack.EMPTY;
		previousArrow = ItemStack.EMPTY;
		BETTER_AHEAD.clear();
		BETTER_BEHIND.clear();
		long now = System.currentTimeMillis();
		long lifetime = CustomItems.lifetimeMs();

		Map<ItemStack, Seen> onPage = new IdentityHashMap<>();
		Integer pageFromItems = null;
		int priced = 0;
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
				String name = stack.getHoverName().getString();
				if (NEXT.matcher(name).find()) {
					nextArrow = stack;
				} else if (PREVIOUS.matcher(name).find()) {
					previousArrow = stack;
				}
				if (pageFromItems == null) {
					pageFromItems = pageNamedBy(stack);
				}
				continue;
			}
			priced++;
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
			String group = custom != null ? custom : kindOf(stack);
			onPage.put(stack, new Seen(group, stack.getHoverName().getString(), perItem, expiry.get().expiresAt(), 0));
		}

		// Menus without offers (a confirmation, a chest) leave the pages seen so far alone.
		if (priced == 0) {
			return;
		}
		updatePage(title, pageFromItems, now);
		// Arrows whose names say nothing: the buttons that turned the page before.
		for (Slot slot : slots) {
			if (slot.container instanceof Inventory || slot.getItem().isEmpty() || priceOf(slot.getItem()).isPresent()) {
				continue;
			}
			if (nextArrow.isEmpty() && slot.getContainerSlot() == learnedNextSlot) {
				nextArrow = slot.getItem();
			} else if (previousArrow.isEmpty() && slot.getContainerSlot() == learnedPreviousSlot) {
				previousArrow = slot.getItem();
			}
		}
		List<Seen> thisPage = new ArrayList<>();
		Map<ItemStack, Seen> placed = new IdentityHashMap<>();
		for (Map.Entry<ItemStack, Seen> entry : onPage.entrySet()) {
			Seen seen = entry.getValue();
			Seen here = new Seen(seen.group(), seen.name(), seen.perItem(), seen.expiresAt(), page);
			thisPage.add(here);
			placed.put(entry.getKey(), here);
		}
		PAGES.put(page, thisPage);

		// Best and count per kind over every page seen.
		Map<String, Seen> best = new LinkedHashMap<>();
		Map<String, Integer> count = new HashMap<>();
		int total = 0;
		for (List<Seen> list : PAGES.values()) {
			for (Seen seen : list) {
				total++;
				count.merge(seen.group(), 1, Integer::sum);
				double perHour = seen.perHour(now);
				Seen current = best.get(seen.group());
				if (Double.isFinite(perHour) && (current == null || perHour < current.perHour(now))) {
					best.put(seen.group(), seen);
				}
			}
		}

		// Rank on this page's kinds against all pages.
		Map<String, List<ItemStack>> byKind = new HashMap<>();
		placed.forEach((stack, seen) -> byKind.computeIfAbsent(seen.group(), g -> new ArrayList<>()).add(stack));
		for (Map.Entry<String, List<ItemStack>> kind : byKind.entrySet()) {
			Seen top = best.get(kind.getKey());
			double topPerHour = top == null ? Double.POSITIVE_INFINITY : top.perHour(now);
			List<ItemStack> stacks = kind.getValue();
			stacks.sort(Comparator.comparingDouble(stack -> placed.get(stack).perHour(now)));
			for (ItemStack stack : stacks) {
				Seen seen = placed.get(stack);
				double perHour = seen.perHour(now);
				int rank = 1;
				for (List<Seen> list : PAGES.values()) {
					for (Seen other : list) {
						if (other != seen && other.group().equals(seen.group()) && other.perHour(now) < perHour) {
							rank++;
						}
					}
				}
				boolean isBest = top == seen;
				OFFERS.put(stack, new Offer(seen.group(), seen.perItem(), seen.expiresAt(), perHour, isBest, rank,
					count.getOrDefault(seen.group(), 1), topPerHour, top == null ? page : top.page()));
			}
			ItemStack cheapestHere = stacks.get(0);
			if (top != null && top.page() == page) {
				AuctionPriceMemory.recordPerHour(cheapestHere, topPerHour, AuctionMarket.current());
			}
		}

		// Arrows towards better offers on other pages.
		for (Seen top : best.values()) {
			if (top.page() > page) {
				BETTER_AHEAD.add(top);
			} else if (top.page() < page) {
				BETTER_BEHIND.add(top);
			}
		}
		BETTER_AHEAD.sort(Comparator.comparingDouble(seen -> seen.perHour(now)));
		BETTER_BEHIND.sort(Comparator.comparingDouble(seen -> seen.perHour(now)));

		if (!best.isEmpty()) {
			List<Seen> bests = new ArrayList<>(best.values());
			bests.sort(Comparator.comparingDouble(seen -> seen.perHour(now)));
			summary = new Summary(total, PAGES.size(), bests);
		}
	}

	private static void updatePage(String title, Integer pageFromItems, long now) {
		// "Auktionshaus", "Auktionshaus (2/5)" and "Auktionshaus - Seite 3" are the same pages.
		String stem = title.replaceAll("(?i)seite|page|s\\.", " ").replaceAll("[\\d/()\\[\\]#:|\\-]", " ").replaceAll("\\s+", " ").strip();
		String key = AuctionMarket.of(title) + "|" + stem;
		if (!key.equals(browseKey) || now - browseSeenAt > BROWSE_IDLE_MS) {
			PAGES.clear();
			page = 1;
			browseKey = key;
		}
		browseSeenAt = now;
		Integer fromTitle = pageIn(title);
		if (fromTitle != null) {
			page = fromTitle;
		} else if (pageFromItems != null) {
			page = pageFromItems;
		} else if (pendingStep != 0 && now - pendingAt < 5000) {
			page = Math.max(1, page + pendingStep);
		} else if (guessedStep != 0 && now - pendingAt < 5000 && (guessedStep > 0 || page > 1)) {
			page += guessedStep;
			if (guessedStep > 0) {
				learnedNextSlot = guessedSlot;
			} else {
				learnedPreviousSlot = guessedSlot;
			}
		}
		pendingStep = 0;
		guessedStep = 0;
	}

	static Integer pageIn(String text) {
		Matcher matcher = PAGE_IN_TITLE.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		int value = Integer.parseInt(number);
		return value >= 1 ? value : null;
	}

	/** A menu item like "Seite 2/5" (name or lore) tells the page. */
	private static Integer pageNamedBy(ItemStack stack) {
		String name = stack.getHoverName().getString();
		if (name.toLowerCase(Locale.ROOT).contains("seite") || name.toLowerCase(Locale.ROOT).contains("page")) {
			Integer found = pageIn(name);
			if (found != null) {
				return found;
			}
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				String text = line.getString().toLowerCase(Locale.ROOT);
				if (text.contains("seite") || text.contains("page")) {
					Integer found = pageIn(text);
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null;
	}

	private static void rememberArrowClick(AbstractContainerScreen<?> container, double mouseX, double mouseY) {
		if (container != screen) {
			return;
		}
		Slot slot = slotAt(container, mouseX, mouseY);
		if (slot == null || slot.getItem().isEmpty()) {
			return;
		}
		pendingStep = 0;
		guessedStep = 0;
		if (slot.getItem() == nextArrow) {
			pendingStep = 1;
		} else if (slot.getItem() == previousArrow) {
			pendingStep = -1;
		} else if (!(slot.container instanceof Inventory) && priceOf(slot.getItem()).isEmpty()
			&& !OTHER_BUTTON.matcher(slot.getItem().getHoverName().getString()).find()) {
			int size = slot.container.getContainerSize();
			int index = slot.getContainerSlot();
			int column = index % 9;
			if (size % 9 == 0 && index >= size - 9 && column != 4) {
				guessedStep = column > 4 ? 1 : -1;
				guessedSlot = index;
			}
		}
		pendingAt = System.currentTimeMillis();
	}

	static Slot slotAt(AbstractContainerScreen<?> container, double mouseX, double mouseY) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) container;
		double x = mouseX - accessor.hugosmpenhanced$getLeftPos();
		double y = mouseY - accessor.hugosmpenhanced$getTopPos();
		for (Slot slot : container.getMenu().slots) {
			if (slot.isActive() && x >= slot.x - 1 && x < slot.x + 17 && y >= slot.y - 1 && y < slot.y + 17) {
				return slot;
			}
		}
		return null;
	}

	/** Same kind for the comparison: same item and same name. */
	private static String kindOf(ItemStack stack) {
		return stack.getItem() + "|" + AuctionSearch.fold(stack.getHoverName().getString());
	}

	private static String groupOf(ItemStack stack) {
		String custom = CustomItems.idOf(stack, true);
		return custom != null ? custom : kindOf(stack);
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

	/** The arrow that leads to a better offer: 1 next, -1 previous, 0 none. For tests. */
	public static int arrowHint(ItemStack stack) {
		if (!stack.isEmpty() && stack == nextArrow && !BETTER_AHEAD.isEmpty()) {
			return 1;
		}
		if (!stack.isEmpty() && stack == previousArrow && !BETTER_BEHIND.isEmpty()) {
			return -1;
		}
		return 0;
	}

	public static int currentPage() {
		return page;
	}

	/**
	 * Frames drawn behind items, called for every slot before its item: gold for the best limited
	 * offer and for the arrow towards a better one on another page, light blue for marked items.
	 */
	public static void renderSlotBackground(GuiGraphics graphics, Slot slot) {
		if (!active() || slot.container instanceof Inventory) {
			return;
		}
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) {
			return;
		}
		if (ModConfigManager.CONFIG.auctionMarkHighlight && MarkedItems.isMarked(stack)) {
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, MarkedItems.FILL);
			graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, MarkedItems.COLOR);
		}
		if (!ModConfigManager.CONFIG.limitedItemsHighlightBest) {
			return;
		}
		Offer offer = OFFERS.get(stack);
		if (offer != null && offer.best()) {
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GOLD_FILL);
			graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, GOLD);
		} else if (arrowHint(stack) != 0) {
			// Pulses gently, so the arrow catches the eye without looking like an offer.
			double wave = (Math.sin(System.currentTimeMillis() / 250.0) + 1) / 2;
			int alpha = 0x40 + (int) (wave * 0x50);
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, alpha << 24 | 0xFFC83A);
			graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, GOLD);
		}
	}

	/**
	 * One line under the menu (above it if there is no room below): the best offer per hour and
	 * where it is, or the best of each kind when several limited items were seen. Left out on screens
	 * too small for it.
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
		List<Seen> bests = summary.bestPerKind();
		long now = System.currentTimeMillis();
		String pages = summary.pages() > 1 ? " · " + summary.pages() + " Seiten" : "";
		if (bests.size() == 1) {
			Seen best = bests.get(0);
			String where = best.page() == page ? "" : " auf Seite " + best.page();
			String text = "⌛ Bestes Angebot" + where + ": " + perHour(best.perHour(now)) + " · noch "
				+ LimitedItems.formatRemaining(best.expiresAt() - now);
			if (font.width(text + pages) <= maxWidth) {
				text += pages;
			}
			return text;
		}
		StringBuilder text = new StringBuilder("⌛ Bestes je Sorte:");
		for (int i = 0; i < bests.size(); i++) {
			Seen best = bests.get(i);
			String part = " " + best.name() + " " + perHour(best.perHour(now)) + (best.page() == page ? "" : " (S. " + best.page() + ")")
				+ (i < bests.size() - 1 ? " ·" : "");
			String rest = " +" + (bests.size() - i) + " mehr";
			if (font.width(text + part) > maxWidth - (i < bests.size() - 1 ? font.width(rest) : 0)) {
				return text + rest;
			}
			text.append(part);
		}
		return text.toString();
	}

	private static void addTooltip(ItemStack stack, List<Component> lines) {
		if (!active()) {
			return;
		}
		int arrow = arrowHint(stack);
		if (arrow != 0 && ModConfigManager.CONFIG.limitedItemsPerHour) {
			long now = System.currentTimeMillis();
			for (Seen seen : arrow > 0 ? BETTER_AHEAD : BETTER_BEHIND) {
				lines.add(Component.literal("★ Seite " + seen.page() + ": " + seen.name() + " " + perHour(seen.perHour(now)))
					.withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		if (!ModConfigManager.CONFIG.limitedItemsPerHour) {
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
					String scope = summary != null && summary.pages() > 1 ? " auf " + summary.pages() + " Seiten" : " hier";
					added.add(Component.literal("★ Günstigstes pro Stunde" + scope + " (von " + offer.ofKind() + ")")
						.withStyle(ChatFormatting.YELLOW));
				} else {
					int more = (int) Math.round((offer.perHour() / offer.bestPerHour() - 1) * 100);
					String where = offer.bestPage() == page ? "" : " auf Seite " + offer.bestPage();
					added.add(Component.literal("Platz " + offer.rank() + " von " + offer.ofKind() + " · bestes" + where + " "
						+ perHour(offer.bestPerHour()) + " (+" + more + "%)").withStyle(ChatFormatting.DARK_GRAY));
				}
			}
		} else if (offer == null && left > 0 && ModConfigManager.CONFIG.limitedItemsSellHint) {
			added.addAll(sellHint(stack, left, now));
		}
		lines.addAll(insertAt(lines), added);
	}

	/** What to ask for an own limited item so it is the best offer per hour. */
	public record SellHint(double perItem, double bestPerHour, int bestPage, long seenAt) {
	}

	public static Optional<SellHint> sellHintFor(ItemStack stack, long left, long now) {
		String group = groupOf(stack);
		Seen best = null;
		for (List<Seen> list : PAGES.values()) {
			for (Seen seen : list) {
				if (seen.group().equals(group) && Double.isFinite(seen.perHour(now))
					&& (best == null || seen.perHour(now) < best.perHour(now))) {
					best = seen;
				}
			}
		}
		double bestPerHour;
		int bestPage;
		long seenAt;
		if (best != null) {
			bestPerHour = best.perHour(now);
			bestPage = best.page();
			seenAt = now;
		} else {
			AuctionPriceMemory.Seen remembered = AuctionPriceMemory.getPerHour(AuctionPriceMemory.keyOf(stack), AuctionMarket.AUCTION);
			if (remembered == null || now - remembered.minAt > SELL_HINT_MEMORY_MS) {
				return Optional.empty();
			}
			bestPerHour = remembered.min;
			bestPage = 0;
			seenAt = remembered.minAt;
		}
		double breakEven = bestPerHour * left / 3_600_000.0;
		double suggestion = niceFloor(breakEven * 0.99);
		return suggestion < 1 ? Optional.empty() : Optional.of(new SellHint(suggestion, bestPerHour, bestPage, seenAt));
	}

	private static List<Component> sellHint(ItemStack stack, long left, long now) {
		Optional<SellHint> hint = sellHintFor(stack, left, now);
		if (hint.isEmpty()) {
			return List.of(Component.literal("Verkaufspreis: AH einmal durchblättern").withStyle(ChatFormatting.DARK_GRAY));
		}
		SellHint h = hint.get();
		int count = stack.getCount();
		String total = count > 1 ? " (" + count + " Stück: " + AuctionSearchScreen.formatPrice(niceFloor(h.perItem() * count)) + ")" : "";
		String source = h.bestPage() > 0 ? "bestes Angebot " + perHour(h.bestPerHour()) + (h.bestPage() != page ? ", Seite " + h.bestPage() : "")
			: "bestes Angebot " + perHour(h.bestPerHour()) + ", " + AuctionSearchScreen.ageText(h.seenAt());
		return List.of(
			Component.literal("Verkaufen für höchstens ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(AuctionSearchScreen.formatPrice(h.perItem())).withStyle(ChatFormatting.GREEN))
				.append(Component.literal(total).withStyle(ChatFormatting.GRAY)),
			Component.literal("  = günstigstes pro Stunde (" + source + ")").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Rounds down to three significant digits: 12.345 -> 12.300, 987,6 -> 987. */
	static double niceFloor(double value) {
		if (value < 100) {
			return Math.floor(value * 100) / 100;
		}
		double step = Math.pow(10, Math.floor(Math.log10(value)) - 2);
		return Math.floor(value / step) * step;
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
