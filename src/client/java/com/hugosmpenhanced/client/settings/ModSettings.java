package com.hugosmpenhanced.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.InventoryButtonsScreen;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.config.NametagStyleScreen;
import com.hugosmpenhanced.client.config.SecondNametagScreen;
import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.hugosmpenhanced.client.feature.antileak.CoordinateMask;
import com.hugosmpenhanced.client.feature.auction.AuctionSearchLanguage;
import com.hugosmpenhanced.client.feature.auction.CustomItems;
import com.hugosmpenhanced.client.feature.auction.MarkedItems;
import com.hugosmpenhanced.client.feature.client.ClientTweaks;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodeScreen;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodes;
import com.hugosmpenhanced.client.feature.colorcodes.HexStyle;
import com.hugosmpenhanced.client.feature.debug.DebugDump;
import com.hugosmpenhanced.client.feature.farm.FarmTracker;
import com.hugosmpenhanced.client.feature.money.MoneyScreen;
import com.hugosmpenhanced.client.feature.money.MoneyTracker;
import com.hugosmpenhanced.client.feature.pay.PayLogScreen;
import com.hugosmpenhanced.client.feature.spawnerstorage.SpawnerStorageScreen;
import com.hugosmpenhanced.client.feature.discord.DiscordOutside;
import com.hugosmpenhanced.client.feature.discord.DiscordPresence;
import com.hugosmpenhanced.client.feature.discord.DiscordPreviewOption;
import com.hugosmpenhanced.client.feature.discord.DiscordStatusOption;
import com.hugosmpenhanced.client.feature.discord.DiscordTimer;
import com.hugosmpenhanced.client.feature.discord.StatusReader;
import com.hugosmpenhanced.client.feature.entityheatmap.EntityHeatmap;
import com.hugosmpenhanced.client.feature.mentions.MentionStyle;
import com.hugosmpenhanced.client.feature.streamer.ChatPatterns;
import com.hugosmpenhanced.client.feature.streamer.DonationAuction;
import com.hugosmpenhanced.client.feature.streamer.Giveaway;
import com.hugosmpenhanced.client.feature.streamer.Money;
import com.hugosmpenhanced.client.feature.streamer.StreamerFeature;
import com.hugosmpenhanced.client.feature.streamer.StreamerScreen;
import com.hugosmpenhanced.client.feature.streamer.TwitchChat;
import com.hugosmpenhanced.client.feature.streamermode.StreamerMode;
import com.hugosmpenhanced.client.feature.gallery.GalleryFeature;
import com.hugosmpenhanced.client.feature.gallery.GalleryScreen;
import com.hugosmpenhanced.client.feature.inventorylayout.InventoryLayout;
import com.hugosmpenhanced.client.feature.keyboard.KeyboardScreen;
import com.hugosmpenhanced.client.feature.mainmenu.MainMenuLayout;
import com.hugosmpenhanced.client.feature.mainmenu.SplashAnimation;
import com.hugosmpenhanced.client.feature.mainmenu.SplashColor;
import com.hugosmpenhanced.client.feature.notes.NotesFeature;
import com.hugosmpenhanced.client.feature.notes.NotesScreen;
import com.hugosmpenhanced.client.feature.sell.SellAutostart;
import com.hugosmpenhanced.client.feature.sell.SellFeature;
import com.hugosmpenhanced.client.feature.sell.SellRulesOption;
import com.hugosmpenhanced.client.feature.sell.SellSpeed;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPrices;
import com.hugosmpenhanced.client.feature.spawnerprices.SpawnerPricesScreen;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.feature.update.ModUpdates;
import com.hugosmpenhanced.client.feature.update.UpdateInstaller;
import com.hugosmpenhanced.client.feature.update.UpdateStatusOption;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.hud.HudEditorScreen;
import com.hugosmpenhanced.client.hud.HudKeys;
import com.hugosmpenhanced.client.settings.option.ButtonOption;
import com.hugosmpenhanced.client.settings.option.CycleOption;
import com.hugosmpenhanced.client.settings.option.InfoOption;
import com.hugosmpenhanced.client.settings.option.KeyBindOption;
import com.hugosmpenhanced.client.settings.option.PillsOption;
import com.hugosmpenhanced.client.settings.option.SliderOption;
import com.hugosmpenhanced.client.settings.option.StringListOption;
import com.hugosmpenhanced.client.settings.option.TextOption;
import com.hugosmpenhanced.client.settings.option.ToggleOption;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Everything the settings menu shows, as data: category, tab, section, row. A new option is one
 * line here; the screen and the search pick it up on their own.
 */
public final class ModSettings {
	private static List<SettingsCategory> categories;

	private ModSettings() {
	}

	public static List<SettingsCategory> categories() {
		if (categories == null) {
			categories = List.of(general(), client(), chat(), players(), economy(), inventory(), tools());
		}
		return categories;
	}

	public static List<SettingsOption> allOptions() {
		List<SettingsOption> all = new ArrayList<>();
		for (SettingsCategory category : categories()) {
			for (SettingsTab tab : category.tabs()) {
				for (SettingsSection section : tab.sections()) {
					all.addAll(section.options());
				}
			}
		}
		return all;
	}

	/** Puts every option back to its default; notes, buttons, layouts, AH history and Minecraft's own options stay. */
	public static void resetAll() {
		for (SettingsOption option : allOptions()) {
			if (option.hasDefault() && !option.isDefault() && !option.minecraftOption()) {
				option.reset();
			}
		}
		ModConfigManager.save();
	}

	// ------------------------------------------------------------------ categories

