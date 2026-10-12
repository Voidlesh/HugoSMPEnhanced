package com.hugosmpenhanced.client.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hugosmpenhanced.client.feature.antileak.CoordinateMask;
import com.hugosmpenhanced.client.feature.auction.AuctionSearchLanguage;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodes;
import com.hugosmpenhanced.client.feature.colorcodes.ColorTemplate;
import com.hugosmpenhanced.client.feature.colorcodes.HexStyle;
import com.hugosmpenhanced.client.feature.discord.DiscordOutside;
import com.hugosmpenhanced.client.feature.discord.DiscordTimer;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButton;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonPosition;
import com.hugosmpenhanced.client.feature.inventorybuttons.InventoryButtonSpots;
import com.hugosmpenhanced.client.feature.inventorylayout.SavedInventoryLayout;
import com.hugosmpenhanced.client.feature.mainmenu.MainMenuLayout;
import com.hugosmpenhanced.client.feature.mainmenu.SplashAnimation;
import com.hugosmpenhanced.client.feature.mainmenu.SplashColor;
import com.hugosmpenhanced.client.feature.nametag.NametagEffect;
import com.hugosmpenhanced.client.feature.notes.Note;
import com.hugosmpenhanced.client.feature.secondnametag.SecondNametagPosition;
import com.hugosmpenhanced.client.feature.sell.SellAutostart;
import com.hugosmpenhanced.client.feature.sell.SellHudPosition;
import com.hugosmpenhanced.client.feature.sell.SellRule;
import com.hugosmpenhanced.client.feature.sell.SellSpeed;

public class ModConfig {
	// Mention-Highlighting
	public boolean mentionsEnabled = true;
	public boolean mentionsSoundEnabled = true;
	public List<String> mentionTriggers = new ArrayList<>();

	// Preis pro Item
	public boolean pricePerItemEnabled = true;

	// Auktionshaus-Suche statt Schild
	public boolean auctionSearchEnabled = true;
	public AuctionSearchLanguage auctionSearchLanguage = AuctionSearchLanguage.GAME;
	public boolean auctionPriceMemoryEnabled = true;
	public List<String> auctionSearchHistory = new ArrayList<>();
	public List<String> auctionSearchFavorites = new ArrayList<>();

	// HugoSMP-Items und limitierte Items im Auktionshaus
	public boolean customItemsEnabled = true;
	public boolean limitedItemsPerHour = true;
	public boolean limitedItemsHighlightBest = true;
	public int limitedItemsLifetimeHours = 72;

	// Spawner-Preise von VOID Market (über hugosmptools.com/trading)
	public boolean spawnerPricesTooltip = true;

	// TPA-Toast
	public boolean tpaToastEnabled = true;

	// Chat beim Weltwechsel erhalten
	public boolean persistentChatEnabled = true;

	// Updates von GitHub
	public boolean updateCheck = true;
	public boolean updateAutoInstall = false;
	public boolean updatePrereleases = false;
	public String updateSkippedVersion = "";

	// Einstellungsmenü: Logo-Knopf im Pausemenü
	public boolean pauseMenuSettingsButton = true;

	// Hauptmenü-Button zum direkten Beitreten
	public boolean mainMenuButtonEnabled = true;

	// Eigenes Hauptmenü
	public boolean mainMenuCustom = true;
	public MainMenuLayout mainMenuLayout = MainMenuLayout.CLASSIC;
	public boolean mainMenuServerCard = true;
	public List<String> splashTexts = new ArrayList<>(List.of(
			"Willkommen auf HugoSMP!", "Jetzt mit Notizblock!", "Hugo war hier", "Kein Hack-Client!", "Diamanten > Gold"));
	public SplashAnimation splashAnimation = SplashAnimation.PULSE;
	public SplashColor splashColor = SplashColor.YELLOW;

	// Chat-Filter
	public boolean publicChatEnabled = true;
	public boolean systemChatEnabled = true;
	public boolean msgChatEnabled = true;
	public boolean clanChatEnabled = true;
	public boolean friendChatEnabled = true;

	// Tabliste
	public boolean tabListCompactEnabled = false;
	public double tabListScale = 0.75;

	// Namens-Stil
	public Set<NametagEffect> nametagEffects = new LinkedHashSet<>();

	// Zweiter Nametag
	public boolean secondNametagEnabled = false;
	public String secondNametagText = "";
	public SecondNametagPosition secondNametagPosition = SecondNametagPosition.ABOVE;
	public double secondNametagScale = 1.0;
	public boolean secondNametagBackground = true;

