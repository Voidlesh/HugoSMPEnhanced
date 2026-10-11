package com.hugosmpenhanced.client.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.hugosmpenhanced.client.feature.auction.AuctionSearchLanguage;
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

	// TPA-Toast
	public boolean tpaToastEnabled = true;

	// Chat beim Weltwechsel erhalten
	public boolean persistentChatEnabled = true;

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
}