	private static SettingsCategory general() {
		return new SettingsCategory("Allgemein", "general", "Menü, Discord, Anti-Leak und Streamer-Modus",
			new SettingsTab("Menü",
				new SettingsSection("Menü",
					new KeyBindOption("Menü öffnen", "Taste für dieses Menü, überall im Spiel", () -> SettingsFeature.openKey),
					toggle("Knopf im Pausemenü", "Logo-Knopf neben Kamera und Notizblock",
						c -> c.pauseMenuSettingsButton, (c, v) -> c.pauseMenuSettingsButton = v)),
				updatesSection(),
				new SettingsSection("Daten",
					ButtonOption.action("Konfig-Ordner", "config/hugosmpenhanced.json", "Öffnen",
						() -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir())),
					ButtonOption.danger("Alles zurücksetzen",
						"Setzt alle Einstellungen und Tasten zurück. Notizen, Buttons, Layouts und Suchverlauf bleiben.",
						"Zurücksetzen", ModSettings::resetAll)),
				new SettingsSection("HugoSMP-Texte",
					new InfoOption("Stand", "Wie die Mod Zahlungen, Menüs und Tooltips von HugoSMP erkennt. Wird stündlich von GitHub geladen, "
						+ "so lassen sich Erkennungen ohne neue Version reparieren.", HugoFormats::describe).keywords("formate erkennung online repo"),
					ButtonOption.action("Jetzt neu laden", null, "Laden", HugoFormats::refresh).keywords("formate"),
					ButtonOption.action("Datei ansehen", "Auf GitHub", "Öffnen", () -> Util.getPlatform().openUri(HugoFormats.EDIT_URL))),
				new SettingsSection("Fehler melden",
					ButtonOption.action("Debug-Datei", "Scoreboard, letzte Chatzeilen und letztes Menü als Text, in der Zwischenablage. "
						+ "In einem Menü: Strg+Shift+C. Mehr mit /hse debug chat, item, menu, scoreboard.", "Erstellen",
						DebugDump::all).keywords("debug bug fehler melden format"),
					ButtonOption.action("Debug-Ordner", "config/hugosmpenhanced/debug", "Öffnen",
						() -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced")))),
				new SettingsSection("Über",
					new InfoOption("HugoSMP Enhanced", null,
						() -> "Version " + version(HugoSmpEnhancedClient.MOD_ID) + "  -  Minecraft " + version("minecraft")))),
			new SettingsTab("Hauptmenü",
				new SettingsSection("Titelbildschirm",
					toggle("Eigenes Hauptmenü", "Ersetzt das Minecraft-Hauptmenü durch das HugoSMP-Menü",
						c -> c.mainMenuCustom, (c, v) -> c.mainMenuCustom = v),
					new CycleOption<>("Layout", "Lässt sich auch im Hauptmenü oben rechts umschalten", MainMenuLayout.class,
						c -> c.mainMenuLayout, (c, v) -> c.mainMenuLayout = v),
					toggle("Server-Karte", "Spieler online und Ping von HugoSMP.net (Seitenleiste)",
						c -> c.mainMenuServerCard, (c, v) -> c.mainMenuServerCard = v),
					toggle("\"HugoSMP.net beitreten\"-Button", null,
						c -> c.mainMenuButtonEnabled, (c, v) -> c.mainMenuButtonEnabled = v)),
				new SettingsSection("Splash-Text",
					new CycleOption<>("Animation", null, SplashAnimation.class, c -> c.splashAnimation, (c, v) -> c.splashAnimation = v),
					new CycleOption<>("Farbe", null, SplashColor.class, c -> c.splashColor, (c, v) -> c.splashColor = v),
					new StringListOption("Eigene Splash-Texte", "Bei jedem Öffnen wird zufällig einer gezeigt",
						"Keine, dann ohne Splash-Text", c -> c.splashTexts, (c, v) -> c.splashTexts = v))),
			discordTab(),
			antiLeakTab(),
			streamerModeTab());
	}

	private static SettingsTab streamerModeTab() {
		return new SettingsTab("Streamer-Modus",
			new SettingsSection("Streamer-Modus",
				toggle("Streamer-Modus aktiviert", "Versteckt Namen überall, wo Text steht: Chat, Tabliste, Nametags, Scoreboard, "
					+ "Titel, Schilder und Menüs. Nur die Anzeige ändert sich, Befehle gehen mit den echten Namen raus.",
					c -> c.streamerModeEnabled, (c, v) -> c.streamerModeEnabled = v).keywords("streamer stream namen verstecken name hider"),
				new KeyBindOption("Ein/Aus-Taste", "Ohne Taste nur hier", () -> StreamerMode.toggleKey)
					.keywords("streamer-modus namen"),
				new InfoOption("Versteckt", null, ModSettings::streamerModeStatus)),
			new SettingsSection("Dein Name",
				toggle("Eigenen Namen verstecken", null,
					c -> c.streamerModeHideOwn, (c, v) -> c.streamerModeHideOwn = v).keywords("eigener name"),
				new TextOption("Ersatzname", "Steht überall, wo sonst dein Name stünde", StreamerMode.DEFAULT_OWN_NAME, 16,
					c -> c.streamerModeOwnName, (c, v) -> c.streamerModeOwnName = v).keywords("alias nick")),
			new SettingsSection("Andere Spieler",
				toggle("Namen anderer verstecken", "Alle Spieler auf dem Server und die Namen unten",
					c -> c.streamerModeHideOthers, (c, v) -> c.streamerModeHideOthers = v),
				new TextOption("Ersatzname", null, StreamerMode.DEFAULT_OTHERS_NAME, 16,
					c -> c.streamerModeOthersName, (c, v) -> c.streamerModeOthersName = v),
				toggle("Durchnummerieren", "\"Spieler 1\", \"Spieler 2\" ... bleibt bis zum Neustart gleich, so sieht man, "
					+ "wer was schreibt", c -> c.streamerModeNumbered, (c, v) -> c.streamerModeNumbered = v),
				new StringListOption("Weitere Namen", "Auch wenn sie nicht online sind, z. B. Freunde oder Zweitaccounts. "
					+ "Mit Streamer-Modus an stehen hier auch nur die Ersatznamen.",
					"Keine", c -> c.streamerModeExtraNames, (c, v) -> c.streamerModeExtraNames = v).keywords("freunde zweitaccount")),
			new SettingsSection("Skins",
				toggle("Skins anderer verstecken", "Steve, Alex und Co. statt der echten Skins, auch im Tab-Kopf. Umhänge fallen weg.",
					c -> c.streamerModeHideSkins, (c, v) -> c.streamerModeHideSkins = v).keywords("skin umhang cape"),
				toggle("Eigenen Skin verstecken", null,
					c -> c.streamerModeHideOwnSkin, (c, v) -> c.streamerModeHideOwnSkin = v).keywords("skin")));
	}

	private static String streamerModeStatus() {
		ModConfig config = ModConfigManager.CONFIG;
		if (!config.streamerModeEnabled) {
			return "Nichts, Modus ist aus";
		}
		int others = StreamerMode.hiddenOthers();
		String own = config.streamerModeHideOwn ? "Dein Name" : null;
		String rest = config.streamerModeHideOthers ? others + (others == 1 ? " anderer Name" : " andere Namen") : null;
		if (own == null && rest == null) {
			return "Keine Namen";
		}
		return own == null ? rest : rest == null ? own : own + " und " + rest;
	}

	private static SettingsSection updatesSection() {
		return new SettingsSection("Updates",
			new UpdateStatusOption().keywords("update version aktualisieren github release"),
			toggle("Nach Updates suchen", "Zeigt neue Versionen im Hauptmenü und einmal im Chat",
				c -> c.updateCheck, (c, v) -> c.updateCheck = v).keywords("update github"),
			toggle("Automatisch installieren", "Lädt neue Versionen selbst herunter, prüft sie und tauscht die Jar "
				+ "aus, wenn du das Spiel beendest", c -> c.updateAutoInstall, (c, v) -> {
					c.updateAutoInstall = v;
					ModUpdates.Release release = ModUpdates.notifiable();
					if (v && release != null && UpdateInstaller.canInstall(release)) {
						UpdateInstaller.download(release);
					}
				}).keywords("update auto download"),
			toggle("Vorabversionen", "Auch Releases, die auf GitHub als Pre-release markiert sind",
				c -> c.updatePrereleases, (c, v) -> {
					c.updatePrereleases = v;
					ModUpdates.check();
				}).keywords("update beta alpha pre-release"),
			ButtonOption.action("Alle Releases", ModUpdates.RELEASES_PAGE.replace("https://", ""), "Öffnen",
				() -> Util.getPlatform().openUri(ModUpdates.RELEASES_PAGE)).keywords("update changelog github"));
	}

	private static SettingsTab antiLeakTab() {
		return new SettingsTab("Anti-Leak",
			new SettingsSection("Anti-Leak",
				toggle("Anti-Leak aktiviert", "Für Streams und Videos: versteckt, woran Zuschauer deine Position erkennen könnten. "
					+ "Nur die Anzeige ändert sich, Spiel und Server merken nichts.",
					c -> c.antiLeakEnabled, (c, v) -> c.antiLeakEnabled = v).keywords("streamer stream koordinaten leak schutz"),
				new KeyBindOption("Ein/Aus-Taste", "Schaltet alles auf einmal, ohne Taste nur hier", () -> AntiLeak.toggleKey)
					.keywords("anti-leak streamer")),
			new SettingsSection("F3-Bildschirm",
				toggle("Koordinaten verstecken", "X und Z in Position, Block, Chunk und \"Targeted Block\", auch mit BetterF3",
					c -> c.antiLeakF3, (c, v) -> c.antiLeakF3 = v).keywords("f3 debug blur xyz"),
				new CycleOption<>("Aussehen", "Die echten Ziffern werden nie gezeichnet. Zufällig zeigt jede Sekunde einen anderen erfundenen Ort.", CoordinateMask.class,
					c -> c.antiLeakF3Style, (c, v) -> c.antiLeakF3Style = v).keywords("f3 blur verschwommen zufall random betterf3"),
				toggle("Höhe (Y) zeigen", "Y allein verrät keinen Ort und hilft beim Abbauen",
					c -> c.antiLeakShowY, (c, v) -> c.antiLeakShowY = v).keywords("f3 y höhe")),
			new SettingsSection("Welt",
				toggle("Bedrock-Muster verstecken", "Die Schichten über dem Bedrock-Boden (und unter der Nether-Decke) "
					+ "werden als glatter Bedrock gezeigt, auch Gänge darin",
					c -> c.antiLeakBedrock, (c, v) -> c.antiLeakBedrock = v).keywords("bedrock seed"),
				toggle("Erze tarnen", "Erze sehen aus wie Stein, Tiefenschiefer oder Netherrack. Achtung: auch du siehst sie dann nicht.",
					c -> c.antiLeakOres, (c, v) -> c.antiLeakOres = v).keywords("erz ore diamant xray seed"),
				toggle("Keine Block-Rotation", "Erde, Gras, Sand und Co. sind nicht mehr zufällig gedreht, Blumen und Gras "
					+ "stehen mittig. Beides hängt sonst nur von der Position ab.",
					c -> c.antiLeakNoRotation, (c, v) -> c.antiLeakNoRotation = v).keywords("textur rotation drehung versatz"),
				toggle("Wolken verschieben", "Das Wolkenmuster passt sonst zu X und Z, es wird bei jedem Start zufällig versetzt",
					c -> c.antiLeakClouds, (c, v) -> c.antiLeakClouds = v).keywords("wolken clouds")));
	}

	private static SettingsTab discordTab() {
		return new SettingsTab("Discord",
			new SettingsSection("Discord Rich Presence",
				toggle("Auf Discord-Profil anzeigen", "Zeigt Ort, Geld, Kills und Tode in deinem Discord-Status. Braucht die Discord-App am PC.",
					c -> c.discordEnabled, (c, v) -> c.discordEnabled = v).keywords("rich presence rpc status"),
				new DiscordStatusOption(),
				new DiscordPreviewOption()),
			new SettingsSection("Inhalt",
				new PillsOption("Anzeigen", "Was aus ist, fällt samt Trennzeichen aus dem Text")
					.pill("Ort", c -> c.discordShowLocation, (c, v) -> c.discordShowLocation = v)
					.pill("Geld", c -> c.discordShowMoney, (c, v) -> c.discordShowMoney = v)
					.pill("Kills", c -> c.discordShowKills, (c, v) -> c.discordShowKills = v)
					.pill("Tode", c -> c.discordShowDeaths, (c, v) -> c.discordShowDeaths = v)
					.pill("K/D", c -> c.discordShowKd, (c, v) -> c.discordShowKd = v)
					.pill("Kopf", c -> c.discordShowHead, (c, v) -> c.discordShowHead = v),
				toggle("Geld kurz", "$1,2 Mio statt $1.234.567", c -> c.discordMoneyShort, (c, v) -> c.discordMoneyShort = v),
				new CycleOption<>("Zeit", null, DiscordTimer.class, c -> c.discordTimer, (c, v) -> c.discordTimer = v),
				new CycleOption<>("Außerhalb von HugoSMP", "Hauptmenü, Einzelspieler und andere Server",
					DiscordOutside.class, c -> c.discordOutside, (c, v) -> c.discordOutside = v),
				new PillsOption("Buttons", "Andere sehen sie auf deinem Profil, du selbst kannst sie nicht klicken")
					.pill("HugoSMP-Discord", c -> c.discordButtonDiscord, (c, v) -> c.discordButtonDiscord = v)
					.pill("Mod herunterladen", c -> c.discordButtonMod, (c, v) -> c.discordButtonMod = v)),
			new SettingsSection("Eigener Text",
				new TextOption("Obere Zeile", "Platzhalter: " + DiscordPresence.PLACEHOLDERS, "z. B. {ort}", 128,
					c -> c.discordDetails, (c, v) -> c.discordDetails = v).keywords("details platzhalter"),
				new TextOption("Untere Zeile", "Teile mit · trennen, damit fehlende Werte sauber wegfallen", "z. B. {geld} · {kills} Kills", 128,
					c -> c.discordState, (c, v) -> c.discordState = v).keywords("state platzhalter")),
			new SettingsSection("Ort-Erkennung",
				new InfoOption("Gerade erkannt", "Liest Scoreboard, Tabliste, Weltname und Befehle wie /afk oder /spawn",
					ModSettings::discordDetected).keywords("ort server"),
				ButtonOption.action("Erkennung prüfen", "Schreibt in den Chat, was die Mod sieht und woher jeder Wert kommt", "Im Chat zeigen",
					() -> {
						Minecraft client = Minecraft.getInstance();
						client.setScreen(null);
						DiscordPresence.printDiagnosis(client);
					}),
				new StringListOption("Orte", "Wort=Name: das erste Wort, das gefunden wird, gibt den Ort an", null,
					c -> c.discordLocations, (c, v) -> c.discordLocations = v).keywords("afk duels pvp spawn smp"),
				SliderOption.ofInt("Spawn-Radius", "So nah am Weltspawn zählt als Spawn, 0 = aus", 0, 500, v -> (int) v + " Blöcke",
					c -> c.discordSpawnRadius, (c, v) -> c.discordSpawnRadius = v),
				new TextOption("Sonst", "Wenn nichts erkannt wird", "leer = kein Ort", 32,
					c -> c.discordFallbackLocation, (c, v) -> c.discordFallbackLocation = v)));
	}

	private static String discordDetected() {
		if (Minecraft.getInstance().level == null) {
			return "nicht im Spiel";
		}
		StatusReader.Reading reading = StatusReader.last();
		return reading.location() == null ? "nichts" : reading.location() + " (" + reading.locationSource() + ")";
	}

	private static SettingsCategory client() {
		return new SettingsCategory("Client", "client", "Fullbright, FPS-Anzeige und Bildschirm",
			new SettingsTab("Anzeige",
				new SettingsSection("Helligkeit",
					toggle("Fullbright", "Alles so hell wie mit Nachtsicht, auch in Höhlen und nachts",
						c -> c.clientFullbright, (c, v) -> c.clientFullbright = v).keywords("nachtsicht gamma hell"),
					new KeyBindOption("Fullbright-Taste", "Schaltet Fullbright an und aus", () -> ClientTweaks.fullbrightKey)
						.keywords("fullbright")),
				new SettingsSection("FPS und Ping",
					toggle("Anzeige", "Bildrate und Ping, ausgeblendet mit F1 und F3. Platz und Größe im HUD-Editor.",
						c -> c.clientFpsHudOn, (c, v) -> c.clientFpsHudOn = v).keywords("fps ping hud"),
					toggle("Ping zeigen", "Nur auf Servern", c -> c.clientFpsShowPing, (c, v) -> c.clientFpsShowPing = v).keywords("ping ms")),
				new SettingsSection("HUD",
					ButtonOption.page("HUD anordnen", "Alle Anzeigen der Mod verschieben, vergrößern und an- oder ausschalten",
						"Öffnen", () -> open(HudEditorScreen::new)).keywords("hud position verschieben größe anzeige editor"),
					new KeyBindOption("Taste", "Öffnet den HUD-Editor, auch per /hse hud", () -> HudKeys.editorKey).keywords("hud editor")),
				new SettingsSection("Bildschirm",
					toggle("Feuer tiefer", "Das Feuer beim Brennen bedeckt nur den unteren Rand",
						c -> c.clientLowFire, (c, v) -> c.clientLowFire = v).keywords("feuer fire overlay"),
					toggle("Kürbis durchsichtig", "Kein Kürbis-Rand, wenn du einen geschnitzten Kürbis trägst",
						c -> c.clientNoPumpkinBlur, (c, v) -> c.clientNoPumpkinBlur = v).keywords("kürbis pumpkin"))));
	}

	private static SettingsCategory chat() {
		return new SettingsCategory("Chat", "chat", "Erwähnungen, Filter, Rechner, Farben und TPA",
			new SettingsTab("Erwähnungen",
				new SettingsSection("Erwähnungen",
					toggle("Erwähnungen hervorheben", "Nachrichten mit deinem Namen werden markiert",
						c -> c.mentionsEnabled, (c, v) -> c.mentionsEnabled = v).keywords("mention"),
					new CycleOption<>("Markierung", "Hinweiszeile, goldene Nachricht oder beides",
						MentionStyle.class, c -> c.mentionStyle, (c, v) -> c.mentionStyle = v).keywords("mention farbe"),
					toggle("Ton bei Erwähnung", null, c -> c.mentionsSoundEnabled, (c, v) -> c.mentionsSoundEnabled = v),
					new StringListOption("Zusätzliche Wörter", "Dein Spielername wird immer erkannt", "Keine",
						c -> c.mentionTriggers, (c, v) -> c.mentionTriggers = v).keywords("trigger"))),
			new SettingsTab("Stichwörter",
				new SettingsSection("Stichwort-Alarm",
					toggle("Stichwort-Alarm", "Meldet sich, wenn eine Chatzeile alle Wörter eines Eintrags enthält, z. B. \"verkaufe elytra\". "
						+ "Wortanfänge reichen, Groß/klein und Umlaute egal.", c -> c.keywordAlarmEnabled, (c, v) -> c.keywordAlarmEnabled = v)
						.keywords("keyword alarm handel deal suche"),
					new StringListOption("Stichwörter", "Ein Eintrag pro Suche, mehrere Wörter = alle müssen vorkommen", "Keine",
						c -> c.keywordAlarmWords, (c, v) -> c.keywordAlarmWords = v).keywords("keyword"),
					new PillsOption("Wie", null)
						.pill("Ton", c -> c.keywordAlarmSound, (c, v) -> c.keywordAlarmSound = v)
						.pill("Einblendung", c -> c.keywordAlarmToast, (c, v) -> c.keywordAlarmToast = v),
					toggle("Nur Spieler-Chat", "Zeilen mit [HugoSMP] vorne zählen nicht",
						c -> c.keywordAlarmPublicOnly, (c, v) -> c.keywordAlarmPublicOnly = v))),
			new SettingsTab("Filter",
				new SettingsSection("Chat-Filter",
					new PillsOption("Angezeigte Nachrichten", "Eigene Nachrichten werden nie ausgeblendet")
						.pill("Öffentlich", c -> c.publicChatEnabled, (c, v) -> c.publicChatEnabled = v)
						.pill("System", c -> c.systemChatEnabled, (c, v) -> c.systemChatEnabled = v)
						.pill("Privat", c -> c.msgChatEnabled, (c, v) -> c.msgChatEnabled = v)
						.pill("Clan", c -> c.clanChatEnabled, (c, v) -> c.clanChatEnabled = v)
						.pill("Freunde", c -> c.friendChatEnabled, (c, v) -> c.friendChatEnabled = v))),
			new SettingsTab("Rechner",
				new SettingsSection("Chat-Rechner",
					toggle("Chat-Rechner", "Zeigt das Ergebnis einer Rechnung am Ende der Eingabe, z. B. 64*3,5 oder 1,5k+200. Tab setzt es ein.",
						c -> c.chatCalculatorEnabled, (c, v) -> c.chatCalculatorEnabled = v)),
				new SettingsSection("Verlauf",
					toggle("Chat beim Weltwechsel behalten", null,
						c -> c.persistentChatEnabled, (c, v) -> c.persistentChatEnabled = v))),
			new SettingsTab("Farben",
				new SettingsSection("Farbcode-Editor",
					ButtonOption.page("Editor öffnen", "Farben, Formate, Verläufe und Vorlagen für /rename und /sign, mit Vorschau",
						"Öffnen", () -> open(ColorCodeScreen::forHeldItem)).keywords("rename sign farbe hex verlauf gradient"),
					new KeyBindOption("Taste", "Öffnet den Editor mit dem Namen des Items in der Hand", () -> ColorCodes.openKey)
						.keywords("farbcode rename"),
					toggle("Vorschau im Chat", "Zeigt beim Tippen von /rename und /sign, wie die Farben aussehen. Strg+E öffnet den Editor.",
						c -> c.colorChatPreview, (c, v) -> c.colorChatPreview = v).keywords("rename sign farbcode")),
				new SettingsSection("Server",
					new CycleOption<>("Farben", "HugoSMP liest nur die 16 Chat-Farben. Hex nur für andere Server.",
						HexStyle.class, c -> c.colorHexStyle, (c, v) -> c.colorHexStyle = v).keywords("hex rgb farbcode"),
					SliderOption.ofInt("Zeichenlimit", "Höchstens so viele Zeichen nach dem Befehl, Codes mitgezählt. HugoSMP: 35",
						0, 128, v -> (int) v == 0 ? "aus" : String.valueOf((int) v),
						c -> c.colorMaxLength, (c, v) -> c.colorMaxLength = v).keywords("limit länge 35"),
					new StringListOption("Befehle", "Ohne Schrägstrich, z. B. rename. Für diese Befehle gibt es Vorschau und Editor.",
						"Keine, dann rename und sign", c -> c.colorCommands, (c, v) -> c.colorCommands = v).keywords("rename sign")),
				new SettingsSection("Vorlagen",
					new InfoOption("Gespeichert", null, () -> String.valueOf(ModConfigManager.CONFIG.colorTemplates.size())),
					ButtonOption.action("Beispiele zurückholen", "Fügt die mitgelieferten Verläufe wieder hinzu", "Hinzufügen",
						ColorCodes::restoreDefaults),
					ButtonOption.danger("Alle Vorlagen löschen", null, "Löschen", () -> {
						ModConfigManager.CONFIG.colorTemplates.clear();
						ModConfigManager.save();
					}))),
			new SettingsTab("TPA",
				new SettingsSection("Teleport-Anfragen",
					toggle("TPA-Toast anzeigen", "Kleine Einblendung bei einer neuen Anfrage",
						c -> c.tpaToastEnabled, (c, v) -> c.tpaToastEnabled = v),
					new KeyBindOption("Anfrage annehmen", "Taste zum schnellen Annehmen", () -> TpaKeybinds.acceptKey))));
	}

	private static SettingsCategory players() {
		return new SettingsCategory("Spieler", "player", "Nametags, Tabliste und HugoGuard",
			new SettingsTab("Nametags",
				new SettingsSection("Nametag",
					ButtonOption.page("Nametag-Stil", "Nur für dich: Effekte für deinen Namen in Tabliste, Nametag und Chat",
						"Bearbeiten", () -> open(NametagStyleScreen::build)).keywords("namens-stil farbe effekt"),
					ButtonOption.page("Zweiter Nametag", "Nur für dich: eine Zeile über oder unter deinem Nametag (F5)",
						"Bearbeiten", () -> open(SecondNametagScreen::build)))),
			new SettingsTab("Tabliste",
				new SettingsSection("Tabliste",
					toggle("Kompakt anzeigen", null, c -> c.tabListCompactEnabled, (c, v) -> c.tabListCompactEnabled = v),
					new SliderOption("Skalierung", null, 0.5, 1.0, 0.05, v -> Math.round(v * 100) + "%",
						c -> c.tabListScale, (c, v) -> c.tabListScale = v))),
			new SettingsTab("HugoGuard",
				new SettingsSection("HugoGuard",
					toggle("HugoGuard aktiviert", "Markiert Spieler, die auf hugoguard.net als Scammer oder vertrauenswürdig gelistet sind",
						c -> c.hugoGuardEnabled, (c, v) -> c.hugoGuardEnabled = v).keywords("scammer"),
					toggle("Trusted-Spieler markieren", "Grünes TRUSTED-Abzeichen",
						c -> c.hugoGuardTrusted, (c, v) -> c.hugoGuardTrusted = v),
					toggle("Leuchten (nur in Sicht)", "Roter bzw. grüner Schimmer, von Wänden verdeckt",
						c -> c.hugoGuardGlow, (c, v) -> c.hugoGuardGlow = v)),
				new SettingsSection("Warnungen",
					new PillsOption("Wo gewarnt wird", null)
						.pill("Über dem Kopf", c -> c.hugoGuardOverhead, (c, v) -> c.hugoGuardOverhead = v)
						.pill("Im Chat", c -> c.hugoGuardChat, (c, v) -> c.hugoGuardChat = v)
						.pill("Bei TPA-Anfragen", c -> c.hugoGuardTpa, (c, v) -> c.hugoGuardTpa = v))));
	}

	private static SettingsCategory economy() {
		return new SettingsCategory("Wirtschaft", "economy", "Geld-Tracker, Zahlungen, Aufträge, Spawner-Lager und Farm",
			new SettingsTab("Geld",
				new SettingsSection("Geld-Tracker",
					toggle("Geld-Tracker", "Liest den Kontostand aus dem Scoreboard und ordnet Änderungen Zahlungen, Menüs und Befehlen zu. Nur auf HugoSMP.",
						c -> c.moneyTrackerEnabled, (c, v) -> c.moneyTrackerEnabled = v).keywords("geld money tracker einnahmen"),
					ButtonOption.page("Übersicht", "Verlauf und Quellen, auch per /hse geld", "Öffnen", () -> open(MoneyScreen::new)),
					new InfoOption("Kontostand", null, () -> MoneyTracker.balance() < 0 ? "nicht erkannt" : Money.format(MoneyTracker.balance()))),
				new SettingsSection("Anzeige",
					toggle("HUD-Anzeige", "Kontostand und Änderung. Platz und Größe im HUD-Editor.",
						c -> c.moneyHudOn, (c, v) -> c.moneyHudOn = v).keywords("geld hud"),
					new CycleOption<>("Änderung seit", null, MoneySpan.class, c -> MoneySpan.values()[Math.max(0, Math.min(1, c.moneyHudMode))],
						(c, v) -> c.moneyHudMode = v.ordinal()))),
			new SettingsTab("Zahlungen",
				new SettingsSection("Zahlungs-Log",
					toggle("Zahlungen speichern", "Jede Zahlung an dich und von dir, auch nach einem Neustart",
						c -> c.payLogEnabled, (c, v) -> c.payLogEnabled = v).keywords("pay log zahlung"),
					ButtonOption.page("Log öffnen", "Auch per /hse zahlungen", "Öffnen", () -> open(PayLogScreen::new))),
				new SettingsSection("Beim Tippen von /pay",
					toggle("Scammer-Warnung", "Name rot und Warnung über der Eingabe, wenn HugoGuard den Spieler als Scammer führt",
						c -> c.payWarning, (c, v) -> c.payWarning = v).keywords("scammer hugoguard pay warnung"),
					toggle("Zweimal Enter bei Scammern", "Die erste Enter-Taste schickt die Zahlung noch nicht ab",
						c -> c.payConfirmScammer, (c, v) -> c.payConfirmScammer = v),
					toggle("Betrag anzeigen", "\"Zahlung an Name: $10.000\" und die letzte Zahlung mit dem Spieler",
						c -> c.payPreview, (c, v) -> c.payPreview = v).keywords("pay vorschau betrag"))),
			new SettingsTab("Aufträge",
				new SettingsSection("Aufträge-Helfer",
					toggle("Aufträge-Helfer", "Im Aufträge-Menü: was du aus deinem Inventar liefern kannst und was es bringt",
						c -> c.ordersHelperEnabled, (c, v) -> c.ordersHelperEnabled = v).keywords("order auftrag liefern"),
					toggle("Lieferbare markieren", "Grüner Rahmen um lieferbare Aufträge, goldener um den besten",
						c -> c.ordersMarkFillable, (c, v) -> c.ordersMarkFillable = v))),
			new SettingsTab("Spawner-Lager",
				new SettingsSection("Erinnerung",
					toggle("Spawner-Lager merken", "Merkt sich beim Öffnen, wie voll ein Spawner ist, und rechnet hoch, wann er voll wird",
						c -> c.spawnerReminderEnabled, (c, v) -> c.spawnerReminderEnabled = v).keywords("spawner lager voll storage"),
					SliderOption.ofInt("Erinnern ab", null, 50, 100, v -> (int) v + " %",
						c -> c.spawnerReminderPercent, (c, v) -> c.spawnerReminderPercent = v),
					toggle("Einblendung", "Kleine Meldung oben rechts", c -> c.spawnerReminderToast, (c, v) -> c.spawnerReminderToast = v),
					toggle("HUD-Anzeige", "Die vollsten Spawner. Platz im HUD-Editor.", c -> c.spawnerHudOn, (c, v) -> c.spawnerHudOn = v),
					ButtonOption.page("Alle Spawner", "Auch per /hse spawner", "Öffnen", () -> open(SpawnerStorageScreen::new)))),
			new SettingsTab("Farm",
				new SettingsSection("Farm-Tracker",
					toggle("Farm-Tracker", "Zählt, was du aufhebst, mit Menge und Wert pro Stunde. Pausiert nach 2 Minuten ohne Drops.",
						c -> c.farmTrackerEnabled, (c, v) -> c.farmTrackerEnabled = v).keywords("farm drops pro stunde"),
					toggle("HUD-Anzeige", "Platz im HUD-Editor", c -> c.farmHudOn, (c, v) -> c.farmHudOn = v).keywords("farm hud"),
					ButtonOption.action("Zurücksetzen", "Auch per /hse farm reset", "Zurücksetzen", FarmTracker::reset)),
				new SettingsSection("Warnungen",
					toggle("Inventar voll", "Meldet sich beim Farmen, wenn kein Platz mehr frei ist",
						c -> c.farmWarnFull, (c, v) -> c.farmWarnFull = v),
					toggle("Werkzeug fast kaputt", null, c -> c.farmWarnDurability, (c, v) -> c.farmWarnDurability = v),
					SliderOption.ofInt("Warnen ab", "Restliche Haltbarkeit", 1, 50, v -> (int) v + " %",
						c -> c.farmDurabilityPercent, (c, v) -> c.farmDurabilityPercent = v))));
	}

	/** What the money HUD's change counts from. */
	public enum MoneySpan implements Labeled {
		SESSION("Spielstart"), TODAY("Heute");

		private final String label;

		MoneySpan(String label) {
			this.label = label;
		}

		@Override
		public Component getDisplayName() {
			return Component.literal(this.label);
		}
	}

	private static SettingsCategory inventory() {
		return new SettingsCategory("Inventar & Handel", "inventory", "Inventar-Buttons, Layouts, Auktionshaus und Verkaufen",
			new SettingsTab("Inventar",
				new SettingsSection("Inventar-Buttons",
					toggle("Im Inventar anzeigen", "Buttons neben und über dem Inventar, die einen Befehl ausführen",
						c -> c.inventoryButtonsEnabled, (c, v) -> c.inventoryButtonsEnabled = v),
					ButtonOption.page("Buttons anordnen", "Befehle, Icons und Plätze festlegen", "Bearbeiten",
						() -> open(InventoryButtonsScreen::new)))),
			new SettingsTab("Layouts",
				new SettingsSection("Inventar-Layout",
					toggle("Layout-Buttons im Inventar", "Auge zeigt, wo jedes Item hingehört, Buch speichert ein Layout",
						c -> c.inventoryLayoutEnabled, (c, v) -> c.inventoryLayoutEnabled = v),
					ButtonOption.danger("Alle Layouts löschen", null, "Löschen", InventoryLayout::clear))),
			new SettingsTab("Auktionshaus",
				new SettingsSection("Anzeige",
					toggle("Preis pro Item anzeigen", "Im Tooltip unter dem Gesamtpreis",
						c -> c.pricePerItemEnabled, (c, v) -> c.pricePerItemEnabled = v).keywords("ah")),
				new SettingsSection("Suche",
					toggle("Eigenes Suchmenü statt Schild", "Suchfenster mit Vorschlägen, letzten Suchen und Favoriten",
						c -> c.auctionSearchEnabled, (c, v) -> c.auctionSearchEnabled = v).keywords("ah order"),
					new CycleOption<>("Vorschläge senden Namen in", "Selbst Getipptes wird immer genau so gesendet",
						AuctionSearchLanguage.class, c -> c.auctionSearchLanguage, (c, v) -> c.auctionSearchLanguage = v),
					toggle("Gesehene Preise merken", "Zeigt bei Vorschlägen den zuletzt gesehenen Preis",
						c -> c.auctionPriceMemoryEnabled, (c, v) -> c.auctionPriceMemoryEnabled = v),
					new StringListOption("Favoriten", null, "Noch keine, ein Stern in der Suche fügt hinzu",
						c -> c.auctionSearchFavorites, (c, v) -> c.auctionSearchFavorites = v),
					ButtonOption.danger("Suchverlauf löschen", null, "Löschen", () -> {
						ModConfigManager.CONFIG.auctionSearchHistory.clear();
						ModConfigManager.save();
					})),
				new SettingsSection("Markieren",
					toggle("Markierte Items hervorheben", "Das Kästchen neben dem Stern in der Suche markiert genau dieses Item im AH",
						c -> c.auctionMarkHighlight, (c, v) -> c.auctionMarkHighlight = v).keywords("ah markieren farbig rahmen"),
					new InfoOption("Markierte Items", null, () -> {
						int marked = ModConfigManager.CONFIG.auctionMarkedItems.size();
						return marked == 0 ? "keine" : marked + (marked == 1 ? " Item" : " Items");
					}),
					ButtonOption.danger("Alle Markierungen entfernen", null, "Entfernen", MarkedItems::clear)),
				new SettingsSection("HugoSMP-Items",
					toggle("Items merken und suchbar machen", "Items aus AH, Orders und Inventar mit Name, Farbe und Modell",
						c -> c.customItemsEnabled, (c, v) -> c.customItemsEnabled = v).keywords("custom ah suche"),
					new InfoOption("Bekannte Items", null, () -> CustomItems.count() + (CustomItems.count() == 1 ? " Item" : " Items")),
					ButtonOption.danger("Bekannte Items vergessen", null, "Löschen", CustomItems::clear)),
				new SettingsSection("Limitierte Items",
					toggle("Preis pro Stunde anzeigen", "Restlaufzeit und Preis pro Stunde im Tooltip, Übersicht unter dem Menü",
						c -> c.limitedItemsPerHour, (c, v) -> c.limitedItemsPerHour = v).keywords("ah limitiert ablauf zeit"),
					toggle("Bestes Angebot markieren", "Goldener Rahmen um das günstigste pro Stunde jeder Sorte",
						c -> c.limitedItemsHighlightBest, (c, v) -> c.limitedItemsHighlightBest = v).keywords("ah limitiert"),
					toggle("Verkaufspreis vorschlagen", "Bei eigenen limitierten Items: welcher Preis das beste Angebot im AH wäre",
						c -> c.limitedItemsSellHint, (c, v) -> c.limitedItemsSellHint = v).keywords("ah limitiert verkaufen preis"),
					SliderOption.ofInt("Lebensdauer", "Ab Erstelldatum", 1, 168,
						v -> (int) v + " Std" + ((int) v % 24 == 0 ? " (" + (int) v / 24 + " T)" : ""),
						c -> c.limitedItemsLifetimeHours, (c, v) -> c.limitedItemsLifetimeHours = v).keywords("limitiert tage"))),
			spawnerTab(),
			sellTab());
	}

	private static SettingsTab spawnerTab() {
		return new SettingsTab("Spawner",
			new SettingsSection("Spawner-Preise",
				toggle("Preise im Tooltip", "Ankauf und Verkauf von VOID Market bei jedem Spawner-Item, nur auf HugoSMP",
					c -> c.spawnerPricesTooltip, (c, v) -> c.spawnerPricesTooltip = v).keywords("spawner preis void market ankauf verkauf"),
				ButtonOption.page("Alle Spawner-Preise", "Auch per /spawnerpreise", "Öffnen", () -> open(SpawnerPricesScreen::new)),
				new InfoOption("Stand", "VOID-Market-Preise, alle 5 Minuten neu",
					() -> SpawnerPrices.snapshot() == null ? "noch nicht geladen" : SpawnerPrices.age(SpawnerPrices.snapshot().fetchedAt()))));
	}

	private static SettingsTab sellTab() {
		return new SettingsTab("Verkaufen",
			new SettingsSection("Verkaufs-Assistent",
				toggle("Verkaufs-Assistent", "Nur auf HugoSMP. Verkauft per /sell, was deine Regeln erlauben.",
					c -> c.sellEnabled, (c, v) -> c.sellEnabled = v).keywords("sell makro macro afk"),
				new KeyBindOption("Jetzt verkaufen", "Verkauft einmal alles nach deinen Regeln", () -> SellFeature.sellKey),
				new KeyBindOption("AFK-Farm an/aus", "Ohne eigene Taste: Shift + Verkaufen-Taste", () -> SellFeature.afkKey),
				toggle("Vorher bestätigen", "Zeigt die Vorschau mit Rückfrage beim ersten Mal und nach jeder Regeländerung",
					c -> c.sellConfirm, (c, v) -> c.sellConfirm = v)),
			new SettingsSection("Regeln", new SellRulesOption()),
			new SettingsSection("Schutz",
				new InfoOption("Wird nie verkauft", "Umbenannte, verzauberte, gefüllte, beschädigte oder sonst veränderte Items, "
					+ "die Hotbar, Offhand, Rüstung und gesperrte Slots", () -> "immer aktiv").keywords("schutz"),
				new InfoOption("Gesperrte Slots", "Alt + Rechtsklick auf einen Slot im Inventar sperrt ihn",
					() -> String.valueOf(ModConfigManager.CONFIG.sellLockedSlots.size())),
				ButtonOption.danger("Alle Slots entsperren", null, "Entsperren", () -> {
					ModConfigManager.CONFIG.sellLockedSlots.clear();
					ModConfigManager.save();
				}),
				toggle("Nur volle Stapel", "Angefangene Stapel bleiben im Inventar",
					c -> c.sellFullStacksOnly, (c, v) -> c.sellFullStacksOnly = v)),
			new SettingsSection("Tempo",
				new CycleOption<>("Geschwindigkeit", "Normal ist menschennah, Schnell so schnell wie ein Shift-Doppelklick",
					SellSpeed.class, c -> c.sellSpeed, (c, v) -> c.sellSpeed = v).keywords("tempo speed"),
				SliderOption.ofInt("Pause zwischen Klicks", "Nur bei Eigene, 20 Ticks = 1 s", 1, 20, v -> (int) v + " Ticks",
					c -> c.sellCustomDelay, (c, v) -> c.sellCustomDelay = v),
				SliderOption.ofInt("Klicks pro Schritt", "Nur bei Eigene", 1, 27, v -> String.valueOf((int) v),
					c -> c.sellCustomClicks, (c, v) -> c.sellCustomClicks = v)),
			new SettingsSection("AFK-Farm",
				SliderOption.ofInt("Verkaufen ab", "Sammelt im Verkaufsmenü, bis es so voll ist", 0, 100, v -> (int) v + " %",
					c -> c.sellThreshold, (c, v) -> c.sellThreshold = v),
				new CycleOption<>("Beim Join starten", "Startet die AFK-Farm 10 s nach dem Betreten von HugoSMP",
					SellAutostart.class, c -> c.sellAutostart, (c, v) -> c.sellAutostart = v).keywords("autostart rejoin"),
				toggle("Status-Anzeige", "Nur solange die AFK-Farm läuft oder pausiert. Platz im HUD-Editor.",
					c -> c.sellHudOn, (c, v) -> c.sellHudOn = v).keywords("hud")));
	}

	private static SettingsCategory tools() {
		return new SettingsCategory("Werkzeuge", "tools", "Notizblock, Galerie, Tastatur, Farm-Heatmap und Streamer-Tools",
			new SettingsTab("Notizen",
				new SettingsSection("Notizblock",
					ButtonOption.page("Notizen öffnen", null, "Öffnen", () -> open(NotesScreen::new)),
					new KeyBindOption("Taste", null, () -> NotesFeature.openKey).keywords("notizblock"),
					toggle("Angeheftete Notizen zeigen", "Notizen mit Stecknadel stehen auf dem Bildschirm",
						c -> c.notesHudOn, (c, v) -> c.notesHudOn = v).keywords("notiz hud anheften"),
					ButtonOption.page("Position", "Im HUD-Editor, zusammen mit den anderen Anzeigen", "Verschieben",
						() -> open(HudEditorScreen::new)).keywords("notiz hud verschieben"))),
			new SettingsTab("Galerie",
				new SettingsSection("Screenshot-Galerie",
					ButtonOption.page("Galerie öffnen", null, "Öffnen", () -> open(GalleryScreen::new)),
					new KeyBindOption("Taste", null, () -> GalleryFeature.openKey).keywords("galerie screenshot"))),
			new SettingsTab("Tastatur",
				new SettingsSection("Tastatur-Ansicht",
					ButtonOption.page("Tastenbelegung als Tastatur", "Zeigt, welche Taste was macht", "Öffnen",
						() -> open(KeyboardScreen::new)))),
			new SettingsTab("Heatmap",
				new SettingsSection("Entity-Heatmap",
					toggle("Heatmap aktiviert", "Farbige Kacheln mit der Anzahl Mobs und Items pro Block, durch Glas sichtbar",
						c -> c.entityHeatmapEnabled, (c, v) -> c.entityHeatmapEnabled = v).keywords("farm entity"),
					new KeyBindOption("Ein/Aus-Taste", null, () -> EntityHeatmap.toggleKey).keywords("heatmap"),
					toggle("Items mitzählen", null, c -> c.entityHeatmapItems, (c, v) -> c.entityHeatmapItems = v)),
				new SettingsSection("Bereich und Grenzen",
					SliderOption.ofInt("Radius", null, 4, 64, v -> (int) v + " Blöcke",
						c -> c.entityHeatmapRadius, (c, v) -> c.entityHeatmapRadius = v),
					SliderOption.ofInt("Cramming-Limit", "Ab hier rot, Server-Standard 24", 2, 100, v -> String.valueOf((int) v),
						c -> c.entityHeatmapCrammingLimit, (c, v) -> c.entityHeatmapCrammingLimit = v),
					SliderOption.ofInt("Anzeigen ab", "Blöcke mit weniger Entitäten ausblenden", 1, 20, v -> String.valueOf((int) v),
						c -> c.entityHeatmapMinCount, (c, v) -> c.entityHeatmapMinCount = v))),
			streamerTab());
	}

	private static SettingsTab streamerTab() {
		return new SettingsTab("Streamer",
			new SettingsSection("Streamer-Tools",
				ButtonOption.page("Streamer-Panel", "Spenden-Auktion und Giveaway mit Glücksrad, auch per /streamer", "Öffnen",
					() -> open(StreamerScreen::new)).keywords("auktion giveaway glücksrad stream"),
				new KeyBindOption("Taste", null, () -> StreamerFeature.panelKey).keywords("streamer panel"),
				toggle("Töne", "Gebote, Beitritte, Glücksrad und Gewinner", c -> c.streamerSounds, (c, v) -> c.streamerSounds = v)),
			new SettingsSection("Spenden-Auktion",
				toggle("Zahlungen zusammenrechnen", "2x $10.000 von einem Spieler = Gebot $20.000. Aus: nur die größte Zahlung zählt.",
					c -> c.streamerSumPayments, (c, v) -> c.streamerSumPayments = v),
				toggle("Spätes Gebot verlängert", "Ein neues Höchstgebot kurz vor Schluss setzt die Uhr wieder hoch",
					c -> c.streamerExtendLateBids, (c, v) -> c.streamerExtendLateBids = v),
				SliderOption.ofInt("Verlängern auf", null, 5, 60, v -> (int) v + " s",
					c -> c.streamerExtendSeconds, (c, v) -> c.streamerExtendSeconds = v),
				toggle("Ansagen im Chat", "Schreibt Start, neues Höchstgebot (höchstens alle 10 s) und Gewinner in den Chat",
					c -> c.streamerAnnounce, (c, v) -> c.streamerAnnounce = v).keywords("broadcast nachricht")),
			new SettingsSection("Zahlungen erkennen",
				new InfoOption("Zuletzt erkannt", "Nur Server-Nachrichten zählen, Chat-Nachrichten von Spielern nie",
					ModSettings::streamerLastPayment).keywords("pay zahlung"),
				ButtonOption.action("Zahlungstext anlernen", "Falls HugoSMP Zahlungen anders schreibt: starten und dir $1 schicken lassen",
					"Anlernen", () -> ChatPatterns.startLearning(ChatPatterns.Learning.PAYMENT)),
				ButtonOption.action("Flüstertext anlernen", "Falls /msg-Beitritte nicht erkannt werden: starten und dir eine /msg schicken lassen",
					"Anlernen", () -> ChatPatterns.startLearning(ChatPatterns.Learning.WHISPER)),
				ButtonOption.danger("Angelerntes vergessen", "Zurück zu den eingebauten Texten", "Vergessen", () -> {
					ChatPatterns.forget(ChatPatterns.Learning.PAYMENT);
					ChatPatterns.forget(ChatPatterns.Learning.WHISPER);
				}),
				ButtonOption.action("Zahlungs-Protokoll", "Jede erkannte Zahlung mit Uhrzeit, zum Nachprüfen und Zurückzahlen",
					"Ordner öffnen", () -> Util.getPlatform().openPath(DonationAuction.logFile().getParent()))),
			new SettingsSection("Giveaway: Ingame",
				toggle("Beitritt per /msg", "Zuschauer schreiben dir das Wort privat", c -> c.streamerIngameJoin, (c, v) -> c.streamerIngameJoin = v),
				new TextOption("Wort", "Groß/klein egal, mit oder ohne !", "join", 24,
					c -> c.streamerIngameJoinWord, (c, v) -> c.streamerIngameJoinWord = v)),
			new SettingsSection("Giveaway: Twitch",
				toggle("Beitritt über Twitch-Chat", "Liest nur mit, ohne Login. Verbindet nur, solange die Teilnahme offen ist.",
					c -> c.streamerTwitchJoin, (c, v) -> c.streamerTwitchJoin = v),
				new TextOption("Kanal", "Dein Twitch-Name oder der Link zum Kanal", "z. B. levi_hugo", 64,
					c -> c.streamerTwitchChannel, (c, v) -> c.streamerTwitchChannel = v).keywords("twitch kanal channel"),
				new TextOption("Befehl", "Mit MC-Namen dahinter klappt später auch TPA: !join Name", "!join", 24,
					c -> c.streamerTwitchJoinWord, (c, v) -> c.streamerTwitchJoinWord = v),
				toggle("Nur Subs, VIPs und Mods", null, c -> c.streamerTwitchSubsOnly, (c, v) -> c.streamerTwitchSubsOnly = v),
				new InfoOption("Verbindung", null, ModSettings::streamerTwitchStatus)),
			new SettingsSection("Glücksrad",
				SliderOption.ofInt("Drehdauer", null, 3, 15, v -> (int) v + " s",
					c -> c.streamerWheelSeconds, (c, v) -> c.streamerWheelSeconds = v)));
	}

	private static String streamerLastPayment() {
		ChatPatterns.Payment last = ChatPatterns.lastPayment();
		if (ChatPatterns.learning() != ChatPatterns.Learning.NONE) {
			return "lerne gerade…";
		}
		return last == null ? "noch keine" : last.name() + " " + Money.format(last.amount());
	}

	private static String streamerTwitchStatus() {
		var config = ModConfigManager.CONFIG;
		if (!config.streamerTwitchJoin) {
			return "aus";
		}
		if (TwitchChat.normalize(config.streamerTwitchChannel).isEmpty()) {
			return "kein Kanal";
		}
		return switch (StreamerFeature.twitch().status()) {
			case CONNECTED -> "verbunden";
			case CONNECTING -> "verbindet…";
			case FAILED -> "getrennt";
			default -> Giveaway.open() ? "aus" : "wartet aufs Giveaway";
		};
	}

	// ------------------------------------------------------------------ helpers

	private static ToggleOption toggle(String label, String description, Function<ModConfig, Boolean> getter,
		BiConsumer<ModConfig, Boolean> setter) {
		return new ToggleOption(label, description, getter, setter);
	}

	private static void open(Function<Screen, ? extends Screen> factory) {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(factory.apply(client.screen));
	}

	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}
}