	// HugoGuard-Scammer-Warnung
	public boolean hugoGuardEnabled = true;
	public boolean hugoGuardOverhead = true;
	public boolean hugoGuardChat = true;
	public boolean hugoGuardTpa = true;
	public boolean hugoGuardTrusted = true;
	public boolean hugoGuardGlow = true;
	// Chat-Rechner
	public boolean chatCalculatorEnabled = true;
	// Farbcode-Editor (/rename, /sign)
	public boolean colorChatPreview = true;
	public HexStyle colorHexStyle = HexStyle.LEGACY_ONLY;
	/** Most characters (codes included) the server takes after /rename or /sign; 0 = only the chat limit. */
	public int colorMaxLength = 35;
	public int colorConfigVersion = 0;
	public List<String> colorCommands = new ArrayList<>(List.of("rename", "sign"));
	public List<ColorTemplate> colorTemplates = ColorCodes.defaultTemplates();
	/** Last used RGB colours as "#RRGGBB", newest first. */
	public List<String> colorRecent = new ArrayList<>();
	// Notizblock
	public List<Note> notes = new ArrayList<>();
	public int notesHudX = 4;
	public int notesHudY = 4;
	// Entity-Heatmap
	public boolean entityHeatmapEnabled = true;
	public int entityHeatmapRadius = 16;
	public int entityHeatmapCrammingLimit = 24;
	public int entityHeatmapMinCount = 1;
	public boolean entityHeatmapItems = true;
	// Inventar-Buttons
	public boolean inventoryButtonsEnabled = true;
	public List<InventoryButton> inventoryButtons = new ArrayList<>();
	// Inventar-Layouts
	public boolean inventoryLayoutEnabled = true;
	public List<SavedInventoryLayout> inventoryLayouts = new ArrayList<>();
	/** Index of the layout shown as ghosts, -1 for none. */
	public int inventoryLayoutShownIndex = -1;
	public InventoryButton inventoryLayoutShowButton = InventoryButton.builtIn("minecraft:ender_eye",
		InventoryButtonPosition.TOP, InventoryButtonSpots.CORNER_START);
	public InventoryButton inventoryLayoutSaveButton = InventoryButton.builtIn("minecraft:writable_book",
		InventoryButtonPosition.TOP, InventoryButtonSpots.CORNER_START + 1);
	/** Single layout of the first version, moved into {@link #inventoryLayouts} on first use. */
	public List<String> inventoryLayout;
	public boolean inventoryLayoutShown;
	// Verkaufs-Assistent
	public boolean sellEnabled = false;
	public List<SellRule> sellRules = new ArrayList<>();
	/** Player inventory indices (0-35) that are never sold from. */
	public Set<Integer> sellLockedSlots = new LinkedHashSet<>();
	public boolean sellFullStacksOnly = false;
	public boolean sellConfirm = true;
	/** Rules the preview was last confirmed for, see SellPlan#rulesKey. */
	public String sellConfirmedRules = "";
	public SellSpeed sellSpeed = SellSpeed.NORMAL;
	public int sellCustomDelay = 3;
	public int sellCustomClicks = 3;
	public int sellThreshold = 90;
	public SellAutostart sellAutostart = SellAutostart.IF_WAS_ON;
	public boolean sellAfkWasOn = false;
	public SellHudPosition sellHudPosition = SellHudPosition.TOP_CENTER;
	/** /sell price per single item, learned from sales of one item type. */
	public Map<String, Double> sellPrices = new HashMap<>();
	public String sellCommand = "sell";
	// Discord Rich Presence
	public boolean discordEnabled = true;
	public String discordDetails = "{ort}";
	public String discordState = "{geld} · {kills} Kills · {tode} Tode · K/D {kd}";
	public boolean discordShowLocation = true;
	public boolean discordShowMoney = true;
	public boolean discordShowKills = true;
	public boolean discordShowDeaths = true;
	public boolean discordShowKd = false;
	public boolean discordShowHead = true;
	public boolean discordMoneyShort = false;
	public DiscordTimer discordTimer = DiscordTimer.JOIN;
	public DiscordOutside discordOutside = DiscordOutside.SHOW;
	public boolean discordButtonDiscord = true;
	public boolean discordButtonMod = true;
	/** "word=Name": the first word found in the scoreboard, world name or warp command names the place. */
	public List<String> discordLocations = new ArrayList<>(List.of("afk=AFK-Welt", "duel=Duels", "pvp=PvP", "spawn=Spawn",
		"lobby=Spawn", "farmwelt=Farmwelt", "smp=SMP", "survival=SMP"));
	public int discordSpawnRadius = 150;
	public String discordFallbackLocation = "SMP";
	// Anti-Leak (Streamer-Schutz)
	public boolean antiLeakEnabled = false;
	public boolean antiLeakF3 = true;
	public CoordinateMask antiLeakF3Style = CoordinateMask.BLUR;
	public boolean antiLeakShowY = true;
	public boolean antiLeakBedrock = true;
	public boolean antiLeakOres = false;
	public boolean antiLeakNoRotation = true;
	public boolean antiLeakClouds = true;
	// Streamer-Modus (Namen verstecken)
	public boolean streamerModeEnabled = false;
	public boolean streamerModeHideOwn = true;
	public String streamerModeOwnName = "Streamer";
	public boolean streamerModeHideOthers = true;
	public String streamerModeOthersName = "Spieler";
	public boolean streamerModeNumbered = true;
	public List<String> streamerModeExtraNames = new ArrayList<>();
	public boolean streamerModeHideSkins = true;
	public boolean streamerModeHideOwnSkin = false;
	// Streamer-Tools (Spenden-Auktion, Giveaway)
	public int streamerAuctionSeconds = 120;
	public long streamerStartBid = 10_000;
	public long streamerMinStep = 5_000;
	public boolean streamerSumPayments = true;
	public boolean streamerExtendLateBids = true;
	public int streamerExtendSeconds = 15;
	public boolean streamerAnnounce = false;
	public boolean streamerSounds = true;
	/** Learned from a real message when the built-in patterns don't fit the server's wording; regex or "". */
	public String streamerPaymentPattern = "";
	public String streamerWhisperPattern = "";
	public boolean streamerIngameJoin = true;
	public String streamerIngameJoinWord = "join";
	public boolean streamerTwitchJoin = true;
	public String streamerTwitchChannel = "";
	public String streamerTwitchJoinWord = "!join";
	public boolean streamerTwitchSubsOnly = false;
	public int streamerWheelSeconds = 7;
}
