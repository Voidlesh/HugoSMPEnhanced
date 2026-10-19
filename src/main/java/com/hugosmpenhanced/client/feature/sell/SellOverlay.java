package com.hugosmpenhanced.client.feature.sell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.settings.Theme;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the sell assistant draws on inventory screens: the side panel in the sell menu (what is
 * sold, what stays and why, the confirmation on first use), green / blue / red marks on the player's
 * slots, lock icons on locked slots, and the Alt clicks in the inventory that add a rule or lock a slot.
 * Holding Alt in the inventory previews what would be sold.
 */
public final class SellOverlay {
	public static final int PANEL_WIDTH = 170;
	private static final int MIN_PANEL_WIDTH = 104;
	private static final int SELL_TINT = 0x5A3CC85A;
	private static final int KEEP_TINT = 0x505A9BFF;
	private static final int PROTECT_TINT = 0x60DC3C3C;
	private static final int ROW = 18;
	private static final int PAD = 7;
	private static final String[] LOCK = {".###.", "#...#", "#...#", "#####", "##.##", "##.##", "#####"};

	private static SellPlan framePlan;
	private static boolean marking;
	/** Button boxes of the panel, refreshed every frame: x, y, w, h. */
	private static int[] confirmBox;
	private static int[] cancelBox;
	private static int[] panelBox;

