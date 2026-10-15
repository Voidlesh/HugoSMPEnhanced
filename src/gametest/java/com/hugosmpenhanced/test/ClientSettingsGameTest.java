package com.hugosmpenhanced.test;

import java.util.List;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.client.FpsHud;
import com.hugosmpenhanced.client.hud.HudManager;
import com.hugosmpenhanced.client.hud.HudPos;
import com.hugosmpenhanced.client.settings.ModSettings;
import com.hugosmpenhanced.client.settings.SettingsCategory;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.client.settings.SettingsTab;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The "Client" category in a world: key O still opens the menu when another binding shares O
 * and opens its own screen (as Iris does with its shader packs) without its letter landing in the
 * search, only the mod's own options in the category, fullbright, the FPS line, lower fire and the see-through pumpkin.
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
			clientTabs(context);

			// Fullbright at midnight.
			server.runCommand("time set 18000");
			context.waitTicks(10);
			context.takeScreenshot("client-night");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientFullbright = true);
			context.waitTicks(5);
			context.takeScreenshot("client-fullbright");
			context.runOnClient(client -> ModConfigManager.CONFIG.clientFullbright = false);
			server.runCommand("time set 6000");

			// FPS line at its default spot and moved to the bottom left in the HUD editor.
			context.runOnClient(client -> ModConfigManager.CONFIG.clientFpsHudOn = true);
			context.waitTicks(3);
			context.takeScreenshot("client-fps-top_right");
			context.runOnClient(client -> HudManager.setPos(FpsHud.INSTANCE, new HudPos(0, 1)));
			context.waitTicks(3);
			context.takeScreenshot("client-fps-bottom_left");
			context.runOnClient(client -> {
				ModConfigManager.CONFIG.clientFpsHudOn = false;
				HudManager.reset(FpsHud.INSTANCE);
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
			ModConfigManager.CONFIG.clientFpsHudOn = defaults.clientFpsHudOn;
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
		// A real keyboard also sends the letter, which reaches the menu that just opened.
		context.getInput().typeChars("o");
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (!(client.screen instanceof SettingsScreen settings)) {
				throw new AssertionError("Key O with a second binding on O opened " + client.screen + " instead of the settings");
			}
			if (!settings.searchText().isEmpty()) {
				throw new AssertionError("The O that opened the menu went into the search: " + settings.searchText());
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

	/** The category only holds the mod's own options; Minecraft's are in its own menu. */
	private static void clientTabs(ClientGameTestContext context) {
		for (SettingsCategory category : ModSettings.categories()) {
			if (category.name().equals("Client")) {
				List<String> tabs = category.tabs().stream().map(SettingsTab::name).toList();
				if (!tabs.equals(List.of("Anzeige"))) {
					throw new AssertionError("Client tabs are " + tabs);
				}
			}
		}
		for (SettingsOption option : ModSettings.allOptions()) {
			if (option.minecraftOption()) {
				throw new AssertionError("Still a copy of a Minecraft option: " + option.label());
			}
		}
		context.setScreen(() -> new SettingsScreen(null).select("Client", "Anzeige"));
		context.waitTicks(3);
		context.takeScreenshot("client-tab-anzeige");
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
