package com.hugosmpenhanced.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.ModSettings;
import com.hugosmpenhanced.client.settings.SettingsCategory;
import com.hugosmpenhanced.client.settings.SettingsFeature;
import com.hugosmpenhanced.client.settings.SettingsOption;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.client.settings.SettingsTab;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;


/**
 * The settings menu: every category and tab at GUI scale 2 and 1, the search, clicking a toggle
 * row, adding a word to a list and setting a key binding (key and side mouse button). Opening by key is in QolGameTest.
 */
@SuppressWarnings("UnstableApiUsage")
public class SettingsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.menuBackgroundBlurriness().set(0);
			ModConfigManager.CONFIG.mentionTriggers.clear();
		});

		for (int scale : new int[] {2, 1}) {
			context.runOnClient(client -> {
				client.options.guiScale().set(scale);
				client.resizeGui();
			});
			List<SettingsCategory> categories = ModSettings.categories();
			for (SettingsCategory category : categories) {
				for (SettingsTab tab : category.tabs()) {
					context.setScreen(() -> new SettingsScreen(null).select(category.name(), tab.name()));
					context.waitTicks(2);
					context.takeScreenshot("settings-gui" + scale + "-" + slug(category.name()) + "-" + slug(tab.name()));
				}
			}
		}

		context.runOnClient(client -> {
			client.options.guiScale().set(2);
			client.resizeGui();
		});

		// Search: letters typed without the field focused do nothing; Ctrl+F (or a click) starts it.
		context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Menü"));
		context.waitTicks(2);
		context.runOnClient(client -> {
			client.gui.screen().charTyped(new CharacterEvent('x'));
			check(((SettingsScreen) client.gui.screen()).searchText().isEmpty(), "a letter typed without focus went into the search");
			client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F, 0, InputConstants.MOD_CONTROL));
			for (char c : "name".toCharArray()) {
				client.gui.screen().charTyped(new CharacterEvent(c));
			}
			check("name".equals(((SettingsScreen) client.gui.screen()).searchText()), "Ctrl+F then typing did not search");
		});
		context.waitTicks(2);
		context.takeScreenshot("settings-search-name");

		// Clicking a toggle row flips it; the plus of a list opens an input, Enter adds the word.
		context.setScreen(() -> new SettingsScreen(null).select("Chat", "Erwähnungen"));
		context.waitTicks(2);
		boolean before = ModConfigManager.CONFIG.mentionsEnabled;
		int[] toggle = option("Erwähnungen hervorheben").bounds();
		clickGui(context, toggle[0] + toggle[2] - 15, toggle[1] + toggle[3] / 2.0);
		context.waitTicks(1);
		if (ModConfigManager.CONFIG.mentionsEnabled == before) {
			throw new AssertionError("Clicking the mention toggle did not change it");
		}
		clickGui(context, toggle[0] + toggle[2] - 15, toggle[1] + toggle[3] / 2.0);

		int[] list = option("Zusätzliche Wörter").bounds();
		clickGui(context, list[0] + 14, list[1] + list[3] - 12);
		context.runOnClient(client -> {
			for (char c : "Hugo".toCharArray()) {
				client.gui.screen().charTyped(new CharacterEvent(c));
			}
		});
		context.waitTicks(2);
		context.takeScreenshot("settings-list-typing");
		context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
		context.waitTicks(2);
		if (!ModConfigManager.CONFIG.mentionTriggers.contains("Hugo")) {
			throw new AssertionError("Typing into the list did not add the word: " + ModConfigManager.CONFIG.mentionTriggers);
		}
		context.takeScreenshot("settings-list-added");
		context.runOnClient(client -> ModConfigManager.CONFIG.mentionTriggers.clear());

		// Key binding row: click, then press a key.
		context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Menü"));
		context.waitTicks(2);
		int[] key = option("Menü öffnen").bounds();
		clickGui(context, key[0] + key[2] - 14, key[1] + key[3] / 2.0);
		context.waitTicks(1);
		context.takeScreenshot("settings-key-listening");
		context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)));
		// A key pressed for a binding sets it; its letter (GLFW sends one right after) stays out of the search.
		clickGui(context, key[0] + key[2] - 14, key[1] + key[3] / 2.0);
		context.runOnClient(client -> {
			client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_K, 0, 0));
			client.gui.screen().charTyped(new CharacterEvent('k'));
			check(SettingsFeature.openKey.getTranslatedKeyMessage().getString().equalsIgnoreCase("K"),
				"key K was not bound but " + SettingsFeature.openKey.getTranslatedKeyMessage().getString());
			check(((SettingsScreen) client.gui.screen()).searchText().isEmpty(), "the bound key's letter went into the search");
		});
		context.waitTicks(2);
		context.takeScreenshot("settings-key-set");
		// A side mouse button can be bound too.
		clickGui(context, key[0] + key[2] - 14, key[1] + key[3] / 2.0);
		context.runOnClient(client -> {
			double scale = ((SettingsScreen) client.gui.screen()).layoutScale();
			MouseButtonEvent side = new MouseButtonEvent((key[0] + 10) * scale, (key[1] + 5) * scale, new MouseButtonInfo(3, 0));
			client.gui.screen().mouseClicked(side, false);
			client.gui.screen().mouseReleased(side);
			check(SettingsFeature.openKey.saveString().startsWith("key.mouse."), "mouse button 4 was not bound: " + SettingsFeature.openKey.saveString());
			SettingsFeature.openKey.setKey(SettingsFeature.openKey.getDefaultKey());
			KeyMapping.resetMapping();
		});
		context.setScreen(() -> null);
	}

	/** Clicks where the settings menu drew something, given in its layout units (see SettingsScreen). */
	private static void clickGui(ClientGameTestContext context, double x, double y) {
		context.runOnClient(client -> {
			double scale = ((SettingsScreen) client.gui.screen()).layoutScale();
			client.gui.screen().mouseClicked(click(x * scale, y * scale), false);
			client.gui.screen().mouseReleased(click(x * scale, y * scale));
		});
	}

	private static MouseButtonEvent click(double x, double y) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
	}

	private static String slug(String name) {
		return name.toLowerCase().replace("ä", "ae").replace("ü", "ue").replace("ö", "oe").replace("&", "und").replaceAll("[^a-z0-9]+", "-");
	}

	private static SettingsOption option(String label) {
		for (SettingsOption option : ModSettings.allOptions()) {
			if (option.label().equals(label)) {
				return option;
			}
		}
		throw new AssertionError("No option " + label);
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}
}
