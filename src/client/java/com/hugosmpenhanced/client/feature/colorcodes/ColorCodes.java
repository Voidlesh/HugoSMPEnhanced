package com.hugosmpenhanced.client.feature.colorcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The colour code editor for /rename and /sign: a key binding (not bound by default), the chat
 * preview's link into the editor (see {@code ColorChatPreviewMixin}) and the shared helpers.
 */
public final class ColorCodes {
	public static final int MAX_COMMAND_LENGTH = 256;
	public static final int MAX_TEMPLATES = 40;
	public static final int MAX_RECENT = 10;

	public static KeyMapping openKey;

	/** A chat input that runs one of the colour commands: the command and where its text starts. */
	public record Match(String command, int textStart) {
	}

	private ColorCodes() {
	}

	public static void register() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hugosmpenhanced.open_color_editor",
			InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), TpaKeybinds.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(ColorCodeScreen.forHeldItem(null));
				}
			}
		});
	}

	/** Commands the editor writes for, without slash; falls back to rename and sign. */
	public static List<String> commands() {
		List<String> commands = new ArrayList<>();
		for (String command : ModConfigManager.CONFIG.colorCommands) {
			String clean = command.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
			if (!clean.isEmpty() && !clean.contains(" ") && !commands.contains(clean)) {
				commands.add(clean);
			}
		}
		if (commands.isEmpty()) {
			commands.add("rename");
			commands.add("sign");
		}
		return commands;
	}

	/** The colour command a chat input starts with, or null. */
	public static Match match(String input) {
		if (!input.startsWith("/")) {
			return null;
		}
		String lower = input.toLowerCase(Locale.ROOT);
		for (String command : commands()) {
			if (lower.startsWith("/" + command + " ")) {
				return new Match(command, command.length() + 2);
			}
		}
		return null;
	}

	public static HexStyle hexStyle() {
		HexStyle style = ModConfigManager.CONFIG.colorHexStyle;
		return style == null ? HexStyle.AMPERSAND_HASH : style;
	}

	/** The held item's own name as codes, for editing an item that was renamed before. */
	public static String heldItemName() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return "";
		}
		ItemStack stack = client.player.getMainHandItem();
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : ColorText.fromComponent(name, hexStyle());
	}

	public static void addRecent(int rgb) {
		List<String> recent = ModConfigManager.CONFIG.colorRecent;
		String hex = String.format("#%06X", rgb & 0xFFFFFF);
		recent.remove(hex);
		recent.add(0, hex);
		while (recent.size() > MAX_RECENT) {
			recent.remove(recent.size() - 1);
		}
	}

	public static List<Integer> recent() {
		List<Integer> colors = new ArrayList<>();
		for (String hex : ModConfigManager.CONFIG.colorRecent) {
			Integer rgb = parseHex(hex);
			if (rgb != null) {
				colors.add(rgb);
			}
		}
		return colors;
	}

	/** "#FFAA00", "FFAA00" or "&#FFAA00" as RGB, or null. */
	public static Integer parseHex(String text) {
		String clean = text.strip().replace("&", "").replace("#", "");
		if (clean.length() == 3) {
			clean = "" + clean.charAt(0) + clean.charAt(0) + clean.charAt(1) + clean.charAt(1) + clean.charAt(2) + clean.charAt(2);
		}
		if (clean.length() != 6) {
			return null;
		}
		try {
			return Integer.parseInt(clean, 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Adds the examples that are missing (by name) back to the end of the list. */
	public static void restoreDefaults() {
		List<ColorTemplate> templates = ModConfigManager.CONFIG.colorTemplates;
		for (ColorTemplate example : defaultTemplates()) {
			if (templates.stream().noneMatch(t -> t.name.equalsIgnoreCase(example.name)) && templates.size() < MAX_TEMPLATES) {
				templates.add(example);
			}
		}
		ModConfigManager.save();
	}

	/** The examples a new config starts with; each is its own name in its look. */
	public static List<ColorTemplate> defaultTemplates() {
		List<ColorTemplate> templates = new ArrayList<>();
		templates.add(gradient("Feuer", 0, 0xFF3D00, 0xFFC400));
		templates.add(gradient("Ozean", 0, 0x00D2FF, 0x3A47D5));
		templates.add(gradient("Gold", ColorText.BOLD, 0xFFE259, 0xFFA751));
		templates.add(gradient("Abendrot", 0, 0xFF5F6D, 0xFFC371));
		templates.add(gradient("Lila Traum", 0, 0xC471F5, 0xFA71CD));
		templates.add(gradient("Giftgrün", 0, 0xA8FF78, 0x2AA876));
		templates.add(gradient("Regenbogen", 0, 0xFF4B4B, 0xFFB23F, 0xFFF35C, 0x5CFF7A, 0x4FC3FF, 0xB46CFF));
		templates.add(new ColorTemplate("Klassisch Gold", "&6&lKlassisch &e&lGold"));
		return templates;
	}

	private static ColorTemplate gradient(String name, int formats, int... stops) {
		ColorText text = ColorText.parse(name);
		text.gradient(0, text.glyphs.size(), stops);
		for (ColorText.Glyph glyph : text.glyphs) {
			glyph.formats = formats;
		}
		return new ColorTemplate(name, text.serialize(HexStyle.AMPERSAND_HASH));
	}
}
