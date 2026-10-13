package com.hugosmpenhanced.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.streamer.ChatPatterns;
import com.hugosmpenhanced.client.feature.streamer.DonationAuction;
import com.hugosmpenhanced.client.feature.streamer.Giveaway;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.feature.streamer.StreamerFeature;
import com.hugosmpenhanced.client.feature.streamer.StreamerScreen;
import com.hugosmpenhanced.client.feature.streamer.WheelScreen;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Streamer tools: payments arrive as server messages and become bids, a fake payment in player chat
 * does nothing, the overlay counts down and shows the winner, losers can be paid back; giveaway
 * joins come from /msg and Twitch, and the wheel stops on the winner it drew.
 */
@SuppressWarnings("UnstableApiUsage")
public class StreamerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> checkText());
		var config = ModConfigManager.CONFIG;
		config.streamerAuctionSeconds = 90;
		config.streamerStartBid = 10_000;
		config.streamerMinStep = 5_000;
		config.streamerSumPayments = true;
		config.streamerAnnounce = false;
		config.streamerTwitchChannel = "";
		config.streamerWheelSeconds = 5;
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getServer().runCommand("gamerule sendCommandFeedback false");
			singleplayer.getServer().runCommand("time set 6000");
			singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:netherite_sword[enchantments={sharpness:5,looting:3}]");
			singleplayer.getClientWorld().waitForChunksRender();
			context.waitTicks(10);

			// ---------------------------------------------------------- auction setup
			context.runOnClient(client -> {
				setStatic(StreamerScreen.class, "auctionItem", ItemStack.EMPTY);
				client.setScreen(new StreamerScreen(null));
			});
			waitMs(context, 500);
			context.takeScreenshot("streamer-auktion-einrichten-leer");
			// "Aus dem Inventar", then the sword in hotbar slot 1, with real mouse clicks.
			int[] fromInventory = context.computeOnClient(client -> {
				Object screen = client.screen;
				int fx = (int) getField(screen, "fx");
				int fy = (int) getField(screen, "fy");
				int fh = (int) getField(screen, "fh");
				int box = (boolean) call(screen, "compact") ? Math.max(56, Math.min(112, fh - 132)) : 112;
				return new int[] {fx + 10 + box / 2, fy + 52 + box + 4 + 19 + 8};
			});
			clickGui(context, fromInventory[0], fromInventory[1]);
			waitMs(context, 300);
			check(context.computeOnClient(c -> (boolean) getField(c.screen, "picking")), "Aus dem Inventar opens the picker");
			context.takeScreenshot("streamer-auktion-item-waehlen");
			int[] slot = context.computeOnClient(client -> new int[] {(client.screen.width - 196) / 2 + 8 + 9, (client.screen.height - 136) / 2 + 20 + 3 * 20 + 3 + 9});
			clickGui(context, slot[0], slot[1]);
			waitMs(context, 300);
			check(context.computeOnClient(c -> !(boolean) getField(c.screen, "picking")
				&& ((ItemStack) getStatic(StreamerScreen.class, "auctionItem")).is(net.minecraft.world.item.Items.NETHERITE_SWORD)), "picked item becomes the auction item");
			waitMs(context, 600);
			context.takeScreenshot("streamer-auktion-einrichten");
			guiScale(context, 1);
			waitMs(context, 300);
			context.takeScreenshot("streamer-auktion-einrichten-gross");
			guiScale(context, 2);

			// ---------------------------------------------------------- live auction
			context.runOnClient(client -> {
				DonationAuction.start(client.player.getMainHandItem(), "Netherite-Schwert", false);
				client.setScreen(null);
			});
			tellraw(singleplayer, "KaiDerBauer hat dir $20.000 gezahlt.");
			waitMs(context, 700);
			tellraw(singleplayer, "Du hast $35.000 von Alexandra erhalten.");
			waitMs(context, 500);
			tellraw(singleplayer, "[Bank] noor_mc hat dir $12.500 gezahlt.");
			// A viewer typing a payment text into chat must not count.
			tellraw(singleplayer, "<Efe2010> Efe2010 hat dir $1.000.000 gezahlt.");
			waitMs(context, 400);
			check(context.computeOnClient(c -> "Alexandra".equals(DonationAuction.leader())), "Alexandra leads with 35k");
			check(context.computeOnClient(c -> DonationAuction.total("Efe2010")) == 0, "fake chat payment ignored");
			check(context.computeOnClient(c -> DonationAuction.bidderCount()) == 3, "three bidders");
			tellraw(singleplayer, "KaiDerBauer hat dir $25.000 gezahlt.");
			waitMs(context, 300);
			check(context.computeOnClient(c -> "KaiDerBauer".equals(DonationAuction.leader())
				&& DonationAuction.total("KaiDerBauer") == 45_000), "Kai sums to 45k and leads");
			waitMs(context, 1200);
			context.takeScreenshot("streamer-auktion-live");
			// 1080p at GUI scale 4 is 427x240: control bar, overlay and an open giveaway must not overlap.
			context.runOnClient(client -> {
				Giveaway.clear();
				Giveaway.setOpen(true);
				client.setScreen(new StreamerScreen(null));
			});
			tellraw(singleplayer, "[MaxBlock -> mir] join");
			guiScale(context, 3);
			waitMs(context, 700);
			context.takeScreenshot("streamer-auktion-live-steuerung-klein");
			context.runOnClient(client -> client.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			waitMs(context, 400);
			context.takeScreenshot("streamer-auktion-live-chat-knopf");
			context.runOnClient(client -> {
				Giveaway.setOpen(false);
				Giveaway.clear();
				client.setScreen(null);
			});
			guiScale(context, 2);
			guiScale(context, 1);
			waitMs(context, 300);
			context.takeScreenshot("streamer-auktion-live-gross");
			guiScale(context, 2);

			context.runOnClient(client -> DonationAuction.finish());
			waitMs(context, 1500);
			context.takeScreenshot("streamer-auktion-verkauft");
			Map<String, Long> refunds = context.computeOnClient(c -> DonationAuction.refunds());
			System.out.println("STREAMER refunds " + refunds);
			check(refunds.equals(Map.of("Alexandra", 35_000L, "noor_mc", 12_500L)), "losers to pay back");
			// The bar was closed: the chat shows a button that brings it back.
			guiScale(context, 3);
			context.runOnClient(client -> client.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			waitMs(context, 400);
			context.takeScreenshot("streamer-auktion-ende-chat-knopf");
			int[] chip = context.computeOnClient(client -> {
				int w = client.font.width("Auktion vorbei") + 20;
				return new int[] {client.screen.width - 4 - w / 2, client.screen.height - 12 - 2 - 14 - 2 + 7};
			});
			clickGui(context, chip[0], chip[1]);
			waitMs(context, 500);
			check(context.computeOnClient(c -> StreamerScreen.liveBarTop(c.screen) >= 0), "chat button opens the control bar");
			context.takeScreenshot("streamer-auktion-ende-steuerung-klein");
			guiScale(context, 2);
			waitMs(context, 300);
			context.takeScreenshot("streamer-auktion-ende-steuerung");
			context.runOnClient(client -> check("Alexandra".equals(DonationAuction.nextWinner()), "place two moves up"));
			context.runOnClient(client -> setField(client.screen, "confirmRefund", true));
			waitMs(context, 200);
			context.takeScreenshot("streamer-auktion-zurueckzahlen");
			int[] pay = context.computeOnClient(client -> {
				int h = 58 + DonationAuction.refunds().size() * 11;
				return new int[] {(client.screen.width - 240) / 2 + 240 - 8 - 65, (client.screen.height - h) / 2 + h - 22 + 8};
			});
			clickGui(context, pay[0], pay[1]);
			waitMs(context, 300);
			check(context.computeOnClient(c -> DonationAuction.refunded() && !(boolean) getField(c.screen, "confirmRefund")), "pay back button in the dialog works");
			context.takeScreenshot("streamer-auktion-zurueckgezahlt");
			context.runOnClient(client -> {
				DonationAuction.close();
				client.setScreen(null);
			});

			// ---------------------------------------------------------- cancel with money in: two clicks, then everyone can be paid back
			context.runOnClient(client -> {
				DonationAuction.start(client.player.getMainHandItem(), "Abbruch-Schwert", false);
				client.setScreen(new StreamerScreen(null));
			});
			tellraw(singleplayer, "KaiDerBauer hat dir $20.000 gezahlt.");
			waitMs(context, 400);
			context.runOnClient(client -> {
				runLiveButton(client.screen, "Abbrechen");
				check(DonationAuction.state() == DonationAuction.State.RUNNING, "first click on Abbrechen only arms it");
				runLiveButton(client.screen, "Wirklich abbrechen?");
				check(DonationAuction.state() == DonationAuction.State.ENDED && DonationAuction.cancelled()
					&& DonationAuction.winner() == null && DonationAuction.refunds().equals(Map.of("KaiDerBauer", 20_000L)),
					"cancelled auction keeps the payers for paying back");
			});
			waitMs(context, 900);
			context.takeScreenshot("streamer-auktion-abgebrochen");
			context.runOnClient(client -> {
				DonationAuction.close();
				client.setScreen(null);
			});

			// ---------------------------------------------------------- test run
			context.runOnClient(client -> StreamerFeature.startDemo(client.player.getMainHandItem(), "Testlauf-Schwert"));
			waitMs(context, 16_000);
			context.takeScreenshot("streamer-testlauf");
			check(context.computeOnClient(c -> DonationAuction.bidderCount()) >= 4, "test run bids arrive");
			context.runOnClient(client -> DonationAuction.cancel());
			check(!context.computeOnClient(c -> java.nio.file.Files.exists(DonationAuction.logFile())
				&& readLog().contains("Testlauf")), "the test run writes nothing to the payment log");

			// ---------------------------------------------------------- giveaway
			context.runOnClient(client -> {
				Giveaway.clear();
				Giveaway.setPrize(new ItemStack(net.minecraft.world.item.Items.ELYTRA), "Elytra");
				Giveaway.setOpen(true);
			});
			tellraw(singleplayer, "[KaiDerBauer -> mir] join");
			tellraw(singleplayer, "Alexandra flüstert dir zu: !join");
			tellraw(singleplayer, "[noor_mc -> mir] hallo, wann geht es los?");
			tellraw(singleplayer, "[KaiDerBauer -> mir] join");
			waitMs(context, 300);
			check(context.computeOnClient(c -> Giveaway.count(Giveaway.Source.INGAME)) == 2, "two in-game joins, once each");
			context.runOnClient(client -> {
				String[][] twitch = {{"TwitchFan42", "!join"}, {"sunny_tv", "!join Sunny_"}, {"miramaus", "!JOIN"}, {"nachteule", "hallo"},
					{"bergziege_", "!join"}, {"pixelpaul", "!join PaulMC"}, {"lia_draws", "!join"}, {"gamerjonas", "!join"},
					{"kekskruemel", "!join"}, {"TwitchFan42", "!join"}};
				for (String[] line : twitch) {
					twitchMessage(line[0], line[1]);
				}
			});
			check(context.computeOnClient(c -> Giveaway.count(Giveaway.Source.TWITCH)) == 8, "eight Twitch joins");
			for (String name : List.of("Efe2010", "Sunny_", "MaxBlock", "Lenaaa", "dieter_mc")) {
				tellraw(singleplayer, "[" + name + " -> mir] join");
			}
			waitMs(context, 300);
			context.runOnClient(client -> {
				setStatic(StreamerScreen.class, "tab", enumValue(StreamerScreen.class, "Tab", "GIVEAWAY"));
				client.setScreen(new StreamerScreen(null));
			});
			waitMs(context, 600);
			int[] prizeButton = context.computeOnClient(client -> new int[] {
				(int) getField(client.screen, "fx") + 10 + 26 + 156 + 80 + 38, (int) getField(client.screen, "fy") + 40 + 10 + 8});
			clickGui(context, prizeButton[0], prizeButton[1]);
			waitMs(context, 300);
			check(context.computeOnClient(c -> (boolean) getField(c.screen, "picking")), "giveaway Aus Inventar opens the picker");
			int[] prizeSlot = context.computeOnClient(client -> new int[] {(client.screen.width - 196) / 2 + 8 + 9, (client.screen.height - 136) / 2 + 20 + 3 * 20 + 3 + 9});
			clickGui(context, prizeSlot[0], prizeSlot[1]);
			waitMs(context, 300);
			check(context.computeOnClient(c -> Giveaway.prize().is(net.minecraft.world.item.Items.NETHERITE_SWORD)), "picked item becomes the giveaway prize");
			context.runOnClient(client -> Giveaway.setPrize(new ItemStack(net.minecraft.world.item.Items.ELYTRA), "Elytra"));
			waitMs(context, 200);
			context.takeScreenshot("streamer-giveaway");

			// ---------------------------------------------------------- wheel
			context.runOnClient(client -> client.setScreen(new WheelScreen(client.screen)));
			waitMs(context, 2200);
			context.takeScreenshot("streamer-gluecksrad-dreht");
			waitMs(context, 5000);
			context.takeScreenshot("streamer-gluecksrad-gewinner");
			context.runOnClient(client -> {
				Giveaway.Participant winner = Giveaway.winner();
				check(winner != null, "a winner is drawn");
				@SuppressWarnings("unchecked")
				List<Giveaway.Participant> wheel = (List<Giveaway.Participant>) getField(client.screen, "wheel");
				int index = (int) call(client.screen, "indexAtPointer");
				System.out.println("STREAMER wheel winner " + winner + " pointer at " + wheel.get(index));
				check(wheel.get(index).key().equals(winner.key()), "the wheel stops on the drawn winner");
			});
			// big giveaway: colours only, the name shows above the pointer
			context.runOnClient(client -> {
				for (int i = 0; i < 60; i++) {
					twitchMessage("zuschauer" + i, "!join");
				}
				client.setScreen(new WheelScreen(null));
			});
			waitMs(context, 2500);
			context.takeScreenshot("streamer-gluecksrad-viele");
			waitMs(context, 4000);
			context.runOnClient(client -> {
				@SuppressWarnings("unchecked")
				List<Giveaway.Participant> wheel = (List<Giveaway.Participant>) getField(client.screen, "wheel");
				int index = (int) call(client.screen, "indexAtPointer");
				check(wheel.get(index).key().equals(Giveaway.winner().key()), "the big wheel also stops on the winner");
				Giveaway.setOpen(false);
			});

			// ---------------------------------------------------------- settings
			context.runOnClient(client -> client.setScreen(new SettingsScreen(null).select("Werkzeuge", "Streamer")));
			waitMs(context, 400);
			context.takeScreenshot("streamer-einstellungen");
			context.runOnClient(client -> client.setScreen(null));
		}
	}

	private static void checkText() {
		check(Money.parse("10k") == 10_000, "10k");
		check(Money.parse("1.500.000") == 1_500_000, "1.500.000");
		check(Money.parse("$2,5 Mio") == 2_500_000, "2,5 Mio");
		check(Money.format(20_000).equals("$20.000"), Money.format(20_000));
		var payment = ChatPatterns.parsePayment("KaiDerBauer hat dir $20.000 gezahlt.");
		check(payment != null && payment.name().equals("KaiDerBauer") && payment.amount() == 20_000, "German payment");
		payment = ChatPatterns.parsePayment("[Bank] You have received $1,250 from Steve_02.");
		check(payment != null && payment.name().equals("Steve_02") && payment.amount() == 1_250, "English payment");
		check(ChatPatterns.parsePayment("<Efe> KaiDerBauer hat dir $20.000 gezahlt.") == null, "chat line is no payment");
		check(ChatPatterns.parsePayment("Kai: hat dir $20.000 gezahlt") == null, "no name, no payment");
		var whisper = ChatPatterns.parseWhisper("[KaiDerBauer -> mir] !join");
		check(whisper != null && whisper.name().equals("KaiDerBauer") && whisper.text().equals("!join"), "whisper");
	}

	private static void tellraw(TestSingleplayerContext singleplayer, String text) {
		singleplayer.getServer().runCommand("tellraw @a " + new com.google.gson.JsonPrimitive(text));
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(client -> {
			client.options.guiScale().set(scale);
			client.resizeDisplay();
		});
	}

	/** A real mouse click at a GUI position, through the window like a player's click. */
	private static void clickGui(ClientGameTestContext context, int guiX, int guiY) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((guiX + 0.5) * scale, (guiY + 0.5) * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
		context.getInput().setCursorPos(0, 0);
	}

	/** Runs the control bar button with this label, like a click on it. */
	private static void runLiveButton(Object screen, String label) {
		List<?> buttons = (List<?>) call(screen, "liveButtons");
		for (Object button : buttons) {
			if (label.equals(call(button, "label"))) {
				((Runnable) call(button, "action")).run();
				return;
			}
		}
		throw new AssertionError("no control bar button " + label);
	}

	private static Object getStatic(Class<?> type, String name) {
		try {
			Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void twitchMessage(String user, String text) {
		try {
			Method method = Giveaway.class.getDeclaredMethod("onTwitchMessage", String.class, String.class, boolean.class);
			method.setAccessible(true);
			method.invoke(null, user, text, false);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static String readLog() {
		try {
			return java.nio.file.Files.readString(DonationAuction.logFile());
		} catch (java.io.IOException e) {
			return "";
		}
	}

	private static void waitMs(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void setStatic(Class<?> type, String name, Object value) {
		try {
			Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			field.set(null, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Object getField(Object target, String name) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static Object call(Object target, String name) {
		try {
			Method method = target.getClass().getDeclaredMethod(name);
			method.setAccessible(true);
			return method.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumValue(Class<?> outer, String simpleName, String value) {
		for (Class<?> inner : outer.getDeclaredClasses()) {
			if (inner.getSimpleName().equals(simpleName)) {
				return Enum.valueOf((Class) inner, value);
			}
		}
		throw new AssertionError("no enum " + simpleName);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Streamer test failed: " + message);
		}
	}
}
