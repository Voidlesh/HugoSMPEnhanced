package com.hugosmpenhanced.test;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.client.HudCorner;
import com.hugosmpenhanced.client.feature.client.Zoom;
import com.hugosmpenhanced.client.settings.ModSettings;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The "Client" category in a world: key O still opens the menu when another binding shares O
 * and opens its own screen (as Iris does with its shader packs), Minecraft options change from the
 * menu, fullbright, the FPS line, zoom, lower fire and the see-through pumpkin.
 */
@SuppressWarnings("UnstableApiUsage")
public class ClientSettingsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.menuBackgroundBlurriness().set(0));
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 6000");
			BlockPos p = context.computeOnClient(client -> client.player.blockPosition());
			int x = p.getX(), y = p.getY(), z = p.getZ();
			// Something to look at: a small wall with a few different blocks.
			server.runCommand("fill " + (x - 3) + " " + y + " " + (z + 6) + " " + (x + 3) + " " + (y + 3) + " " + (z + 6) + " stone_bricks");
			server.runCommand("fill " + (x - 1) + " " + (y + 1) + " " + (z + 6) + " " + (x + 1) + " " + (y + 2) + " " + (z + 6) + " oak_planks");
			server.runCommand("setblock " + x + " " + (y + 1) + " " + (z + 5) + " oak_sign[rotation=8]{front_text:{messages:['\"HugoSMP\"','\"Enhanced\"','\"\"','\"\"']}}");
			server.runCommand("tp @p " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 10");
			context.waitTicks(20);

			openKeyWithConflict(context);
			minecraftOptions(context);

			// Fullbright at midnight.
			server.runCommand("time set 18000");
			context.waitTicks(10);
			context.takeScreenshot("client-night");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientFullbright = true);
			context.waitTicks(5);
			context.takeScreenshot("client-fullbright");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientFullbright = false);
			server.runCommand("time set 6000");

			// FPS line in each corner.
			for (HudCorner corner : new HudCorner[] {HudCorner.TOP_RIGHT, HudCorner.BOTTOM_LEFT}) {
				context.runOnClient(client -> ModConfigManager.CONFIG.clientFpsHud = corner);
				context.waitTicks(3);
				context.takeScreenshot("client-fps-" + corner.name().toLowerCase());
			}
			context.runOnClient(client -> ModConfigManager.CONFIG.clientFpsHud = HudCorner.OFF);

			// Zoom: hold C, the wheel zooms further, letting go ends it.
			context.takeScreenshot("client-zoom-off");
			context.getInput().holdKey(GLFW.GLFW_KEY_C);
			context.waitTicks(15);
			context.runOnClient(client -> {
				if (!Zoom.zooming()) {
					throw new AssertionError("Holding C did not zoom");
				}
			});
			context.takeScreenshot("client-zoom-4x");
			context.getInput().scroll(1);
			context.getInput().scroll(1);
			context.waitTicks(15);
			int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
			context.takeScreenshot("client-zoom-scrolled");
			context.getInput().releaseKey(GLFW.GLFW_KEY_C);
			context.waitTicks(15);
			context.runOnClient(client -> {
				if (Zoom.zooming()) {
					throw new AssertionError("Zoom did not end when C was let go");
				}
				if (client.player.getInventory().getSelectedSlot() != slot) {
					throw new AssertionError("Scrolling while zoomed changed the hotbar slot");
				}
			});

			// Lower fire and the see-through pumpkin, in survival so the fire shows.
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p fire_resistance 60 0 true");
			// The client only knows "burning" from the server's entity flag.
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setRemainingFireTicks(400));
			context.waitTicks(5);
			context.takeScreenshot("client-fire-normal");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientLowFire = true);
			context.waitTicks(3);
			context.takeScreenshot("client-fire-lower");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientLowFire = false);
			server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().clearFire());
			server.runCommand("item replace entity @p armor.head with carved_pumpkin");
			context.waitTicks(40);
			context.takeScreenshot("client-pumpkin-normal");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientNoPumpkinBlur = true);
			context.waitTicks(3);
			context.takeScreenshot("client-pumpkin-hidden");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientNoPumpkinBlur = false);
			server.runCommand("item replace entity @p armor.head with air");
			server.runCommand("gamemode creative @p");
			context.waitTicks(5);
		}
		context.runOnClient(client -> {
			ModConfig defaults = new ModConfig();
			ModConfigManager.CONFIG.clientFpsHud = defaults.clientFpsHud;
			ModConfigManager.save();
		});
	}

	/**
	 * Another binding on O that opens a screen of its own, like Iris's shader pack key: the
	 * advancements key is moved to O for this. Before the fix the advancements opened instead.
	 */
	private static void openKeyWithConflict(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.keyAdvancements.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_O));
			KeyMapping.resetMapping();
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_O);
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (!(client.screen instanceof SettingsScreen)) {
				throw new AssertionError("Key O with a second binding on O opened " + client.screen + " instead of the settings");
			}
		});
		context.takeScreenshot("client-key-o-conflict");
		context.getInput().pressKey(GLFW.GLFW_KEY_O);
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (client.screen != null) {
				throw new AssertionError("Key O did not close the settings, screen is " + client.screen);
			}
			client.options.keyAdvancements.setKey(client.options.keyAdvancements.getDefaultKey());
			KeyMapping.resetMapping();
		});
		// Without a conflict it still opens.
		context.getInput().pressKey(GLFW.GLFW_KEY_O);
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (!(client.screen instanceof SettingsScreen)) {
				throw new AssertionError("Key O did not open the settings, screen is " + client.screen);
			}
		});
		context.setScreen(() -> null);
		context.waitTicks(40);
	}

	/** Rows bound to Minecraft's options change those, and "reset everything" leaves them alone. */
	private static void minecraftOptions(ClientGameTestContext context) {
		context.setScreen(() -> new SettingsScreen(null).select("Client", "Minecraft"));
		context.waitTicks(3);
		context.takeScreenshot("client-tab-minecraft");
		boolean sprintBefore = context.computeOnClient(client -> client.options.toggleSprint().get());
		int distanceBefore = context.computeOnClient(client -> client.options.renderDistance().get());
		// The row sits below the fold; the search brings it up.
		context.runOnClient(client -> {
			for (char c : "Sprinten".toCharArray()) {
				client.screen.charTyped(new CharacterEvent(c, 0));
			}
		});
		context.waitTicks(2);
		context.takeScreenshot("client-search-sprint");
		int[] sprint = option("Sprinten").bounds();
		click(context, sprint[0] + sprint[2] - 20, sprint[1] + sprint[3] / 2.0);
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (client.options.toggleSprint().get() == sprintBefore) {
				throw new AssertionError("The sprint row did not change Minecraft's toggle sprint");
			}
			int changedDistance = distanceBefore == 6 ? 7 : 6;
			client.options.renderDistance().set(changedDistance);
			ModSettings.resetAll();
			if (client.options.renderDistance().get() != changedDistance || client.options.toggleSprint().get() == sprintBefore) {
				throw new AssertionError("Resetting the mod's settings changed Minecraft's own options");
			}
			client.options.toggleSprint().set(sprintBefore);
			client.options.renderDistance().set(distanceBefore);
		});
		context.setScreen(() -> new SettingsScreen(null).select("Client", "Minecraft"));
		context.waitTicks(3);
		// The FOV slider: dragging to the right end sets 110.
		int[] fov = option("Sichtfeld").bounds();
		int trackRight = fov[0] + fov[2] - 8 - 46;
		context.runOnClient(client -> {
			client.screen.mouseClicked(mouse(trackRight + 2, fov[1] + fov[3] / 2.0), false);
			client.screen.mouseReleased(mouse(trackRight + 2, fov[1] + fov[3] / 2.0));
			if (client.options.fov().get() != 110) {
				throw new AssertionError("FOV slider did not set 110 but " + client.options.fov().get());
			}
			client.options.fov().set(70);
		});
		context.setScreen(() -> new SettingsScreen(null).select("Client", "Anzeige"));
		context.waitTicks(3);
		context.takeScreenshot("client-tab-anzeige");
		context.setScreen(() -> new SettingsScreen(null).select("Client", "Zoom"));
		context.waitTicks(3);
		context.takeScreenshot("client-tab-zoom");
		context.setScreen(() -> null);
	}

	private static void click(ClientGameTestContext context, double x, double y) {
		context.runOnClient(client -> {
			client.screen.mouseClicked(mouse(x, y), false);
			client.screen.mouseReleased(mouse(x, y));
		});
	}

	private static MouseButtonEvent mouse(double x, double y) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
	}

	private static SettingsOption option(String label) {
		for (SettingsOption option : ModSettings.allOptions()) {
			if (option.label().equals(label)) {
				return option;
			}
		}
		throw new AssertionError("No option " + label);
	}
}
