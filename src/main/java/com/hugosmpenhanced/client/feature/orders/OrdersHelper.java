package com.hugosmpenhanced.client.feature.orders;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.auction.AuctionLore;
import com.hugosmpenhanced.client.feature.auction.AuctionPriceMemory;
import com.hugosmpenhanced.client.feature.auction.LimitedOffers;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.util.MenuContext;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * In the orders menu: which orders you can fill from your inventory right now and what that pays.
 * Fillable orders get a green frame (the best one gold), matching items in your inventory a green
 * corner, each order's tooltip the numbers, and a line under the menu the total.
 * <p>
 * The menu is recognised by its title (words "menu.orders" in the formats file), an order by a
 * price in its lore ("order.price", or the auction house's "Preis …: $" line), the progress by
 * "order.progress" ("120/500").
 */
public final class OrdersHelper {
	private static final int GREEN = 0xFF5ADC78;
	private static final int GREEN_FILL = 0x405ADC78;
	private static final int GOLD = 0xFFFFC83A;
	private static final int GOLD_FILL = 0x50FFC83A;

	/** Lets game tests run it outside HugoSMP. */
	public static boolean forceActive;

	/** One order in the menu and what you could deliver to it. */
	public record Order(double perItem, int done, int total, String owner, int have, int deliverable, double value, boolean best) {
		public int open() {
			return this.total > 0 ? Math.max(0, this.total - this.done) : -1;
		}
	}

	private static AbstractContainerScreen<?> screen;
	private static final Map<ItemStack, Order> ORDERS = new IdentityHashMap<>();
	private static final List<ItemStack> FILLABLE = new ArrayList<>();
	private static List<ItemStack> lastContents = new ArrayList<>();

	private OrdersHelper() {
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
					ORDERS.clear();
					FILLABLE.clear();
					lastContents.clear();
				}
			});
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addTooltip(stack, lines));
	}

	private static boolean active(AbstractContainerScreen<?> container) {
		if (!ModConfigManager.CONFIG.ordersHelperEnabled || !forceActive && !ServerCheck.isHugoSmp()) {
			return false;
		}
		return MenuContext.kindOf(HugoFormats.clean(container.getTitle().getString())) == MenuContext.Kind.ORDERS;
	}

	public static void update(AbstractContainerScreen<?> container) {
		if (!active(container)) {
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
		ORDERS.clear();
		FILLABLE.clear();
		List<ItemStack> inventory = new ArrayList<>();
		for (Slot slot : slots) {
			if (slot.container instanceof Inventory && !slot.getItem().isEmpty()) {
				inventory.add(slot.getItem());
			}
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (inventory.isEmpty() && player != null) {
			// Menus that hide the player's inventory still know it.
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty()) {
					inventory.add(stack);
				}
			}
		}
		Map<ItemStack, Order> found = new IdentityHashMap<>();
		ItemStack best = null;
		double bestValue = 0;
		for (Slot slot : slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || slot.container instanceof Inventory) {
				continue;
			}
			Order order = read(stack, inventory);
			if (order == null) {
				continue;
			}
			found.put(stack, order);
			if (order.value() > bestValue) {
				bestValue = order.value();
				best = stack;
			}
		}
		for (Map.Entry<ItemStack, Order> entry : found.entrySet()) {
			Order order = entry.getValue();
			if (entry.getKey() == best) {
				order = new Order(order.perItem(), order.done(), order.total(), order.owner(), order.have(), order.deliverable(), order.value(), true);
			}
			ORDERS.put(entry.getKey(), order);
		}
		for (ItemStack stack : inventory) {
			for (Map.Entry<ItemStack, Order> entry : ORDERS.entrySet()) {
				if (entry.getValue().deliverable() > 0 && sameKind(stack, entry.getKey())) {
					FILLABLE.add(stack);
					break;
				}
			}
		}
	}

	/** The order a menu item stands for, or null when its lore has no price. */
	static Order read(ItemStack stack, List<ItemStack> inventory) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		double perItem = -1;
		double total = -1;
		int done = -1;
		int wanted = -1;
		String owner = null;
		for (Component line : lore.lines()) {
			String text = HugoFormats.clean(line.getString());
			if (perItem < 0) {
				Matcher price = HugoFormats.find("order.price", text);
				if (price != null) {
					perItem = Money.parse(HugoFormats.group(price, "amount"));
				} else {
					Optional<AuctionLore.Price> auction = AuctionLore.parse(text);
					if (auction.isPresent() && auction.get().perItem()) {
						perItem = auction.get().amount();
					}
				}
			}
			if (total < 0) {
				Matcher sum = HugoFormats.find("order.total", text);
				if (sum != null) {
					total = Money.parse(HugoFormats.group(sum, "amount"));
				}
			}
			if (wanted < 0) {
				Matcher progress = HugoFormats.find("order.progress", text);
				if (progress != null) {
					Long a = number(HugoFormats.group(progress, "done"));
					Long b = number(HugoFormats.group(progress, "total"));
					if (a != null && b != null && b > 0 && a <= b) {
						done = a.intValue();
						wanted = b.intValue();
					}
				}
			}
			if (owner == null) {
				Matcher who = HugoFormats.find("order.owner", text);
				if (who != null) {
					owner = HugoFormats.group(who, "name");
				}
			}
		}
		if (perItem <= 0 && total > 0 && wanted > 0) {
			perItem = total / wanted;
		}
		if (perItem <= 0) {
			return null;
		}
		int have = 0;
		for (ItemStack own : inventory) {
			if (sameKind(own, stack)) {
				have += own.getCount();
			}
		}
		int open = wanted > 0 ? Math.max(0, wanted - Math.max(0, done)) : -1;
		int deliverable = open >= 0 ? Math.min(have, open) : have;
		return new Order(perItem, Math.max(0, done), wanted, owner, have, deliverable, deliverable * perItem, false);
	}

	private static Long number(String text) {
		if (text == null) {
			return null;
		}
		Double value = AuctionLore.parseAmount(text);
		return value == null ? null : Math.round(value);
	}

	/**
	 * Same item type. The order's icon carries its own name ("Auftrag: Diamant"), so names say
	 * nothing; only enchanted books must also carry the same enchantment.
	 */
	static boolean sameKind(ItemStack own, ItemStack wanted) {
		if (own.isEmpty() || own.getItem() != wanted.getItem()) {
			return false;
		}
		if (!wanted.is(Items.ENCHANTED_BOOK)) {
			return true;
		}
		String wantedKey = AuctionPriceMemory.keyOf(wanted);
		return wantedKey == null || wantedKey.equals(AuctionPriceMemory.keyOf(own));
	}

	// ------------------------------------------------------------------ drawing

	public static void renderSlotBackground(GuiGraphics graphics, Slot slot) {
		if (screen == null || !ModConfigManager.CONFIG.ordersMarkFillable || slot.getItem().isEmpty()) {
			return;
		}
		ItemStack stack = slot.getItem();
		if (slot.container instanceof Inventory) {
			if (containsIdentity(FILLABLE, stack)) {
				graphics.fill(slot.x + 11, slot.y, slot.x + 16, slot.y + 2, GREEN);
				graphics.fill(slot.x + 14, slot.y, slot.x + 16, slot.y + 5, GREEN);
			}
			return;
		}
		Order order = ORDERS.get(stack);
		if (order == null || order.deliverable() <= 0) {
			return;
		}
		graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, order.best() ? GOLD_FILL : GREEN_FILL);
		graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, order.best() ? GOLD : GREEN);
	}

	private static boolean containsIdentity(List<ItemStack> list, ItemStack stack) {
		for (ItemStack one : list) {
			if (one == stack) {
				return true;
			}
		}
		return false;
	}

	private static void renderSummary(AbstractContainerScreen<?> container, GuiGraphics graphics) {
		if (screen != container || ORDERS.isEmpty()) {
			return;
		}
		int count = 0;
		double sum = 0;
		for (Order order : ORDERS.values()) {
			if (order.deliverable() > 0) {
				count++;
				sum += order.value();
			}
		}
		String text = count == 0 ? "Keinen dieser " + ORDERS.size() + " Aufträge kannst du gerade beliefern"
			: (count == 1 ? "1 Auftrag" : count + " Aufträge") + " lieferbar · zusammen " + Money.compact(Math.round(sum));
		Font font = Minecraft.getInstance().font;
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) container;
		int width = font.width(text) + 8;
		int x = Math.max(4, accessor.hugosmpenhanced$getLeftPos() + (accessor.hugosmpenhanced$getImageWidth() - width) / 2);
		// Under the menu; one line lower when the limited-items line is there too.
		int y = accessor.hugosmpenhanced$getTopPos() + accessor.hugosmpenhanced$getImageHeight() + 3
			+ (LimitedOffers.summaryShown(container) ? 14 : 0);
		if (y + 12 > container.height) {
			// No room below: above the menu, at the very top if need be.
			y = Math.max(1, accessor.hugosmpenhanced$getTopPos() - 14);
		}
		graphics.fill(x, y, x + width, y + 12, 0xE0101010);
		graphics.renderOutline(x, y, width, 12, count > 0 ? 0xFF2F7A40 : 0xFF444444);
		graphics.drawString(font, text, x + 4, y + 2, count > 0 ? 0xFF8CF0A0 : 0xFFAAAAAA);
	}

	private static void addTooltip(ItemStack stack, List<Component> lines) {
		Order order = ORDERS.get(stack);
		if (order == null || screen == null) {
			return;
		}
		lines.add(Component.empty());
		lines.add(Component.literal("Aufträge-Helfer").withStyle(ChatFormatting.DARK_GREEN));
		lines.add(Component.literal("Preis pro Stück: " + Money.format(Math.round(order.perItem()))).withStyle(ChatFormatting.GRAY));
		if (order.open() >= 0) {
			lines.add(Component.literal("Noch offen: " + order.open() + " von " + order.total() + " (" + Money.compact(Math.round(order.open() * order.perItem())) + ")")
				.withStyle(ChatFormatting.GRAY));
		}
		if (order.have() <= 0) {
			lines.add(Component.literal("Du hast keins davon dabei.").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		lines.add(Component.literal("Du hast " + order.have() + " im Inventar").withStyle(ChatFormatting.GRAY));
		if (order.deliverable() > 0) {
			lines.add(Component.literal("Lieferbar: " + order.deliverable() + " → " + Money.format(Math.round(order.value())))
				.withStyle(order.best() ? ChatFormatting.GOLD : ChatFormatting.GREEN));
		}
	}

	/** For game tests. */
	public static Order order(ItemStack stack) {
		return ORDERS.get(stack);
	}
}
