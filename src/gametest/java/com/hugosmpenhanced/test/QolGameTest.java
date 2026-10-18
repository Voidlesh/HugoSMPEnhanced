package com.hugosmpenhanced.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.hugoguard.HgProfileScreen;
import com.hugosmpenhanced.client.feature.hugoguard.HugoGuard;
import com.hugosmpenhanced.client.feature.notes.Note;
import com.hugosmpenhanced.client.feature.notes.NotesScreen;
import com.mojang.authlib.GameProfile;


import com.hugosmpenhanced.client.settings.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Screenshots for the trusted badge and glow (in the open and behind a wall), the chat calculator,
 * the notepad with a pinned note, the /hg profile page and the config screen.
 */
@SuppressWarnings("UnstableApiUsage")
public class QolGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		HugoGuard.fetchAll();
		HugoGuard.forceActive = true;
		ModConfigManager.CONFIG.hugoGuardEnabled = true;
		String trustedName = firstTrusted();
		HugoGuard.Entry scammer = context.computeOnClient(client -> HugoGuard.scammer("StepOnMyBrick"));
		HugoGuard.Entry trusted = context.computeOnClient(client -> HugoGuard.trusted(trustedName));
		System.out.println("QOL scammer=" + scammer + " trusted=" + trusted);
		if (scammer == null || trusted == null) {
			throw new AssertionError("HugoGuard lists did not load");
		}

		// No menu blur and a flat world, so the world loads in time under software rendering.
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set day");
			int[] base = context.computeOnClient(client -> {
				var self = client.player;
				self.setYRot(0);
				self.setXRot(0);
				return new int[] {self.getBlockX(), self.getBlockY(), self.getBlockZ()};
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				Vec3 eye = client.player.position();
				spawn(client, scammer.uuid(), scammer.username(), eye.add(1.5, 0, 5), 180);
				spawn(client, trusted.uuid(), trusted.username(), eye.add(-1.5, 0, 5), 180);
				spawn(client, UUID.randomUUID(), "Ehrlich", eye.add(-4.5, 0, 6), 180);
			});
			context.waitTicks(20);
			context.takeScreenshot("qol-glow-open");

			// A stone wall in front of the scammer only (+x is to the left when facing +z): its red
			// glow must disappear with it, while the trusted player next to it stays visible.
			singleplayer.getServer().runCommand("fill " + (base[0] + 0) + " " + base[1] + " " + (base[2] + 3)
				+ " " + (base[0] + 4) + " " + (base[1] + 3) + " " + (base[2] + 3) + " minecraft:stone");
			context.waitTicks(20);
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("qol-glow-wall");

			// Chat calculator: the result shows above the input; Tab puts it in.
			context.setScreen(() -> new ChatScreen("verkaufe für 64*3,5", false));
			context.waitTicks(5);
			context.takeScreenshot("qol-calculator");
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(2);
			String after = context.computeOnClient(client -> chatInput(client).getValue());
			System.out.println("QOL calculator after tab: " + after);
			if (!"verkaufe für 224".equals(after)) {
				throw new AssertionError("Tab did not put the result in: " + after);
			}
			context.setScreen(() -> new ChatScreen("2st + 5", false));
			context.waitTicks(3);
			context.takeScreenshot("qol-calculator-stacks");
			context.setScreen(() -> null);

			// Notepad with one pinned note, then the HUD with it.
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.notes.clear();
				Note shopping = new Note();
				shopping.title = "Einkauf";
				shopping.text = "- 3 Stacks Eisen\n- 2 Shulker Sand\n- Elytra reparieren\nBase: 1200 / -340";
				shopping.pinned = true;
				Note prices = new Note();
				prices.title = "Preise";
				prices.text = "Diamant: 150\nNetherite: 4.000";
				ModConfigManager.CONFIG.notes.add(shopping);
				ModConfigManager.CONFIG.notes.add(prices);
			});
			context.setScreen(() -> new NotesScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("qol-notes");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.takeScreenshot("qol-notes-hud");

			// /hg pages for the scammer and the trusted player.
			context.runOnClient(client -> client.player.connection.sendCommand("hg " + scammer.username()));
			context.waitTicks(80);
			context.takeScreenshot("qol-hg-scammer");
			context.setScreen(() -> new HgProfileScreen(null, trusted.username()));
			context.waitTicks(80);
			context.takeScreenshot("qol-hg-trusted");
			context.setScreen(() -> new HgProfileScreen(null, "Notch"));
			context.waitTicks(80);
			context.takeScreenshot("qol-hg-unlisted");

			// The config screen must still open with the new options.
			context.setScreen(() -> new SettingsScreen(null).select("Werkzeuge", "Notizen"));
			context.waitTicks(5);
			context.takeScreenshot("qol-config");
			context.setScreen(() -> null);
			context.waitTicks(2);

			// O opens the settings in game, O closes them again; the pause menu has the logo button.
			context.getInput().pressKey(InputConstants.KEY_O);
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (!(client.gui.screen() instanceof SettingsScreen)) {
					throw new AssertionError("Key O did not open the settings, screen is " + client.gui.screen());
				}
			});
			context.getInput().pressKey(InputConstants.KEY_O);
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (client.gui.screen() != null) {
					throw new AssertionError("Key O did not close the settings, screen is " + client.gui.screen());
				}
			});
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			context.takeScreenshot("qol-pause-buttons");
			context.setScreen(() -> null);
		}
		ModConfigManager.CONFIG.notes.clear();
		HugoGuard.forceActive = false;
	}

	private static String firstTrusted() {
		try {
			HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
					URI.create("https://hugoguard.net/api/public/v1/players?status=TRUSTED&page=1&pageSize=1"))
				.header("User-Agent", "Mozilla/5.0").GET().build(), HttpResponse.BodyHandlers.ofString());
			return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("results")
				.get(0).getAsJsonObject().get("username").getAsString();
		} catch (Exception e) {
			throw new AssertionError("Trusted list could not be loaded", e);
		}
	}

	private static EditBox chatInput(Minecraft client) {
		try {
			Field field = ChatScreen.class.getDeclaredField("input");
			field.setAccessible(true);
			return (EditBox) field.get(client.gui.screen());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void spawn(Minecraft client, UUID uuid, String name, Vec3 at, float yaw) {
		RemotePlayer player = new RemotePlayer(client.level, new GameProfile(uuid, name));
		player.snapTo(at.x, at.y, at.z, yaw, 0);
		player.setYHeadRot(yaw);
		client.level.addEntity(player);
	}
}
