package com.hugosmpenhanced.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.debug.DebugDump;
import com.hugosmpenhanced.client.feature.farm.FarmTracker;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.keywords.KeywordAlarm;
import com.hugosmpenhanced.client.feature.money.MoneyScreen;
import com.hugosmpenhanced.client.feature.money.MoneyTracker;
import com.hugosmpenhanced.client.feature.orders.OrdersHelper;
import com.hugosmpenhanced.client.feature.pay.PayCommand;
import com.hugosmpenhanced.client.feature.pay.PayLog;
import com.hugosmpenhanced.client.feature.pay.PayLogScreen;
import com.hugosmpenhanced.client.feature.spawnerstorage.SpawnerStorage;
import com.hugosmpenhanced.client.feature.spawnerstorage.SpawnerStorageScreen;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.hud.HudEditorScreen;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.client.util.MenuContext;
import com.hugosmpenhanced.client.util.ServerCheck;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The economy tools of 0.1.5: /pay warning and preview, payment log, money tracker with sources,
 * orders helper, spawner storage, keyword alarm, farm tracker, the HUD editor with all of them,
 * the debug dump and every new settings tab. Checks the logic, then takes a screenshot of each.
 */
@SuppressWarnings("UnstableApiUsage")
public class EconomyGameTest implements FabricClientGameTest {
	private static final String SCAMMER = "ScamTester";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		checkParsing();
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("gamerule doDaylightCycle false");
			singleplayer.getServer().runCommand("time set 6000");
			context.runOnClient(client -> {
				ServerCheck.forceHugoSmp = true;
				HugoGuard.forceActive = true;
				ModConfigManager.CONFIG.hugoGuardEnabled = true;
				HugoGuard.putForTest(new HugoGuard.Entry(SCAMMER, UUID.randomUUID(), HugoGuard.Status.SCAMMER, 7, -40, "Betrug beim Handel"));
				HugoGuard.putForTest(new HugoGuard.Entry("FairerHaendler", UUID.randomUUID(), HugoGuard.Status.TRUSTED, 0, 25, null));
				PayLog.clear();
				MoneyTracker.clearHistory();
				MoneyTracker.resetForTest();
				FarmTracker.reset();
				SpawnerStorage.forgetAll();
			});
			context.waitTicks(5);