	private SellOverlay() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> beforeRender(container));
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> afterRender(container, graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> allowClick(container, event));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (container == SellEngine.boundScreen() && SellEngine.state() == SellEngine.State.CONFIRM
					&& (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || SellFeature.sellKey.matches(event))) {
					SellEngine.confirm();
					return false;
				}
				// Keys do not tick while a screen is open: let them stop the assistant from inside the sell menu.
				if (container == SellEngine.boundScreen() && SellEngine.running()) {
					if (SellFeature.sellKey.matches(event) || SellFeature.afkKey.matches(event)) {
						SellEngine.cancel();
						return false;
					}
				}
				return true;
			});
			ScreenEvents.remove(screen).register(s -> framePlan = null);
		});
	}

	// ------------------------------------------------------------------ slot marks

	private static void beforeRender(AbstractContainerScreen<?> screen) {
		Minecraft client = Minecraft.getInstance();
		ModConfig config = ModConfigManager.CONFIG;
		boolean bound = screen == SellEngine.boundScreen();
		boolean preview = screen instanceof InventoryScreen && config.sellEnabled && altDown();
		marking = bound || preview;
		framePlan = (marking || config.sellEnabled) && client.player != null ? SellPlan.compute(client.player.getInventory(), config) : null;
	}

	public static void renderSlot(GuiGraphicsExtractor graphics, Slot slot) {
		ModConfig config = ModConfigManager.CONFIG;
		if (!config.sellEnabled || framePlan == null || !(slot.container instanceof Inventory)) {
			return;
		}
		int index = slot.getContainerSlot();
		int x = slot.x;
		int y = slot.y;
		if (marking) {
			int tint = switch (framePlan.status(index)) {
				case SELL -> SELL_TINT;
				case KEEP -> KEEP_TINT;
				case PROTECTED, LOCKED -> PROTECT_TINT;
				default -> 0;
			};
			if (tint != 0) {
				graphics.fill(x, y, x + 16, y + 16, tint);
			}
		}
		if (config.sellLockedSlots.contains(index)) {
			graphics.fill(x + 10, y - 1, x + 17, y + 8, 0xC0000000);
			Theme.glyph(graphics, LOCK, x + 11, y, 0xFFFF6A6A);
		}
	}

	private static boolean altDown() {
		var window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
	}

	// ------------------------------------------------------------------ clicks

	private static boolean allowClick(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
		if (screen == SellEngine.boundScreen()) {
			if (SellEngine.state() == SellEngine.State.CONFIRM && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				if (inside(confirmBox, event.x(), event.y())) {
					SellEngine.confirm();
					return false;
				}
				if (inside(cancelBox, event.x(), event.y())) {
					SellEngine.cancel();
					return false;
				}
			}
			// While the assistant clicks, the player's own clicks would only get in the way.
			return SellEngine.state() == SellEngine.State.CONFIRM || SellEngine.state() == SellEngine.State.WAITING;
		}
		if (!(screen instanceof InventoryScreen) || !ModConfigManager.CONFIG.sellEnabled || !(event.hasAltDown() || altDown())) {
			return true;
		}
		Slot slot = ((AbstractContainerScreenAccessor) screen).hugosmpenhanced$getHoveredSlot();
		if (slot == null || !(slot.container instanceof Inventory) || slot.getContainerSlot() >= SellPlan.MAIN_END) {
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && slot.hasItem()) {
			toggleRule(slot.getItem());
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			toggleLock(slot.getContainerSlot());
			return false;
		}
		return true;
	}

	/** Alt + left click: add a rule for this item, or remove it again. */
	public static void toggleRule(ItemStack stack) {
		ModConfig config = ModConfigManager.CONFIG;
		String id = SellPlan.itemId(stack);
		String name = stack.getHoverName().getString();
		boolean removed = config.sellRules.removeIf(rule -> rule.item.equals(id));
		if (!removed) {
			config.sellRules.add(new SellRule(id));
		}
		ModConfigManager.save();
		message(removed ? Component.literal("Regel entfernt: ").withStyle(ChatFormatting.GRAY).append(Component.literal(name).withStyle(ChatFormatting.WHITE))
			: Component.literal("Regel angelegt: ").withStyle(ChatFormatting.GRAY).append(Component.literal(name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  (Behalten und Menge im Einstellungsmenü)").withStyle(ChatFormatting.DARK_GRAY)));
	}

	/** Alt + right click: lock or unlock this slot; locked slots are never sold from. */
	public static void toggleLock(int index) {
		ModConfig config = ModConfigManager.CONFIG;
		boolean unlocked = config.sellLockedSlots.remove(index);
		if (!unlocked) {
			config.sellLockedSlots.add(index);
		}
		ModConfigManager.save();
	}

	private static void message(Component text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[Verkaufen] ").withStyle(ChatFormatting.GOLD).append(text));
		}
	}

	private static boolean inside(int[] box, double x, double y) {
		return box != null && x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
	}

	// ------------------------------------------------------------------ side panel

	/** Centre of the panel's confirm (true) or cancel button in GUI pixels, for the game test. */
	public static int[] buttonCenter(boolean confirm) {
		int[] box = confirm ? confirmBox : cancelBox;
		return box == null ? null : new int[] {box[0] + box[2] / 2, box[1] + box[3] / 2};
	}

	private static void afterRender(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (screen != SellEngine.boundScreen() || framePlan == null) {
			confirmBox = null;
			cancelBox = null;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		ModConfig config = ModConfigManager.CONFIG;
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		AbstractContainerMenu menu = screen.getMenu();

		// Beside the menu, never over its slots: full width where there is room, narrower on small
		// windows / big GUI scales, only as a last resort over the menu's edge.
		int left = access.hugosmpenhanced$getLeftPos();
		int top = access.hugosmpenhanced$getTopPos();
		int rightSpace = screen.width - (left + access.hugosmpenhanced$getImageWidth()) - 6 - 2;
		int leftSpace = left - 6 - 2;
		int width;
		int x;
		if (rightSpace >= PANEL_WIDTH) {
			width = PANEL_WIDTH;
			x = left + access.hugosmpenhanced$getImageWidth() + 6;
		} else if (leftSpace >= PANEL_WIDTH) {
			width = PANEL_WIDTH;
			x = left - 6 - width;
		} else if (Math.max(rightSpace, leftSpace) >= MIN_PANEL_WIDTH) {
			width = Math.max(rightSpace, leftSpace);
			x = rightSpace >= leftSpace ? left + access.hugosmpenhanced$getImageWidth() + 6 : left - 6 - width;
		} else {
			width = Math.min(PANEL_WIDTH, screen.width - 4);
			x = screen.width - width - 2;
		}
		int cx = x + PAD;
		int right = x + width - PAD;
		int inner = right - cx;

		List<SellPlan.Line> selling = mergeWithMenu(menu, framePlan);
		List<SellPlan.Line> kept = framePlan.keptLines();
		boolean confirming = SellEngine.state() == SellEngine.State.CONFIRM;
		int buttonWidth = (inner - 6) / 2;
		boolean stackedButtons = buttonWidth < font.width("Abbrechen") + 10;
		int buttonsHeight = stackedButtons ? 18 + 4 + 18 + 4 : 22;
		int base = PAD + 12 + 6 + 12 + Math.max(1, selling.size()) * ROW + 4 + 12 + 6 + (confirming ? 12 + buttonsHeight : 14) + PAD - 4;
		// Show as many kept items as fit on the screen (at most 5), the rest as "+ N weitere".
		int shownKept = Math.min(kept.size(), 5);
		while (shownKept > 0 && base + 12 + shownKept * ROW + (kept.size() > shownKept ? 10 : 0) > screen.height - 4) {
			shownKept--;
		}
		int height = base;
		if (!kept.isEmpty()) {
			height += 12 + shownKept * ROW + (kept.size() > shownKept ? 10 : 0);
		}
		int y = Math.max(2, Math.min(top, screen.height - height - 2));
		panelBox = new int[] {x, y, width, height};
		graphics.nextStratum();
		Theme.panel(graphics, x, y, width, height, Theme.FRAME, Theme.FRAME_BORDER);

		int cy = y + PAD;
		String chip = SellEngine.mode() == SellEngine.Mode.AFK ? "AFK-FARM" : "JETZT";
		Component title = Component.literal("Verkaufs-Assistent").withStyle(ChatFormatting.BOLD);
		if (font.width(title) + 6 + font.width(chip) + 8 > inner) {
			title = Component.literal("Verkaufen").withStyle(ChatFormatting.BOLD);
			if (font.width(title) + 6 + font.width(chip) + 8 > inner) {
				chip = SellEngine.mode() == SellEngine.Mode.AFK ? "AFK" : chip;
			}
		}
		int chipW = font.width(chip) + 8;
		graphics.text(font, title, cx, cy, Theme.WHITE);
		Theme.panel(graphics, right - chipW, cy - 2, chipW, 12, Theme.GOLD_FILL, Theme.GOLD);
		graphics.text(font, chip, right - chipW + 4, cy, Theme.GOLD, false);
		cy += 12;
		graphics.fill(cx, cy + 2, right, cy + 3, Theme.FRAME_BORDER);
		cy += 6;

		graphics.text(font, "WIRD VERKAUFT", cx, cy, 0xFF6ED982, false);
		cy += 12;
		if (selling.isEmpty()) {
			graphics.text(font, fit(font, "Nichts mehr einzulegen", inner), cx, cy + 4, Theme.MUTED, false);
			cy += ROW;
		}
		double total = 0;
		boolean allKnown = true;
		for (SellPlan.Line line : selling) {
			graphics.item(line.stack(), cx, cy);
			String count = SellEngine.formatCount(line.count()) + "x";
			Double price = config.sellPrices.get(SellPlan.itemId(line.stack()));
			String money = price != null ? SellEngine.formatMoney(price * line.count()) : null;
			int used = font.width(count) + (money != null ? 6 + font.width(money) : 0);
			drawName(graphics, font, line.stack().getHoverName().getString(), cx + 20, cy + 4, right - cx - 20 - used - 6);
			if (money != null) {
				graphics.text(font, money, right - font.width(money), cy + 4, Theme.GOLD, false);
				graphics.text(font, count, right - font.width(money) - 6 - font.width(count), cy + 4, Theme.MUTED, false);
				total += price * line.count();
			} else {
				allKnown = false;
				graphics.text(font, count, right - font.width(count), cy + 4, Theme.MUTED, false);
			}
			cy += ROW;
		}
		graphics.fill(cx, cy + 1, right, cy + 2, 0xFF323232);
		cy += 5;
		if (allKnown && !selling.isEmpty()) {
			String sum = SellEngine.formatMoney(total);
			String label = font.width("Summe (geschätzt)") <= inner - font.width(sum) - 6 ? "Summe (geschätzt)" : "Summe ca.";
			graphics.text(font, fit(font, label, inner - font.width(sum) - 6), cx, cy, Theme.MUTED, false);
			graphics.text(font, sum, right - font.width(sum), cy, Theme.GOLD, false);
		} else {
			graphics.text(font, fit(font, inner >= font.width("Erlös kommt danach im Chat") ? "Erlös kommt danach im Chat" : "Erlös: siehe Chat", inner),
				cx, cy, Theme.FAINT, false);
		}
		cy += 12 + 6;

		if (!kept.isEmpty()) {
			graphics.text(font, fit(font, "BLEIBT IM INVENTAR", inner), cx, cy, 0xFFE07878, false);
			cy += 12;
			for (int i = 0; i < shownKept; i++) {
				SellPlan.Line line = kept.get(i);
				graphics.item(line.stack(), cx, cy);
				String reason = fit(font, line.reason(), inner - 20);
				int reasonColor = reason.startsWith("Regel") || reason.startsWith("erst") ? 0xFF96C8FF : 0xFFE68282;
				int reasonW = font.width(reason);
				drawName(graphics, font, line.stack().getHoverName().getString() + " x" + SellEngine.formatCount(line.count()),
					cx + 20, cy + 4, inner - 20 - reasonW - 6);
				graphics.text(font, reason, right - reasonW, cy + 4, reasonColor, false);
				cy += ROW;
			}
			if (kept.size() > shownKept) {
				graphics.text(font, "+ " + (kept.size() - shownKept) + (shownKept == 0 ? " Items" : " weitere"), cx + (shownKept == 0 ? 0 : 20), cy, Theme.FAINT, false);
				cy += 10;
			}
		}

		if (confirming) {
			String hint = inner >= font.width("Rückfrage nur beim ersten Mal") ? "Rückfrage nur beim ersten Mal" : "Nur beim ersten Mal";
			graphics.text(font, fit(font, hint, inner), cx, cy, Theme.FAINT, false);
			cy += 12;
			if (stackedButtons) {
				confirmBox = new int[] {cx, cy, inner, 18};
				cancelBox = new int[] {cx, cy + 22, inner, 18};
			} else {
				confirmBox = new int[] {cx, cy, buttonWidth, 18};
				cancelBox = new int[] {right - buttonWidth, cy, buttonWidth, 18};
			}
			boolean overConfirm = inside(confirmBox, mouseX, mouseY);
			boolean overCancel = inside(cancelBox, mouseX, mouseY);
			Theme.panel(graphics, confirmBox[0], confirmBox[1], confirmBox[2], 18, overConfirm ? 0xFFFFD866 : Theme.GOLD, 0xFF966E14);
			String sell = "Verkaufen";
			graphics.text(font, sell, confirmBox[0] + (confirmBox[2] - font.width(sell)) / 2, confirmBox[1] + 5, 0xFF2A1C00, false);
			Theme.panel(graphics, cancelBox[0], cancelBox[1], cancelBox[2], 18, overCancel ? 0xFF383838 : 0xFF282828, overCancel ? Theme.GOLD : 0xFF5A5A5A);
			String cancel = "Abbrechen";
			graphics.text(font, cancel, cancelBox[0] + (cancelBox[2] - font.width(cancel)) / 2, cancelBox[1] + 5, Theme.WHITE, false);
		} else {
			confirmBox = null;
			cancelBox = null;
			String status = statusLine(config);
			if (font.width(status) > inner && SellEngine.state() == SellEngine.State.WAITING) {
				status = "Menü " + Math.max(0, SellEngine.fillPercent()) + " % (ab " + config.sellThreshold + " %)";
			}
			graphics.text(font, fit(font, status, inner), cx, cy + 2, Theme.MUTED, false);
		}
	}

	/** Item name, shortened with "..."; left out when there is no sensible room (the icon says enough). */
	private static void drawName(GuiGraphicsExtractor graphics, Font font, String name, int x, int y, int width) {
		if (width >= 24) {
			graphics.text(font, fit(font, name, width), x, y, Theme.TEXT, false);
		}
	}

	/** Cuts text to the width with "..." instead of mid-word without a hint. */
	private static String fit(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))).stripTrailing() + "...";
	}

	private static String statusLine(ModConfig config) {
		return switch (SellEngine.state()) {
			case INSERTING -> "Lege ein ...";
			case SELLING -> "Verkaufe ...";
			case WAITING -> "Menü " + Math.max(0, SellEngine.fillPercent()) + " % · verkauft ab " + config.sellThreshold + " %";
			default -> "";
		};
	}

	/** What will be sold: what is already in the menu plus what the plan still moves in. */
	private static List<SellPlan.Line> mergeWithMenu(AbstractContainerMenu menu, SellPlan plan) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, ItemStack> examples = new LinkedHashMap<>();
		int size = SellEngine.containerSize(menu);
		for (int i = 0; i < size; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (i == SellEngine.buttonSlot() || stack.isEmpty()) {
				continue;
			}
			String id = SellPlan.itemId(stack);
			counts.computeIfAbsent(id, k -> new int[1])[0] += stack.getCount();
			examples.putIfAbsent(id, stack);
		}
		for (SellPlan.Line line : plan.sellLines()) {
			String id = SellPlan.itemId(line.stack());
			counts.computeIfAbsent(id, k -> new int[1])[0] += line.count();
			examples.putIfAbsent(id, line.stack());
		}
		List<SellPlan.Line> lines = new ArrayList<>();
		counts.forEach((id, count) -> lines.add(new SellPlan.Line(examples.get(id), count[0], null)));
		return lines;
	}
}
