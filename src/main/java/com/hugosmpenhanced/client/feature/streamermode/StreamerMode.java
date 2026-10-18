package com.hugosmpenhanced.client.feature.streamermode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Streamer mode: hides your own name and the names of other players everywhere text is drawn
 * (chat, tab list, nametags, scoreboard, titles, signs, item names, menus), and optionally their skins.
 *
 * <p>Other players get a stand-in like "Spieler 3" that stays the same for the whole game start,
 * so viewers can still follow who says what without learning who it is. Numbers are handed out in
 * random order to players seen for the first time, so they don't reveal the tab list order either.
 *
 * <p>Only the display changes. Commands still go out with the real names, so picking "Spieler 3"
 * from the tab completion sends the real name to the server.
 */
public final class StreamerMode {
	public static final String DEFAULT_OWN_NAME = "Streamer";
	public static final String DEFAULT_OTHERS_NAME = "Spieler";

	public static KeyMapping toggleKey;

	/** Lower-case name to its number for this game start. */
	private static final Map<String, Integer> NUMBERS = new HashMap<>();
	private static int nextNumber = 1;
	private static int signature;
	private static volatile boolean hideSkins;
	private static volatile boolean hideOwnSkin;
	private static volatile UUID ownId;
	private static int hiddenOthers;

	private StreamerMode() {
	}

	public static void register() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hugosmpenhanced.streamer_mode",
			InputConstants.Type.KEYBOARD,
			InputConstants.UNKNOWN.getValue(),
			TpaKeybinds.CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				toggle(client);
			}
			update(client);
		});
		update(Minecraft.getInstance());
	}

	public static void toggle(Minecraft client) {
		ModConfig config = ModConfigManager.CONFIG;
		config.streamerModeEnabled = !config.streamerModeEnabled;
		ModConfigManager.save();
		update(client);
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal(
				config.streamerModeEnabled ? "§aStreamer-Modus an" : "§7Streamer-Modus aus"));
		}
	}

	public static boolean enabled() {
		return ModConfigManager.CONFIG.streamerModeEnabled;
	}

	/** Rebuilds the name table when the config or the players online changed. */
	public static void update(Minecraft client) {
		ModConfig config = ModConfigManager.CONFIG;
		hideSkins = config.streamerModeEnabled && config.streamerModeHideSkins;
		hideOwnSkin = config.streamerModeEnabled && config.streamerModeHideOwnSkin;
		ownId = client.getUser() == null ? null : client.getUser().getProfileId();

		List<String> others = new ArrayList<>();
		ClientPacketListener connection = client.getConnection();
		if (config.streamerModeEnabled && config.streamerModeHideOthers) {
			if (connection != null) {
				for (PlayerInfo info : connection.getOnlinePlayers()) {
					GameProfile profile = info.getProfile();
					if (!profile.id().equals(ownId)) {
						others.add(profile.name());
					}
				}
			}
			others.addAll(config.streamerModeExtraNames);
		}
		String own = client.getUser() == null ? "" : client.getUser().getName();

		int sig = config.streamerModeEnabled ? 1 : 0;
		if (config.streamerModeEnabled) {
			sig = 31 * sig + (config.streamerModeHideOwn ? 1 : 2);
			sig = 31 * sig + own.hashCode();
			sig = 31 * sig + ownName(config).hashCode();
			sig = 31 * sig + othersName(config).hashCode();
			sig = 31 * sig + (config.streamerModeNumbered ? 1 : 2);
			sig = 31 * sig + new HashSet<>(others).hashCode();
		}
		if (sig == signature) {
			return;
		}
		signature = sig;
		NameMask.set(build(config, own, others));
		// Chat lines are wrapped once when they arrive; wrap them again with the new names so
		// their length fits the stand-ins.
		if (client.gui != null) {
			client.gui.hud.getChat().rescaleChat();
		}
	}

	/** How many names of other players are hidden right now. */
	public static int hiddenOthers() {
		return hiddenOthers;
	}

	private static Map<String, String> build(ModConfig config, String own, List<String> others) {
		Map<String, String> names = new HashMap<>();
		if (!config.streamerModeEnabled) {
			return names;
		}
		// Words of the stand-ins themselves are never replaced, or "Spieler 3" would turn into
		// "Spieler 7 3" as soon as somebody called Spieler joins.
		Set<String> reserved = new HashSet<>();
		reserveWords(reserved, ownName(config));
		reserveWords(reserved, othersName(config));

		List<String> fresh = new ArrayList<>();
		for (String name : others) {
			String key = key(name);
			if (key != null && !reserved.contains(key) && !NUMBERS.containsKey(key) && !fresh.contains(key)) {
				fresh.add(key);
			}
		}
		Collections.shuffle(fresh);
		for (String key : fresh) {
			NUMBERS.put(key, nextNumber++);
		}
		for (String name : others) {
			String key = key(name);
			if (key != null && !reserved.contains(key)) {
				names.put(key, config.streamerModeNumbered ? othersName(config) + " " + NUMBERS.get(key) : othersName(config));
			}
		}
		hiddenOthers = names.size();
		String ownKey = key(own);
		if (config.streamerModeHideOwn && ownKey != null && !reserved.contains(ownKey)) {
			names.put(ownKey, ownName(config));
		}
		return names;
	}

	/** The name as the word it appears as in text: Bedrock prefixes like "." dropped, lower case. */
	private static String key(String name) {
		if (name == null) {
			return null;
		}
		StringBuilder key = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (NameMask.isNameChar(c)) {
				key.append(c);
			}
		}
		return key.length() < 3 ? null : key.toString().toLowerCase(Locale.ROOT);
	}

	private static void reserveWords(Set<String> reserved, String text) {
		for (String word : text.split("[^A-Za-z0-9_]+")) {
			if (!word.isEmpty()) {
				reserved.add(word.toLowerCase(Locale.ROOT));
			}
		}
	}

	public static String ownName(ModConfig config) {
		String name = config.streamerModeOwnName == null ? "" : config.streamerModeOwnName.strip();
		return name.isEmpty() ? DEFAULT_OWN_NAME : name;
	}

	public static String othersName(ModConfig config) {
		String name = config.streamerModeOthersName == null ? "" : config.streamerModeOthersName.strip();
		return name.isEmpty() ? DEFAULT_OTHERS_NAME : name;
	}

	/** The local player's name as it is shown right now. */
	public static String shownOwnName(String realName) {
		return realName == null ? null : NameMask.nameFor(realName);
	}

	/** Default skin (picked from the UUID, so it says nothing about the player) instead of the real one. */
	public static PlayerSkin skin(GameProfile profile, PlayerSkin real) {
		if (!hideSkins && !hideOwnSkin) {
			return real;
		}
		boolean own = profile.id().equals(ownId);
		return (own ? hideOwnSkin : hideSkins) ? DefaultPlayerSkin.get(profile) : real;
	}
}