			payLog(context);
			moneyTracker(context);
			payWarning(context);
			orders(context, singleplayer);
			spawners(context);
			keywords(context);
			farm(context);
			hud(context);
			debug(context);
			settings(context);
		} finally {
			context.runOnClient(client -> {
				ServerCheck.forceHugoSmp = false;
				HugoGuard.forceActive = false;
				OrdersHelper.forceActive = false;
				KeywordAlarm.forceActive = false;
				ModConfigManager.CONFIG.moneyHudOn = false;
				ModConfigManager.CONFIG.spawnerHudOn = false;
				ModConfigManager.CONFIG.farmHudOn = false;
				ModConfigManager.CONFIG.keywordAlarmWords.clear();
				ModConfigManager.save();
			});
		}
	}

	private static void checkParsing() {
		PayCommand pay = PayCommand.parse("/pay Steve 10k");
		check(pay != null && pay.name().equals("Steve") && pay.amount() == 10_000, "pay 10k: " + pay);
		check(PayCommand.parse("/pay Steve") != null && PayCommand.parse("/pay Steve").amountText() == null, "pay without amount");
		check(PayCommand.parse("/essentials:pay Steve 1.500") != null && PayCommand.parse("/essentials:pay Steve 1.500").amount() == 1_500, "namespaced pay");
		check(PayCommand.parse("/msg Steve 10k") == null, "msg is no payment");
		check(PayCommand.parse("pay Steve 10k") == null, "chat text is no payment");
		check(Money.compact(260_900_000).equals("$260,9 Mio"), Money.compact(260_900_000));
		check(Money.compact(12_500).equals("$12,5k"), Money.compact(12_500));
		check(Money.compact(950).equals("$950"), Money.compact(950));
		List<String> words = List.of("verkauf elytra", "suche mending");
		check("verkauf elytra".equals(KeywordAlarm.match("[VIP] Kai: Verkaufe meine Elytra für 5m", words)), "keyword in any order");
		check(KeywordAlarm.match("Kai: Kaufe Elytra", words) == null, "kaufe is not verkauf");
		check("suche mending".equals(KeywordAlarm.match("SUCHE Mending-Buch", words)), "keyword case and hyphen");
		check(KeywordAlarm.match("Kai: Verkaufe Schwert", words) == null, "all words needed");
	}

	private static void payLog(ClientGameTestContext context) {
		context.runOnClient(client -> {
			PayLog.onMessage(Component.literal("KaiDerBauer hat dir $20.000 gezahlt."));
			PayLog.onCommand("pay FairerHaendler 2500");
			PayLog.onMessage(Component.literal("Du hast FairerHaendler $2.500 gezahlt."));
			PayLog.onCommand("pay NiemandDa 999");
			PayLog.onMessage(Component.literal("Dieser Spieler ist nicht online."));
			PayLog.onMessage(Component.literal("Du hast 1.250.000$ von Mika_77 erhalten."));
		});
		List<PayLog.Entry> entries = context.computeOnClient(client -> List.copyOf(PayLog.entries()));
		System.out.println("PAYLOG " + entries);
		check(entries.size() == 3, "three payments logged, the refused one dropped: " + entries);
		check(entries.get(0).incoming() && entries.get(0).amount() == 20_000 && entries.get(0).name().equals("KaiDerBauer"), "incoming 20k");
		check(!entries.get(1).incoming() && entries.get(1).confirmed() && entries.get(1).amount() == 2_500, "outgoing confirmed 2.5k");
		check(entries.get(2).amount() == 1_250_000 && entries.get(2).name().equals("Mika_77"), "incoming 1.25M");
		context.getInput().setCursorPos(0, 0);
		context.setScreen(() -> new PayLogScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("economy-zahlungen");
		context.setScreen(() -> null);
	}

	private static void moneyTracker(ClientGameTestContext context) {
		context.runOnClient(client -> {
			long t = System.currentTimeMillis() - 45_000;
			MoneyTracker.resetForTest();
			MoneyTracker.observe(260_900_000, t);
			// An exact payment: its sidebar change is not counted twice.
			MoneyTracker.exact(MoneyTracker.PAY, 50_000);
			MoneyTracker.observe(260_950_000, t + 1_000);
			// While a spawner menu is open.
			MenuContext.set("Zombie Spawner");
			MoneyTracker.observe(262_000_000, t + 2_000);
			MenuContext.set(null);
			// Sidebar first, then the exact sale: the guess is replaced.
			MoneyTracker.observe(262_150_000, t + 38_000);
			MoneyTracker.exact(MoneyTracker.SELL, 150_000);
			// Rounding of the sidebar below 10.000 is ignored.
			MoneyTracker.observe(262_155_000, t + 40_000);
			// Spending with nothing open.
			MoneyTracker.observe(261_655_000, t + 44_000);
		});
		MoneyTracker.Book book = context.computeOnClient(client -> MoneyTracker.session());
		System.out.println("MONEY income=" + book.income + " spent=" + book.spent);
		check(book.income.getOrDefault(MoneyTracker.PAY, 0L) == 50_000, "pay 50k counted once");
		check(book.income.getOrDefault("Spawner", 0L) == 1_050_000, "spawner 1.05M");
		check(book.income.getOrDefault(MoneyTracker.SELL, 0L) == 150_000, "sale exact");
		check(!book.income.containsKey(MoneyTracker.OTHER), "no leftover guess in Sonstiges: " + book.income);
		check(book.spent.getOrDefault(MoneyTracker.OTHER, 0L) == 500_000, "500k spent without source");
		check(context.computeOnClient(client -> MoneyTracker.sessionChange()) == 755_000, "session change");
		context.setScreen(() -> new MoneyScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("economy-geld");
		context.setScreen(() -> null);
	}

	private static void payWarning(ClientGameTestContext context) {
		context.setScreen(() -> new ChatScreen("/pay " + SCAMMER + " 10k", false));
		context.waitTicks(5);
		context.takeScreenshot("economy-pay-scammer");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(3);
		context.runOnClient(client -> check(client.screen instanceof ChatScreen, "first Enter on a scammer payment must not send"));
		context.takeScreenshot("economy-pay-scammer-enter");
		context.setScreen(() -> null);
		context.setScreen(() -> new ChatScreen("/pay FairerHaendler 2,5k", false));
		context.waitTicks(5);
		context.takeScreenshot("economy-pay-vorschau");
		context.setScreen(() -> null);
	}

	private static void orders(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		OrdersHelper.forceActive = true;
		ItemStack diamonds = order(Items.DIAMOND, "Diamant", "Preis pro Stück: $3.400", "Geliefert: 120/500", "Von: Mika_77");
		ItemStack iron = order(Items.IRON_INGOT, "Eisenbarren", "Preis pro Stück: $250", "Geliefert: 10/2000", "Von: KaiDerBauer");
		ItemStack wheat = order(Items.WHEAT, "Weizen", "Preis pro Stück: $40", "Geliefert: 0/640", "Von: Bauer_Bob");
		// Through the server, so the inventory is not synced away again.
		singleplayer.getServer().runCommand("clear @p");
		singleplayer.getServer().runCommand("item replace entity @p inventory.0 with diamond 64");
		singleplayer.getServer().runCommand("item replace entity @p inventory.1 with diamond 30");
		singleplayer.getServer().runCommand("item replace entity @p inventory.2 with iron_ingot 64");
		context.waitTicks(5);
		context.runOnClient(client -> {
			var inventory = client.player.getInventory();
			SimpleContainer page = new SimpleContainer(54);
			page.setItem(10, diamonds);
			page.setItem(11, iron);
			page.setItem(12, wheat);
			client.setScreen(new ContainerScreen(new ChestMenu(MenuType.GENERIC_9x6, 0, inventory, page, 6), inventory,
				Component.literal("Aufträge")));
		});
		context.waitTicks(5);
		OrdersHelper.Order d = context.computeOnClient(client -> OrdersHelper.order(diamonds));
		OrdersHelper.Order i = context.computeOnClient(client -> OrdersHelper.order(iron));
		OrdersHelper.Order w = context.computeOnClient(client -> OrdersHelper.order(wheat));
		System.out.println("ORDERS " + d + " / " + i + " / " + w);
		check(d != null && d.have() == 94 && d.deliverable() == 94 && d.value() == 94 * 3_400 && d.best(), "diamonds 94 deliverable, best");
		check(i != null && i.deliverable() == 64 && !i.best(), "iron 64");
		check(w != null && w.deliverable() == 0, "no wheat");
		hoverSlotOf(context, diamonds);
		context.takeScreenshot("economy-auftraege");
		String tooltip = context.computeOnClient(client -> diamonds.getTooltipLines(Item.TooltipContext.of(client.level), client.player,
			TooltipFlag.NORMAL).stream().map(Component::getString).collect(Collectors.joining("\n")));
		check(tooltip.contains("Lieferbar: 94") && tooltip.contains("$319.600"), "order tooltip: " + tooltip);
		context.getInput().setCursorPos(0, 0);
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("clear @p");
	}

	private static ItemStack order(Item item, String name, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withColor(ChatFormatting.AQUA).withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lore)
			.map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY)).toList()));
		return stack;
	}

	private static void spawners(ClientGameTestContext context) {
		context.runOnClient(client -> {
			long now = System.currentTimeMillis();
			String world = client.level.dimension().identifier().toString();
			BlockPos zombie = new BlockPos(120, 64, -40);
			// First visit two hours ago: 1.000 of 2.304; now 1.900.
			SpawnerStorage.clicked(world, zombie, "Zombie");
			SpawnerStorage.record("Zombie Spawner (x4)", storageSlots("Lager: 1.000/2.304"), now - 2 * 3_600_000L);
			SpawnerStorage.clicked(world, zombie, "Zombie");
			SpawnerStorage.record("Zombie Spawner (x4)", storageSlots("Lager: 1.900/2.304"), now);
			BlockPos skeleton = new BlockPos(-300, 70, 15);
			SpawnerStorage.clicked(world, skeleton, "Skelett");
			SpawnerStorage.record("Skelett Spawner", storageSlots("Lager: 200/1.152"), now - 6 * 3_600_000L);
			SpawnerStorage.clicked(world, skeleton, "Skelett");
			SpawnerStorage.record("Skelett Spawner", storageSlots("Lager: 500/1.152"), now - 3_600_000L);
		});
		List<SpawnerStorage.Spawner> spawners = context.computeOnClient(client -> List.copyOf(SpawnerStorage.spawners()));
		SpawnerStorage.Spawner zombie = spawners.get(0);
		System.out.println("SPAWNER " + zombie.label() + " rate=" + zombie.rate + " cap=" + zombie.capacity);
		check(zombie.stack == 4 && zombie.capacity == 2_304 && Math.abs(zombie.rate - 450) < 1, "zombie x4, 450 items/h");
		check(zombie.label().equals("4× Zombie"), zombie.label());
		check(spawners.get(1).percent(System.currentTimeMillis()) > 45, "skeleton projected forward");
		context.setScreen(() -> new SpawnerStorageScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("economy-spawner");
		context.setScreen(() -> null);
	}

	private static List<Slot> storageSlots(String lore) {
		SimpleContainer menu = new SimpleContainer(54);
		ItemStack info = new ItemStack(Items.CHEST);
		info.set(DataComponents.CUSTOM_NAME, Component.literal("Lager"));
		info.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore))));
		menu.setItem(49, info);
		menu.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 64));
		List<Slot> slots = new java.util.ArrayList<>();
		for (int i = 0; i < 54; i++) {
			slots.add(new Slot(menu, i, 0, 0));
		}
		return slots;
	}

	private static void keywords(ClientGameTestContext context) {
		context.runOnClient(client -> {
			KeywordAlarm.forceActive = true;
			ModConfigManager.CONFIG.keywordAlarmEnabled = true;
			ModConfigManager.CONFIG.keywordAlarmWords.clear();
			ModConfigManager.CONFIG.keywordAlarmWords.add("verkauf elytra");
			String line = "[VIP] KaiDerBauer: Verkaufe meine Elytra für 5m, /msg me";
			client.gui.getChat().addMessage(Component.literal("Mika_77: wer hat Eisen?"));
			KeywordAlarm.check(line);
			client.gui.getChat().addMessage(Component.literal(line));
		});
		context.setScreen(() -> new ChatScreen("", false));
		context.waitTicks(5);
		context.takeScreenshot("economy-stichwort");
		context.setScreen(() -> null);
	}

	private static void farm(ClientGameTestContext context) {
		context.runOnClient(client -> {
			FarmTracker.reset();
			ModConfigManager.CONFIG.sellPrices.put("minecraft:bone", 40.0);
			for (int i = 0; i < 30; i++) {
				FarmTracker.add(new ItemStack(Items.BONE), 64);
				FarmTracker.add(new ItemStack(Items.ARROW), 40);
			}
			FarmTracker.add(new ItemStack(Items.BOW), 1);
		});
		check(context.computeOnClient(client -> FarmTracker.totalItems()) == 30 * 104 + 1, "farm count");
		check(context.computeOnClient(client -> FarmTracker.value()) == 30 * 64 * 40.0, "farm value from sell prices");
	}

	private static void hud(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.moneyHudOn = true;
			ModConfigManager.CONFIG.spawnerHudOn = true;
			ModConfigManager.CONFIG.farmHudOn = true;
			MoneyTracker.observe(261_655_000, System.currentTimeMillis());
		});
		context.waitTicks(40);
		context.takeScreenshot("economy-hud");
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("economy-hud-editor");
		context.setScreen(() -> null);
	}

	private static void debug(ClientGameTestContext context) {
		Path folder = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced").resolve("debug");
		context.runOnClient(client -> DebugDump.all());
		context.waitTicks(3);
		try (var files = Files.list(folder)) {
			Path dump = files.filter(p -> p.getFileName().toString().endsWith("-alles.txt")).findFirst().orElseThrow();
			String text = Files.readString(dump);
			System.out.println("DEBUG dump " + dump.getFileName() + " " + text.length() + " chars");
			check(text.contains("== Scoreboard") && text.contains("== Chat"), "debug dump has scoreboard and chat");
		} catch (Exception e) {
			throw new AssertionError("debug dump missing", e);
		}
		String clipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
		check(clipboard.startsWith("HugoSMP Enhanced"), "debug dump in the clipboard");
	}

	private static void settings(ClientGameTestContext context) {
		for (String tab : List.of("Geld", "Zahlungen", "Aufträge", "Spawner-Lager", "Farm")) {
			context.setScreen(() -> new SettingsScreen(null).select("Wirtschaft", tab));
			context.waitTicks(4);
			context.takeScreenshot("economy-settings-" + tab.toLowerCase().replace('ä', 'a'));
		}
		context.setScreen(() -> new SettingsScreen(null).select("Chat", "Stichwörter"));
		context.waitTicks(4);
		context.takeScreenshot("economy-settings-stichwoerter");
		context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Menü"));
		context.waitTicks(4);
		context.takeScreenshot("economy-settings-allgemein");
		context.setScreen(() -> null);
	}

	private static void hoverSlotOf(ClientGameTestContext context, ItemStack stack) {
		double[] pos = context.computeOnClient(client -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) client.screen;
			double scale = client.getWindow().getGuiScale();
			for (Slot slot : ((ContainerScreen) client.screen).getMenu().slots) {
				if (slot.getItem() == stack) {
					return new double[] {(screen.hugosmpenhanced$getLeftPos() + slot.x + 8) * scale,
						(screen.hugosmpenhanced$getTopPos() + slot.y + 8) * scale};
				}
			}
			throw new AssertionError("Stack not in the menu");
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
