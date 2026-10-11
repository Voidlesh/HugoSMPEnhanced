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
import com.hugosmpenhanced.client.feature.auction.AuctionSearchLanguage;
import com.hugosmpenhanced.client.feature.entityheatmap.EntityHeatmap;
import com.hugosmpenhanced.client.feature.gallery.GalleryFeature;
import com.hugosmpenhanced.client.feature.gallery.GalleryScreen;
import com.hugosmpenhanced.client.feature.inventorylayout.InventoryLayout;
import com.hugosmpenhanced.client.feature.keyboard.KeyboardScreen;
import com.hugosmpenhanced.client.feature.mainmenu.MainMenuLayout;
import com.hugosmpenhanced.client.feature.mainmenu.SplashAnimation;
import com.hugosmpenhanced.client.feature.mainmenu.SplashColor;
import com.hugosmpenhanced.client.feature.notes.NotesFeature;
import com.hugosmpenhanced.client.feature.notes.NotesHudPositionScreen;
import com.hugosmpenhanced.client.feature.notes.NotesScreen;
import com.hugosmpenhanced.client.feature.tpa.TpaKeybinds;
import com.hugosmpenhanced.client.settings.option.ButtonOption;
import com.hugosmpenhanced.client.settings.option.CycleOption;
import com.hugosmpenhanced.client.settings.option.InfoOption;
import com.hugosmpenhanced.client.settings.option.KeyBindOption;
import com.hugosmpenhanced.client.settings.option.PillsOption;
import com.hugosmpenhanced.client.settings.option.SliderOption;
import com.hugosmpenhanced.client.settings.option.StringListOption;
import com.hugosmpenhanced.client.settings.option.ToggleOption;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
			categories = List.of(general(), chat(), players(), inventory(), tools());
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

	/** Puts every option back to its default; notes, buttons, layouts and AH history stay. */
	public static void resetAll() {
		for (SettingsOption option : allOptions()) {
			if (option.hasDefault() && !option.isDefault()) {
				option.reset();
			}
		}
		ModConfigManager.save();
	}

	// ------------------------------------------------------------------ categories

	private static SettingsCategory general() {
		return new SettingsCategory("Allgemein", "general", "Menü, Taste und Titelbildschirm",
			new SettingsTab("Menü",
				new SettingsSection("Menü",
					new KeyBindOption("Menü öffnen", "Taste für dieses Menü, überall im Spiel", () -> SettingsFeature.openKey),
					toggle("Knopf im Pausemenü", "Logo-Knopf neben Kamera und Notizblock",
						c -> c.pauseMenuSettingsButton, (c, v) -> c.pauseMenuSettingsButton = v)),
				new SettingsSection("Daten",
					ButtonOption.action("Konfig-Ordner", "config/hugosmpenhanced.json", "Öffnen",
						() -> Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir())),
					ButtonOption.danger("Alles zurücksetzen",
						"Setzt alle Einstellungen und Tasten zurück. Notizen, Buttons, Layouts und Suchverlauf bleiben.",
						"Zurücksetzen", ModSettings::resetAll)),
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
						"Keine, dann ohne Splash-Text", c -> c.splashTexts, (c, v) -> c.splashTexts = v))));
	}

	private static SettingsCategory chat() {
		return new SettingsCategory("Chat", "chat", "Erwähnungen, Filter, Rechner und TPA",
			new SettingsTab("Erwähnungen",
				new SettingsSection("Erwähnungen",
					toggle("Erwähnungen hervorheben", "Nachrichten mit deinem Namen werden markiert",
						c -> c.mentionsEnabled, (c, v) -> c.mentionsEnabled = v).keywords("mention"),
					toggle("Ton bei Erwähnung", null, c -> c.mentionsSoundEnabled, (c, v) -> c.mentionsSoundEnabled = v),
					new StringListOption("Zusätzliche Wörter", "Dein Spielername wird immer erkannt", "Keine",
						c -> c.mentionTriggers, (c, v) -> c.mentionTriggers = v).keywords("trigger"))),
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

	private static SettingsCategory inventory() {
		return new SettingsCategory("Inventar & Handel", "inventory", "Inventar-Buttons, Layouts und Auktionshaus",
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
					}))));
	}

	private static SettingsCategory tools() {
		return new SettingsCategory("Werkzeuge", "tools", "Notizblock, Galerie, Tastatur und Farm-Heatmap",
			new SettingsTab("Notizen",
				new SettingsSection("Notizblock",
					ButtonOption.page("Notizen öffnen", null, "Öffnen", () -> open(NotesScreen::new)),
					new KeyBindOption("Taste", null, () -> NotesFeature.openKey).keywords("notizblock"),
					ButtonOption.page("Angeheftete Notizen", "Position auf dem Bildschirm", "Verschieben",
						() -> open(NotesHudPositionScreen::new)))),
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
						c -> c.entityHeatmapMinCount, (c, v) -> c.entityHeatmapMinCount = v))));
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
