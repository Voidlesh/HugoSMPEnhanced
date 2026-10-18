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
		migrate();
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
		return style == null ? HexStyle.LEGACY_ONLY : style;
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

	/** The examples a new config starts with; each is its own name in its look, only chat colours. */
	public static List<ColorTemplate> defaultTemplates() {
		List<ColorTemplate> templates = new ArrayList<>();
		templates.add(new ColorTemplate("Feuer", "&4Fe&cu&6e&er"));
		templates.add(new ColorTemplate("Ozean", "&9Oz&3e&ban"));
		templates.add(new ColorTemplate("Gold", "&6&lGo&e&lld"));
		templates.add(new ColorTemplate("Abendrot", "&5Ab&den&cdr&6ot"));
		templates.add(new ColorTemplate("Lila Traum", "&5Lila &dTraum"));
		templates.add(new ColorTemplate("Giftgrün", "&2Gift&agrün"));
		templates.add(new ColorTemplate("Regenbogen", "&cRe&6ge&en&ab&bo&9g&de&5n"));
		templates.add(new ColorTemplate("Schatten", "&8Sch&7att&fen"));
		templates.add(new ColorTemplate("Klassisch Gold", "&6&lKlassisch &e&lGold"));
		return templates;
	}

	/**
	 * Version 2: HugoSMP only reads the 16 chat colours and allows 35 characters, so older configs
	 * switch to that and the hex example templates are replaced by chat colour ones.
	 */
	private static void migrate() {
		var config = ModConfigManager.CONFIG;
		if (config.colorConfigVersion >= 2) {
			return;
		}
		config.colorHexStyle = HexStyle.LEGACY_ONLY;
		config.colorMaxLength = 35;
		for (ColorTemplate example : defaultTemplates()) {
			for (ColorTemplate template : config.colorTemplates) {
				if (template.name.equals(example.name) && template.text.contains("&#")) {
					template.text = example.text;
				}
			}
		}
		config.colorTemplates.removeIf(t -> t.name.equals("Sonnenuntergang") && t.text.contains("&#"));
		config.colorConfigVersion = 2;
		ModConfigManager.save();
	}

	/** Whether only the 16 chat colours are written (no hex). */
	public static boolean legacy() {
		return hexStyle() == HexStyle.LEGACY_ONLY;
	}

	/** How long the text after the command may be, codes included. */
	public static int textLimit(String command) {
		int commandRoom = MAX_COMMAND_LENGTH - command.length() - 2;
		int limit = ModConfigManager.CONFIG.colorMaxLength;
		return limit <= 0 ? commandRoom : Math.min(limit, commandRoom);
	}
}
