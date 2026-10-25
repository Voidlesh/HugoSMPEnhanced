package com.hugosmpenhanced.client.feature.sell;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.auction.AuctionLore;
import com.hugosmpenhanced.client.feature.money.MoneyTracker;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The sell assistant's state machine, ticked once per client tick. It sends {@code /sell}, binds
 * only to the menu that opens in answer to it, shift-clicks what {@link SellPlan} allows into it,
 * clicks the sell button and checks the goods are gone. Every click goes through
 * {@code handleInventoryMouseClick}, exactly what a real mouse click sends.
 *
 * <p>"Jetzt verkaufen" sells once and stops. The AFK farm keeps going, sells once the menu is full
 * enough and pauses (instead of taking the screen away) while the player moves, chats or gets hurt.</p>
 */
public final class SellEngine {
	public enum Mode {
		NOW, AFK
	}

	public enum State {
		IDLE, OPENING, CONFIRM, INSERTING, SELLING, WAITING, PAUSED
	}

	/** One sale: what went in and, once the server tells us, what it paid. */
	public record Sale(Map<String, Integer> counts, Map<String, ItemStack> examples, int items) {
	}

	private static final int PLAYER_SLOTS = 36;
	private static final int OPEN_TIMEOUT = 100;
	private static final int MAX_OPEN_ATTEMPTS = 3;
	private static final int WATCHDOG = 600;
	private static final int SELL_TIMEOUT = 40;
	private static final int INCOME_WINDOW = 60;
	private static final int REOPEN_DELAY = 20;
	private static final int POLL = 20;
	private static final int RESUME_STILL_TICKS = 100;
	private static final double MOVE_LIMIT = 1.0;
	private static final List<String> BUTTON_WORDS = List.of("verkauf", "sell", "bestätig", "bestaetig", "confirm");
	private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9.,]*)\\s*([kKmM](?![a-zA-Z]))?|([0-9][0-9.,]*)\\s*([kKmM])?\\s*\\$");

	/** Lets the game test sell in singleplayer; the assistant otherwise only runs on HugoSMP. */
	public static boolean anyServer;

	private static Mode mode = Mode.NOW;
	private static State state = State.IDLE;
	private static int boundId = -1;
	private static int buttonSlot = -1;
	private static int cooldown;
	private static int openWait;
	private static int openAttempts;
	private static int watchdog;
	private static int sellWait;
	private static int sellRetries;
	private static int buttonProblems;
	private static boolean buttonGuard;
	private static String batchSignature;
	private static int moveFailures;
	private static boolean waitingShown;
	private static int pollTimer;

	private static String pauseReason = "";
	private static String stopReason = "";
	private static long stoppedAt;
	private static Vec3 anchor;
	private static Vec3 lastPos;
	private static int stillTicks;
	private static float lastHealth;
	private static int autostartTicks;

	private static Sale pendingSale;
	private static int incomeTicks;

	// Session totals for the HUD.
	private static int sales;
	private static int itemsSold;
	private static double income;
	private static boolean incomeKnown;
	private static long sessionStart;
	private static int runSales;

	private SellEngine() {
	}

	// ------------------------------------------------------------------ queries for HUD and overlay

	public static State state() {
		return state;
	}

	public static Mode mode() {
		return mode;
	}

	public static boolean running() {
		return state != State.IDLE;
	}

	public static boolean afkRunning() {
		return state != State.IDLE && mode == Mode.AFK;
	}

	public static String pauseReason() {
		return pauseReason;
	}

	/** Why the assistant stopped last, while the HUD still shows it; null otherwise. */
	public static String recentStop() {
		return System.currentTimeMillis() - stoppedAt < 6000 && !stopReason.isEmpty() ? stopReason : null;
	}

	public static int sales() {
		return sales;
	}

	public static int itemsSold() {
		return itemsSold;
	}

	public static double income() {
		return incomeKnown ? income : -1;
	}

	/** Income per hour of this AFK session, -1 while unknown. */
	public static double incomePerHour() {
		long ms = System.currentTimeMillis() - sessionStart;
		if (!incomeKnown || ms < 60_000) {
			return -1;
		}
		return income * 3_600_000.0 / ms;
	}

	/** The menu the assistant works in, if it is open right now. */
	public static AbstractContainerScreen<?> boundScreen() {
		Minecraft client = Minecraft.getInstance();
		if (boundId != -1 && client.gui.screen() instanceof AbstractContainerScreen<?> screen
			&& screen.getMenu().containerId == boundId) {
			return screen;
		}
		return null;
	}

	public static int buttonSlot() {
		return buttonSlot;
	}

	/** How full the bound menu is, 0-100, or -1 when none is open. */
	public static int fillPercent() {
		AbstractContainerScreen<?> screen = boundScreen();
		return screen == null ? -1 : fillPercent(screen.getMenu(), containerSize(screen.getMenu()));
	}

	// ------------------------------------------------------------------ controls

	/** The sell key: sells once, or stops whatever is running. */
	public static void pressSell() {
		if (running()) {
			stop(mode == Mode.AFK ? "AFK-Farm ausgeschaltet." : "Verkauf abgebrochen.", true);
			return;
		}
		startNow();
	}

	/** The AFK key (or Shift + sell key). */
	public static void toggleAfk() {
		if (afkRunning()) {
			stop("AFK-Farm ausgeschaltet.", true);
			return;
		}
		if (running()) {
			stop("", true);
		}
		startAfk(false);
	}

	public static void startNow() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!checkCanStart(player)) {
			return;
		}
		SellPlan plan = SellPlan.compute(player.getInventory(), ModConfigManager.CONFIG);
		if (plan.isEmpty()) {
			info(plan.keptLines().isEmpty()
				? "Nichts zu verkaufen: kein Item aus deinen Regeln im Inventar."
				: "Nichts zu verkaufen: alles wird behalten, ist gesperrt oder geschützt.");
			return;
		}
		begin(Mode.NOW);
		open(player);
	}

	public static void startAfk(boolean automatic) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (!checkCanStart(player)) {
			return;
		}
		ModConfig config = ModConfigManager.CONFIG;
		if (automatic && config.sellConfirm && !SellPlan.rulesKey(config).equals(config.sellConfirmedRules)) {
			info("AFK-Farm nicht automatisch gestartet: deine Regeln wurden noch nicht bestätigt. Einmal mit Shift+G starten.");
			return;
		}
		begin(Mode.AFK);
		sales = 0;
		itemsSold = 0;
		income = 0;
		incomeKnown = false;
		sessionStart = System.currentTimeMillis();
		config.sellAfkWasOn = true;
		ModConfigManager.save();
		anchor = player.position();
		lastHealth = player.getHealth();
		info((automatic ? "AFK-Farm automatisch gestartet." : "AFK-Farm an.") + " Stopp mit derselben Taste.");
		state = State.WAITING;
		pollTimer = 0;
	}

	/** Called by the preview's "Verkaufen" button. */
	public static void confirm() {
		if (state != State.CONFIRM) {
			return;
		}
		ModConfig config = ModConfigManager.CONFIG;
		config.sellConfirmedRules = SellPlan.rulesKey(config);
		ModConfigManager.save();
		state = State.INSERTING;
		cooldown = 2;
		progress();
	}

	/** Called by the preview's "Abbrechen" button and Escape. */
	public static void cancel() {
		// Close first: stop() forgets which menu was ours.
		closeMenu();
		stop(mode == Mode.AFK ? "AFK-Farm ausgeschaltet." : "Verkauf abgebrochen.", true);
	}

	private static boolean checkCanStart(LocalPlayer player) {
		ModConfig config = ModConfigManager.CONFIG;
		if (player == null) {
			return false;
		}
		if (!config.sellEnabled) {
			info("Der Verkaufs-Assistent ist aus. Einschalten unter Einstellungen > Inventar & Handel > Verkaufen.");
			return false;
		}
		if (!anyServer && !ServerCheck.isHugoSmp()) {
			info("Der Verkaufs-Assistent funktioniert nur auf HugoSMP.");
			return false;
		}
		if (config.sellRules.stream().noneMatch(rule -> rule.enabled)) {
			info("Noch keine Regeln. Alt + Klick auf ein Item im Inventar legt eine an.");
			return false;
		}
		return true;
	}

	private static void begin(Mode newMode) {
		resetMenuState();
		mode = newMode;
		boundId = -1;
		openAttempts = 0;
		buttonProblems = 0;
		runSales = 0;
		stopReason = "";
		progress();
	}

	/** Stops; userAction says whether the player asked for it (then the AFK farm does not autostart again). */
	public static void stop(String reason, boolean userAction) {
		boolean wasAfk = mode == Mode.AFK && state != State.IDLE;
		state = State.IDLE;
		resetMenuState();
		boundId = -1;
		autostartTicks = 0;
		if (wasAfk) {
			ModConfigManager.CONFIG.sellAfkWasOn = false;
			ModConfigManager.save();
		}
		if (!userAction && !reason.isEmpty()) {
			stopReason = reason;
			stoppedAt = System.currentTimeMillis();
			info(Component.literal("Gestoppt: ").withStyle(ChatFormatting.RED).append(Component.literal(reason).withStyle(ChatFormatting.WHITE)));
		} else if (!reason.isEmpty()) {
			info(reason);
		}
	}

	// ------------------------------------------------------------------ events

	/** On joining a server: schedule the autostart. */
	public static void onJoin() {
		state = State.IDLE;
		boundId = -1;
		ModConfig config = ModConfigManager.CONFIG;
		boolean wanted = config.sellAutostart == SellAutostart.ALWAYS
			|| (config.sellAutostart == SellAutostart.IF_WAS_ON && config.sellAfkWasOn);
		autostartTicks = config.sellEnabled && wanted ? 200 : 0;
	}

	/** On leaving: forget the menu, but remember that the AFK farm was on for the next join. */
	public static void onDisconnect() {
		state = State.IDLE;
		boundId = -1;
		autostartTicks = 0;
		resetMenuState();
	}

	/** System chat and action bar: picks up what the server paid for the last sale. */
	public static void onGameMessage(Component message) {
		if (pendingSaleDraft != null && state == State.SELLING) {
			// Many servers pay before the menu empties or closes: keep it for the sale being finished.
			double early = parseMoney(message.getString());
			if (early > 0) {
				earlyIncome = early;
			}
			return;
		}
		if (pendingSale == null || incomeTicks <= 0) {
			return;
		}
		double amount = parseMoney(message.getString());
		if (amount > 0) {
			announce(pendingSale, amount);
			pendingSale = null;
			incomeTicks = 0;
		}
	}

	/** First money amount in a message ("$1.234", "1.234$", "$1,5k"), or -1. */
	public static double parseMoney(String text) {
		Matcher matcher = MONEY.matcher(text);
		if (!matcher.find()) {
			return -1;
		}
		String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
		String suffix = matcher.group(1) != null ? matcher.group(2) : matcher.group(4);
		Double value = AuctionLore.parseAmount(digits);
		if (value == null) {
			return -1;
		}
		if (suffix != null) {
			value *= switch (suffix.toLowerCase(Locale.ROOT)) {
				case "k" -> 1_000;
				case "m" -> 1_000_000;
				default -> 1;
			};
		}
		return value;
	}

	// ------------------------------------------------------------------ tick

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (autostartTicks > 0 && player != null && --autostartTicks == 0 && state == State.IDLE
			&& (anyServer || ServerCheck.isHugoSmp())) {
			startAfk(true);
		}
		if (pendingSale != null && --incomeTicks <= 0) {
			announce(pendingSale, -1);
			pendingSale = null;
		}
		if (state == State.IDLE) {
			return;
		}
		if (player == null || client.level == null || client.gameMode == null) {
			onDisconnect();
			return;
		}
		if (!player.isAlive()) {
			stop("Gestorben.", false);
			return;
		}

		AbstractContainerMenu menu = boundMenu(client);
		if (boundId != -1 && menu == null) {
			menuClosed(player);
			if (state == State.IDLE) {
				return;
			}
		}
		if (state == State.OPENING && menu == null) {
			menu = tryBind(client);
			if (menu == null) {
				if (--openWait <= 0) {
					if (openAttempts >= MAX_OPEN_ATTEMPTS) {
						stop("/" + command() + " hat " + openAttempts + "x kein Menü geöffnet.", false);
					} else {
						send(player);
					}
				}
				return;
			}
		}

		if (state == State.INSERTING || state == State.SELLING || state == State.OPENING) {
			if (++watchdog > WATCHDOG) {
				stop("Seit " + WATCHDOG / 20 + " s geht es nicht weiter.", false);
				return;
			}
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		switch (state) {
			case CONFIRM -> {
			}
			case INSERTING -> {
				if (menu != null) {
					insertStep(client, player, menu);
				}
			}
			case SELLING -> {
				if (menu != null) {
					sellingStep(client, player, menu);
				}
			}
			case WAITING -> waitingStep(client, player, menu);
			case PAUSED -> pausedStep(client, player);
			default -> {
			}
		}
		if (player != null) {
			lastPos = player.position();
			lastHealth = player.getHealth();
		}
	}

	private static void menuClosed(LocalPlayer player) {
		boundId = -1;
		boolean afterSale = state == State.SELLING;
		Sale draft = pendingSaleDraft;
		resetMenuState();
		if (afterSale) {
			// HugoSMP closes the menu after a sale; the goods are gone.
			pendingSaleDraft = draft;
			finishSale();
			afterSale(player);
			return;
		}
		if (mode == Mode.NOW) {
			stop(state == State.CONFIRM ? "Verkauf abgebrochen." : "Menü geschlossen, Verkauf abgebrochen.", true);
		} else {
			pause("Menü geschlossen");
		}
	}

	/** After a sale: go on while there is more to sell, otherwise finish (now) or wait (AFK). */
	private static void afterSale(LocalPlayer player) {
		SellPlan plan = SellPlan.compute(player.getInventory(), ModConfigManager.CONFIG);
		boolean menuOpen = boundId != -1;
		if (!plan.isEmpty()) {
			if (menuOpen) {
				state = State.INSERTING;
				cooldown = ModConfigManager.CONFIG.sellSpeed.delayTicks();
			} else {
				state = State.OPENING;
				openAttempts = 0;
				openWait = REOPEN_DELAY;
				boundId = -1;
			}
			return;
		}
		if (mode == Mode.NOW) {
			closeMenu();
			state = State.IDLE;
			boundId = -1;
		} else {
			state = State.WAITING;
			pollTimer = POLL;
			waitingShown = false;
		}
	}

	// ------------------------------------------------------------------ menu binding

	private static void open(LocalPlayer player) {
		state = State.OPENING;
		openAttempts = 0;
		send(player);
	}

	private static void send(LocalPlayer player) {
		player.connection.sendCommand(command());
		openAttempts++;
		openWait = OPEN_TIMEOUT;
		progress();
	}

	private static String command() {
		String command = ModConfigManager.CONFIG.sellCommand.trim();
		return command.startsWith("/") ? command.substring(1) : command;
	}

	private static AbstractContainerMenu boundMenu(Minecraft client) {
		AbstractContainerScreen<?> screen = boundScreen();
		return screen == null ? null : screen.getMenu();
	}

	private static AbstractContainerMenu tryBind(Minecraft client) {
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return null;
		}
		AbstractContainerMenu menu = screen.getMenu();
		int size = containerSize(menu);
		if (size <= 0 || menu.containerId == 0) {
			return null;
		}
		boundId = menu.containerId;
		buttonSlot = findButton(menu, size);
		resetMenuState();
		progress();
		ModConfig config = ModConfigManager.CONFIG;
		boolean confirmed = !config.sellConfirm || SellPlan.rulesKey(config).equals(config.sellConfirmedRules);
		state = confirmed ? State.INSERTING : State.CONFIRM;
		cooldown = 2;
		return menu;
	}

	/** Container slots without the 36 player slots, or -1 for menus that cannot be the sell menu. */
	static int containerSize(AbstractContainerMenu menu) {
		if (menu instanceof InventoryMenu) {
			return -1;
		}
		int size = menu.slots.size() - PLAYER_SLOTS;
		return size >= 9 ? size : -1;
	}

	/** The sell button: a slot whose item is named like one, otherwise the last slot. */
	private static int findButton(AbstractContainerMenu menu, int size) {
		for (int i = 0; i < size; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
			for (String word : BUTTON_WORDS) {
				if (name.contains(word)) {
					return i;
				}
			}
		}
		return size - 1;
	}

	// ------------------------------------------------------------------ inserting

	private static void insertStep(Minecraft client, LocalPlayer player, AbstractContainerMenu menu) {
		int size = containerSize(menu);
		if (handleCursor(client, player, menu, size)) {
			return;
		}

		if (batchSignature != null) {
			boolean unchanged = batchSignature.equals(signature(menu, size));
			batchSignature = null;
			if (unchanged) {
				if (++moveFailures >= 2) {
					moveFailures = 0;
					clickSell(client, player, menu, size);
					return;
				}
			} else {
				moveFailures = 0;
				progress();
			}
		}

		SellPlan plan = SellPlan.compute(player.getInventory(), ModConfigManager.CONFIG);
		List<Integer> sources = sourceSlots(menu, size, plan);
		if (sources.isEmpty()) {
			int goods = goodsSlots(menu, size);
			if (mode == Mode.NOW) {
				if (goods > 0) {
					clickSell(client, player, menu, size);
				} else {
					afterSale(player);
				}
				return;
			}
			int fill = fillPercent(menu, size);
			if (goods > 0 && fill >= ModConfigManager.CONFIG.sellThreshold) {
				clickSell(client, player, menu, size);
			} else {
				state = State.WAITING;
				pollTimer = POLL;
				progress();
			}
			return;
		}

		if (!hasRoomFor(menu, size, menu.getSlot(sources.get(0)).getItem())) {
			clickSell(client, player, menu, size);
			return;
		}

		batchSignature = signature(menu, size);
		int max = ModConfigManager.CONFIG.sellSpeed.clicksPerStep();
		int clicks = 0;
		for (int index : sources) {
			if (clicks >= max) {
				break;
			}
			ItemStack stack = menu.getSlot(index).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			if (!hasRoomFor(menu, size, stack)) {
				break;
			}
			int before = stack.getCount();
			String id = SellPlan.itemId(stack);
			click(client, player, menu, index, 0, ContainerInput.QUICK_MOVE);
			clicks++;
			ItemStack after = menu.getSlot(index).getItem();
			if (!after.isEmpty() && after.getCount() == before && SellPlan.itemId(after).equals(id)) {
				break; // nothing moved, do not keep clicking
			}
		}
		if (clicks == 0) {
			batchSignature = null;
			clickSell(client, player, menu, size);
			return;
		}
		cooldown = ModConfigManager.CONFIG.sellSpeed.delayTicks();
	}

	/** Menu slots holding stacks the plan sells, grouped by item like a shift double click. */
	private static List<Integer> sourceSlots(AbstractContainerMenu menu, int size, SellPlan plan) {
		List<Integer> slots = new ArrayList<>();
		for (int i = size; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (slot.container instanceof Inventory && slot.hasItem() && plan.sells(slot.getContainerSlot())) {
				slots.add(i);
			}
		}
		slots.sort(Comparator.comparing((Integer i) -> SellPlan.itemId(menu.getSlot(i).getItem())).thenComparing(i -> i));
		return slots;
	}

	/** Empties the cursor first so nothing is ever dropped or clicked onto the button. True when it acted. */
	private static boolean handleCursor(Minecraft client, LocalPlayer player, AbstractContainerMenu menu, int size) {
		ItemStack cursor = menu.getCarried();
		if (cursor.isEmpty()) {
			buttonGuard = false;
			return false;
		}
		if (buttonGuard) {
			// The "button" behaved like a normal item and is now on the cursor: put it back.
			buttonGuard = false;
			click(client, player, menu, buttonSlot, 0, ContainerInput.PICKUP);
			cooldown = 4;
			if (++buttonProblems >= 3) {
				stop("Slot " + buttonSlot + " ist kein Verkaufs-Button.", false);
			}
			return true;
		}
		for (int i = 0; i < size; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (i != buttonSlot && (stack.isEmpty() || (ItemStack.isSameItemSameComponents(stack, cursor) && stack.getCount() < stack.getMaxStackSize()))) {
				click(client, player, menu, i, 0, ContainerInput.PICKUP);
				cooldown = 2;
				return true;
			}
		}
		for (int i = size; i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (stack.isEmpty() || (ItemStack.isSameItemSameComponents(stack, cursor) && stack.getCount() < stack.getMaxStackSize())) {
				click(client, player, menu, i, 0, ContainerInput.PICKUP);
				cooldown = 2;
				return true;
			}
		}
		stop("Ein Stapel am Mauszeiger passt nirgends hin. Gestoppt, damit nichts fallen gelassen wird.", false);
		return true;
	}

	// ------------------------------------------------------------------ selling

	private static void clickSell(Minecraft client, LocalPlayer player, AbstractContainerMenu menu, int size) {
		if (!menu.getCarried().isEmpty()) {
			return;
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, ItemStack> examples = new LinkedHashMap<>();
		int items = 0;
		for (int i = 0; i < size; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (i == buttonSlot || stack.isEmpty()) {
				continue;
			}
			String id = SellPlan.itemId(stack);
			counts.merge(id, stack.getCount(), Integer::sum);
			examples.putIfAbsent(id, stack.copy());
			items += stack.getCount();
		}
		if (items == 0) {
			afterSale(player);
			return;
		}
		if (sellRetries == 0) {
			earlyIncome = -1;
		}
		pendingSaleDraft = new Sale(counts, examples, items);
		click(client, player, menu, buttonSlot, 0, ContainerInput.PICKUP);
		buttonGuard = true;
		state = State.SELLING;
		sellWait = SELL_TIMEOUT;
		// Give the server time to answer before a stack on the cursor counts as "not a button".
		cooldown = 10;
		progress();
	}

	private static Sale pendingSaleDraft;
	/** Payout that arrived while the sale click was still being processed. */
	private static double earlyIncome = -1;

	private static void sellingStep(Minecraft client, LocalPlayer player, AbstractContainerMenu menu) {
		int size = containerSize(menu);
		if (!menu.getCarried().isEmpty()) {
			handleCursor(client, player, menu, size);
			state = State.INSERTING;
			return;
		}
		buttonGuard = false;
		if (goodsSlots(menu, size) == 0) {
			finishSale();
			afterSale(player);
			return;
		}
		if (--sellWait > 0) {
			return;
		}
		if (++sellRetries >= 3) {
			stop("Der Verkaufs-Button reagiert nicht (Slot " + buttonSlot + ").", false);
			return;
		}
		clickSell(client, player, menu, size);
	}

	private static void finishSale() {
		Sale sale = pendingSaleDraft;
		pendingSaleDraft = null;
		sellRetries = 0;
		buttonProblems = 0;
		if (sale == null) {
			return;
		}
		sales++;
		runSales++;
		itemsSold += sale.items();
		if (pendingSale != null) {
			announce(pendingSale, -1);
			pendingSale = null;
		}
		if (earlyIncome > 0) {
			announce(sale, earlyIncome);
			earlyIncome = -1;
		} else {
			pendingSale = sale;
			incomeTicks = INCOME_WINDOW;
		}
		progress();
	}

	private static void announce(Sale sale, double amount) {
		ModConfig config = ModConfigManager.CONFIG;
		if (amount > 0) {
			income += amount;
			incomeKnown = true;
			MoneyTracker.exact(MoneyTracker.SELL, Math.round(amount));
			if (sale.counts().size() == 1) {
				Map.Entry<String, Integer> only = sale.counts().entrySet().iterator().next();
				config.sellPrices.put(only.getKey(), amount / only.getValue());
				ModConfigManager.save();
			}
		}
		List<String> parts = new ArrayList<>();
		sale.counts().forEach((id, count) -> parts.add(formatCount(count) + " " + sale.examples().get(id).getHoverName().getString()));
		MutableComponent line = Component.literal(String.join(", ", parts)).withStyle(ChatFormatting.WHITE);
		if (amount > 0) {
			line.append(Component.literal("  +" + formatMoney(amount)).withStyle(ChatFormatting.GREEN));
		}
		info(Component.literal("Verkauft: ").withStyle(ChatFormatting.GRAY).append(line));
	}

	// ------------------------------------------------------------------ AFK waiting and pausing

	private static void waitingStep(Minecraft client, LocalPlayer player, AbstractContainerMenu menu) {
		if (menu == null) {
			String interruption = interruption(client, player, true);
			if (interruption != null) {
				pause(interruption);
				return;
			}
		}
		if (--pollTimer > 0) {
			return;
		}
		pollTimer = POLL;
		progress();
		SellPlan plan = SellPlan.compute(player.getInventory(), ModConfigManager.CONFIG);
		if (plan.isEmpty()) {
			return;
		}
		waitingShown = false;
		if (menu != null) {
			state = State.INSERTING;
		} else {
			open(player);
		}
	}

	private static void pausedStep(Minecraft client, LocalPlayer player) {
		boolean moved = lastPos != null && player.position().distanceToSqr(lastPos) > 0.0025;
		if (moved || interruption(client, player, false) != null) {
			stillTicks = 0;
			return;
		}
		if (++stillTicks >= RESUME_STILL_TICKS) {
			anchor = player.position();
			state = State.WAITING;
			pollTimer = 0;
			pauseReason = "";
		}
	}

	/** Why the AFK farm should not open a menu right now, or null. */
	private static String interruption(Minecraft client, LocalPlayer player, boolean checkMove) {
		if (client.gui.screen() instanceof ChatScreen) {
			return "du schreibst";
		}
		if (client.gui.screen() != null) {
			return "ein Fenster ist offen";
		}
		if (player.hurtTime > 0 || player.getHealth() < lastHealth - 0.01f) {
			return "Schaden bekommen";
		}
		if (checkMove && anchor != null && player.position().distanceTo(anchor) > MOVE_LIMIT) {
			return "du bewegst dich";
		}
		return null;
	}

	private static void pause(String reason) {
		state = State.PAUSED;
		pauseReason = reason;
		stillTicks = 0;
		boundId = -1;
		resetMenuState();
	}

	// ------------------------------------------------------------------ helpers

	private static int goodsSlots(AbstractContainerMenu menu, int size) {
		int goods = 0;
		for (int i = 0; i < size; i++) {
			if (i != buttonSlot && menu.getSlot(i).hasItem()) {
				goods++;
			}
		}
		return goods;
	}

	private static int fillPercent(AbstractContainerMenu menu, int size) {
		int usable = 0;
		int filled = 0;
		for (int i = 0; i < size; i++) {
			if (i == buttonSlot) {
				continue;
			}
			usable++;
			if (menu.getSlot(i).hasItem()) {
				filled++;
			}
		}
		return usable == 0 ? 100 : filled * 100 / usable;
	}

	private static boolean hasRoomFor(AbstractContainerMenu menu, int size, ItemStack stack) {
		for (int i = 0; i < size; i++) {
			if (i == buttonSlot) {
				continue;
			}
			ItemStack slot = menu.getSlot(i).getItem();
			if (slot.isEmpty() || (slot.getCount() < slot.getMaxStackSize() && ItemStack.isSameItemSameComponents(slot, stack))) {
				return true;
			}
		}
		return false;
	}

	private static String signature(AbstractContainerMenu menu, int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = size; i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			sb.append(stack.isEmpty() ? "-" : SellPlan.itemId(stack) + "x" + stack.getCount()).append(';');
		}
		return sb.toString();
	}

	private static void click(Minecraft client, LocalPlayer player, AbstractContainerMenu menu, int slot, int button, ContainerInput type) {
		if (client.gameMode != null) {
			//? if modern
			client.gameMode.handleContainerInput(menu.containerId, slot, button, type, player);
			//? else
			client.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, player);
			//? end
		}
	}

	private static void closeMenu() {
		Minecraft client = Minecraft.getInstance();
		if (boundScreen() != null && client.player != null) {
			boundId = -1;
			client.player.closeContainer();
		}
	}

	private static void resetMenuState() {
		batchSignature = null;
		moveFailures = 0;
		buttonGuard = false;
		sellRetries = 0;
		pendingSaleDraft = null;
		waitingShown = false;
	}

	private static void progress() {
		watchdog = 0;
	}

	public static String formatMoney(double amount) {
		DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.GERMANY));
		return "$" + format.format(Math.round(amount));
	}

	public static String formatCount(int count) {
		return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(count);
	}

	private static void info(String message) {
		info(Component.literal(message).withStyle(ChatFormatting.WHITE));
	}

	private static void info(Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			//? if modern
			player.sendSystemMessage(Component.literal("[Verkaufen] ").withStyle(ChatFormatting.GOLD).append(message));
			//? else
			player.displayClientMessage(Component.literal("[Verkaufen] ").withStyle(ChatFormatting.GOLD).append(message), false);
			//? end
		}
	}
}
