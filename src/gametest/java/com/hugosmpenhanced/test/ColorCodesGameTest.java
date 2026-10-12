package com.hugosmpenhanced.test;

import org.lwjgl.glfw.GLFW;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodeScreen;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodes;
import com.hugosmpenhanced.client.feature.colorcodes.ColorTemplate;
import com.hugosmpenhanced.client.feature.colorcodes.ColorText;
import com.hugosmpenhanced.client.feature.colorcodes.HexStyle;
import com.hugosmpenhanced.client.settings.SettingsScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The colour code editor end to end, with real mouse and keyboard input: the chat preview while
 * typing /rename, Ctrl+E into the editor, a gradient, colour and bold on a selection, undo, saving a
 * template, putting a template's look on other text, and sending /rename to a test server that
 * names the held item. Also checks the code reading and writing on its own first.
 */
@SuppressWarnings("UnstableApiUsage")
public class ColorCodesGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		checkCodes();
		context.runOnClient(client -> {
			client.options.menuBackgroundBlurriness().set(0);
			ModConfigManager.CONFIG.colorHexStyle = HexStyle.AMPERSAND_HASH;
			ModConfigManager.CONFIG.colorChatPreview = true;
			ModConfigManager.CONFIG.colorTemplates = ColorCodes.defaultTemplates();
			ModConfigManager.CONFIG.colorRecent.clear();
		});

		context.setScreen(() -> new SettingsScreen(null).select("Chat", "Farben"));
		context.waitTicks(2);
		context.takeScreenshot("farbcodes-0-einstellungen");

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			singleplayer.getServer().runCommand("gamerule sendCommandFeedback false");
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
				player.getInventory().setItem(1, new ItemStack(Items.NAME_TAG));
				player.inventoryMenu.broadcastChanges();
			});
			context.waitTicks(5);

			// Typing /rename in chat shows the colours right away.
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(2);
			context.getInput().typeChars("/rename &6&lHugo&eSMP &#7CFC7CEnhanced");
			context.waitTicks(3);
			context.takeScreenshot("farbcodes-1-chat-vorschau");

			// Ctrl+E opens the editor with that text.
			combo(context, GLFW.GLFW_KEY_E, GLFW.GLFW_MOD_CONTROL);
			context.waitTicks(3);
			String opened = context.computeOnClient(client -> client.screen instanceof ColorCodeScreen screen ? screen.text() : null);
			expect("&6&lHugo&eSMP &#7CFC7CEnhanced".equals(opened), "Ctrl+E did not open the editor with the chat text: " + opened);
			context.takeScreenshot("farbcodes-2-editor-aus-chat");

			// New text, then a gradient over all of it.
			selectAll(context);
			context.getInput().typeChars("HugoSMP Enhanced");
			click(context, "gradient");
			String gradient = text(context);
			expect(ColorText.parse(gradient).plain().equals("HugoSMP Enhanced"), "Gradient changed the letters: " + gradient);
			expect(gradient.chars().filter(c -> c == '#').count() == 15, "Gradient should colour 15 letters, skipping the space: " + gradient);
			context.takeScreenshot("farbcodes-3-verlauf");

			// Select "Enhanced" with the keyboard, make it gold and bold.
			context.getInput().pressKey(GLFW.GLFW_KEY_END);
			combo(context, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
			click(context, "swatch:6");
			click(context, "format:0");
			String styled = text(context);
			ColorText parsed = ColorText.parse(styled);
			ColorText.Glyph e = parsed.glyphs.get(8);
			ColorText.Glyph h = parsed.glyphs.get(0);
			expect(e.color != null && e.color.code() == '6' && (e.formats & ColorText.BOLD) != 0, "Enhanced is not gold and bold: " + styled);
			expect(h.color != null && !h.color.isLegacy() && (h.formats & ColorText.BOLD) == 0, "Hugo lost its gradient: " + styled);
			context.takeScreenshot("farbcodes-4-markierung-gold-fett");

			// Undo takes the bold back.
			combo(context, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
			expect((ColorText.parse(text(context)).glyphs.get(8).formats & ColorText.BOLD) == 0, "Ctrl+Z did not undo bold: " + text(context));
			combo(context, GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL);
			expect(text(context).equals(styled), "Ctrl+Y did not redo: " + text(context));

			// Save it as a template.
			int before = ModConfigManager.CONFIG.colorTemplates.size();
			click(context, "save");
			selectAll(context);
			context.getInput().typeChars("Mein Schwert");
			context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
			context.waitTicks(2);
			ColorTemplate saved = ModConfigManager.CONFIG.colorTemplates.get(0);
			expect(ModConfigManager.CONFIG.colorTemplates.size() == before + 1 && saved.name.equals("Mein Schwert")
				&& saved.text.equals(styled), "Template not saved: " + saved.name + " / " + saved.text);
			context.takeScreenshot("farbcodes-5-vorlage-gespeichert");

			// Other text with the look of "Feuer" (brush), then hover a template for its tooltip.
			selectAll(context);
			context.getInput().typeChars("Flammenschwert");
			int fire = indexOf("Feuer");
			click(context, "brush:" + fire);
			String fiery = text(context);
			ColorText fieryText = ColorText.parse(fiery);
			expect(fieryText.plain().equals("Flammenschwert") && fieryText.glyphs.get(0).color != null
				&& fieryText.glyphs.get(0).color.rgb() != fieryText.glyphs.get(13).color.rgb(), "Feuer style not applied: " + fiery);
			hover(context, "template:" + indexOf("Ozean"));
			context.takeScreenshot("farbcodes-6-stil-feuer");

			// Load the saved template and send /rename; the test server names the sword.
			click(context, "template:0");
			expect(text(context).equals(styled), "Loading the template did not restore its text: " + text(context));
			click(context, "send");
			context.waitTicks(5);
			String renamed = singleplayer.getServer().computeOnServer(server -> {
				ItemStack held = server.getPlayerList().getPlayers().get(0).getMainHandItem();
				Component name = held.get(DataComponents.CUSTOM_NAME);
				return name == null ? null : name.getString();
			});
			expect("HugoSMP Enhanced".equals(renamed), "Server did not rename the sword: " + renamed + " (sent " + TestRenameServer.lastRename + ")");
			expect(context.computeOnClient(client -> client.screen == null), "Editor did not close after sending");
			// Switch slots so the hotbar shows the new name.
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
			context.waitTicks(2);
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(4);
			context.takeScreenshot("farbcodes-7-umbenannt");

			// Opening by key reads the held item's name back as codes.
			String fromItem = context.computeOnClient(client -> ColorCodes.heldItemName());
			expect(ColorText.parse(fromItem).plain().equals("HugoSMP Enhanced") && fromItem.contains("&6&l"),
				"Held item name not read back: " + fromItem);

			// Larger GUI area: GUI scale 1.
			context.runOnClient(client -> {
				client.options.guiScale().set(1);
				client.resizeDisplay();
			});
			context.setScreen(() -> ColorCodeScreen.forHeldItem(null));
			context.waitTicks(3);
			context.takeScreenshot("farbcodes-8-editor-gui1");
			context.runOnClient(client -> {
				client.options.guiScale().set(2);
				client.resizeDisplay();
			});

			// A long gradient shares colours between letters so it still fits into one command.
			context.setScreen(() -> new ColorCodeScreen(null, "rename", "", null));
			context.waitTicks(2);
			context.getInput().typeChars("Das ist ein sehr langer Name fuer einen Verlauf");
			click(context, "gradient");
			String longGradient = text(context);
			expect(("/rename " + longGradient).length() <= ColorCodes.MAX_COMMAND_LENGTH && longGradient.chars().filter(c -> c == '#').count() > 10,
				"Long gradient does not fit or lost its colours: " + longGradient.length());
			context.takeScreenshot("farbcodes-9-langer-verlauf");

			// Too long for one command: send is refused, the counter turns red.
			for (int i = 0; i < 5; i++) {
				click(context, "format:" + i);
			}
			context.getInput().pressKey(GLFW.GLFW_KEY_END);
			context.getInput().typeChars(" &#123456a&#654321b&#abcdefc&#fedcbad&#111111e&#222222f&#333333g&#444444h&#555555i&#666666j");
			context.waitTicks(1);
			click(context, "send");
			expect(context.computeOnClient(client -> client.screen instanceof ColorCodeScreen), "A too long command was sent");
			context.takeScreenshot("farbcodes-10-zu-lang");
			context.setScreen(() -> null);
		}
	}

	/** Reading and writing codes without the game. */
	private static void checkCodes() {
		roundTrip("&6&lHugo&eSMP");
		roundTrip("&#ff8800Orange &rnormal");
		roundTrip("Ohne Farbe");
		ColorText x = ColorText.parse("&x&f&f&0&0&0&0Rot");
		expect(x.glyphs.get(0).color.rgb() == 0xFF0000 && x.plain().equals("Rot"), "&x format not read");
		expect(x.serialize(HexStyle.AMPERSAND_X).equals("&x&f&f&0&0&0&0Rot"), "&x not written: " + x.serialize(HexStyle.AMPERSAND_X));
		expect(x.serialize(HexStyle.LEGACY_ONLY).equals("&cRot"), "nearest colour wrong: " + x.serialize(HexStyle.LEGACY_ONLY));
		ColorText unknown = ColorText.parse("50& mehr &zx");
		expect(unknown.plain().equals("50& mehr &zx"), "Unknown codes must stay text: " + unknown.plain());
		ColorText bold = ColorText.parse("&lA &lB");
		expect(bold.serialize(HexStyle.AMPERSAND_HASH).equals("&lA B"), "Repeated codes not merged: " + bold.serialize(HexStyle.AMPERSAND_HASH));
		ColorText partial = ColorText.parse("&cRot&lFett");
		partial.toggleFormat(0, 3, ColorText.BOLD);
		expect(partial.serialize(HexStyle.AMPERSAND_HASH).equals("&c&lRotFett"), "Bold over both: " + partial.serialize(HexStyle.AMPERSAND_HASH));
		partial.toggleFormat(0, 3, ColorText.BOLD);
		expect(partial.serialize(HexStyle.AMPERSAND_HASH).equals("&cRot&lFett"), "Bold removed: " + partial.serialize(HexStyle.AMPERSAND_HASH));
		expect(ColorCodes.match("/rename &cHi").textStart() == 8, "match /rename");
		expect(ColorCodes.match("/RENAME x") != null && ColorCodes.match("/renamex") == null && ColorCodes.match("rename x") == null, "match edge cases");
	}

	private static void roundTrip(String text) {
		String out = ColorText.parse(text).serialize(HexStyle.AMPERSAND_HASH);
		expect(out.equals(text), "Round trip changed \"" + text + "\" to \"" + out + "\"");
	}

	private static int indexOf(String name) {
		for (int i = 0; i < ModConfigManager.CONFIG.colorTemplates.size(); i++) {
			if (ModConfigManager.CONFIG.colorTemplates.get(i).name.equals(name)) {
				return i;
			}
		}
		throw new AssertionError("No template " + name);
	}

	private static String text(ClientGameTestContext context) {
		return context.computeOnClient(client -> ((ColorCodeScreen) client.screen).text());
	}

	private static void selectAll(ClientGameTestContext context) {
		combo(context, GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
	}

	/** Key with modifiers; the test input always sends modifiers 0, so this goes to the screen directly. */
	private static void combo(ClientGameTestContext context, int key, int modifiers) {
		context.runOnClient(client -> client.screen.keyPressed(new KeyEvent(key, 0, modifiers)));
		context.waitTicks(2);
	}

	private static void hover(ClientGameTestContext context, String control) {
		double[] pos = context.computeOnClient(client -> {
			int[] b = ((ColorCodeScreen) client.screen).bounds(control);
			double scale = client.getWindow().getGuiScale();
			return new double[] {(b[0] + b[2] / 2.0) * scale, (b[1] + b[3] / 2.0) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
	}

	private static void click(ClientGameTestContext context, String control) {
		hover(context, control);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void expect(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
