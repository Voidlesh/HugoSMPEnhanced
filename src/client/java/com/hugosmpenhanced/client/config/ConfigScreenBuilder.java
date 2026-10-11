package com.hugosmpenhanced.client.config;

import java.util.ArrayList;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import com.hugosmpenhanced.client.feature.auction.AuctionSearchLanguage;
import com.hugosmpenhanced.client.feature.mainmenu.MainMenuLayout;
import com.hugosmpenhanced.client.feature.mainmenu.SplashAnimation;
import com.hugosmpenhanced.client.feature.mainmenu.SplashColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigScreenBuilder {
	private ConfigScreenBuilder() {
	}

	public static Screen build(Screen parent) {
		ModConfig config = ModConfigManager.CONFIG;

		return YetAnotherConfigLib.createBuilder()
				.title(Component.literal("HugoSMP Enhanced"))
				.category(ConfigCategory.createBuilder()
						.name(Component.literal("Allgemein"))
						.group(mentionsGroup(config))
						.group(mentionTriggersGroup(config))
						.group(priceGroup(config))
						.group(tpaGroup(config))
						.group(hugoGuardGroup(config))
						.group(tabListGroup(config))
						.group(nametagStyleGroup())
						.group(secondNametagGroup())
						.group(inventoryButtonsGroup(config))
						.group(chatGroup(config))
						.group(chatFilterGroup(config))
						.group(mainMenuGroup(config))
						.group(splashTextsGroup(config))
						.build())
				.save(ModConfigManager::save)
				.build()
				.generateScreen(parent);
	}

	private static OptionGroup mentionsGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Mention-Highlighting"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Aktiviert"))
						.binding(true, () -> config.mentionsEnabled, v -> config.mentionsEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Sound bei Erwähnung"))
						.binding(true, () -> config.mentionsSoundEnabled, v -> config.mentionsSoundEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.build();
	}

	private static ListOption<String> mentionTriggersGroup(ModConfig config) {
		return ListOption.<String>createBuilder()
				.name(Component.literal("Zusätzliche Trigger-Wörter"))
				.description(OptionDescription.of(Component.literal(
						"Dein Spielername wird immer automatisch erkannt, unabhängig von dieser Liste.")))
				.initial("")
				.controller(StringControllerBuilder::create)
				.binding(new ArrayList<>(config.mentionTriggers), () -> config.mentionTriggers,
						v -> config.mentionTriggers = v)
				.build();
	}

	private static OptionGroup priceGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Auktionshaus"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Preis pro Item anzeigen"))
						.binding(true, () -> config.pricePerItemEnabled, v -> config.pricePerItemEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Eigenes Suchmenü statt Schild"))
						.description(OptionDescription.of(Component.literal(
								"Ersetzt das Such-Schild im AH und bei Orders durch ein Suchfenster mit Item-Vorschlägen, letzten Suchen und Favoriten.")))
						.binding(true, () -> config.auctionSearchEnabled, v -> config.auctionSearchEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<AuctionSearchLanguage>createBuilder()
						.name(Component.literal("Vorschläge senden Namen in"))
						.description(OptionDescription.of(Component.literal(
								"Welcher Itemname beim Klick auf einen Vorschlag an die AH-Suche geht. Selbst Getipptes wird immer genau so gesendet.")))
						.binding(AuctionSearchLanguage.GAME, () -> config.auctionSearchLanguage, v -> config.auctionSearchLanguage = v)
						.controller(opt -> EnumControllerBuilder.create(opt).enumClass(AuctionSearchLanguage.class))
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Gesehene Preise merken"))
						.description(OptionDescription.of(Component.literal(
								"Merkt sich Preise, über die du im AH oder bei Orders mit der Maus fährst. Die Suche zeigt im AH den niedrigsten, bei Orders den höchsten der letzten Tage.")))
						.binding(true, () -> config.auctionPriceMemoryEnabled, v -> config.auctionPriceMemoryEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.build();
	}

	private static OptionGroup hugoGuardGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("HugoGuard"))
				.description(OptionDescription.of(Component.literal(
						"Markiert Spieler, die auf hugoguard.net als Scammer oder vertrauenswürdig gelistet sind. Die Listen werden alle 10 Minuten neu geladen.")))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Aktiviert"))
						.binding(true, () -> config.hugoGuardEnabled, v -> config.hugoGuardEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Trusted-Spieler markieren"))
						.binding(true, () -> config.hugoGuardTrusted, v -> config.hugoGuardTrusted = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Leuchten (nur in Sicht)"))
						.description(OptionDescription.of(Component.literal(
								"Roter bzw. grüner Schimmer um den Spieler. Wird von Wänden verdeckt, ist also kein Durch-die-Wand-Sehen.")))
						.binding(true, () -> config.hugoGuardGlow, v -> config.hugoGuardGlow = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Über dem Kopf"))
						.binding(true, () -> config.hugoGuardOverhead, v -> config.hugoGuardOverhead = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Im Chat"))
						.binding(true, () -> config.hugoGuardChat, v -> config.hugoGuardChat = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Bei TPA-Anfragen"))
						.binding(true, () -> config.hugoGuardTpa, v -> config.hugoGuardTpa = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.build();
	}

	private static OptionGroup tpaGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("TPA"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("TPA-Toast anzeigen"))
						.description(OptionDescription.of(Component.literal(
								"Tastenkombination zum Annehmen wird im Steuerung-Menü unter \"HugoSMP Enhanced\" konfiguriert.")))
						.binding(true, () -> config.tpaToastEnabled, v -> config.tpaToastEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.build();
	}

	private static OptionGroup tabListGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Tabliste"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Kompakt anzeigen"))
						.binding(false, () -> config.tabListCompactEnabled, v -> config.tabListCompactEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Double>createBuilder()
						.name(Component.literal("Skalierung"))
						.binding(0.75, () -> config.tabListScale, v -> config.tabListScale = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt)
								.range(0.5, 1.0)
								.step(0.05)
								.valueFormatter(v -> Component.literal(Math.round(v * 100) + "%")))
						.build())
				.build();
	}

	private static OptionGroup nametagStyleGroup() {
		return OptionGroup.createBuilder()
				.name(Component.literal("Namens-Stil"))
				.description(OptionDescription.of(Component.literal(
						"Nur für dich sichtbar: Effekte für deinen eigenen Namen in Tabliste, Nametag, Scoreboard und Chat.")))
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Nametag-Stil bearbeiten"))
						.text(Component.literal("Öffnen"))
						.action((screen, option) -> Minecraft.getInstance().setScreen(NametagStyleScreen.build(screen)))
						.build())
				.build();
	}

	private static OptionGroup secondNametagGroup() {
		return OptionGroup.createBuilder()
				.name(Component.literal("Zweiter Nametag"))
				.description(OptionDescription.of(Component.literal(
						"Nur für dich sichtbar: eine zusätzliche Zeile über oder unter deinem Nametag in der Third-Person-Ansicht (F5).")))
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Zweiten Nametag bearbeiten"))
						.text(Component.literal("Öffnen"))
						.action((screen, option) -> Minecraft.getInstance().setScreen(SecondNametagScreen.build(screen)))
						.build())
				.build();
	}

	private static OptionGroup inventoryButtonsGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Inventar-Buttons"))
				.description(OptionDescription.of(Component.literal(
						"Buttons rechts neben und über deinem Inventar, die einen Command ausführen oder eine Chatnachricht senden.")))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Im Inventar anzeigen"))
						.binding(true, () -> config.inventoryButtonsEnabled, v -> config.inventoryButtonsEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(ButtonOption.createBuilder()
						.name(Component.literal("Inventar-Buttons bearbeiten"))
						.text(Component.literal("Öffnen"))
						.action((screen, option) -> Minecraft.getInstance().setScreen(new InventoryButtonsScreen(screen)))
						.build())
				.build();
	}

	private static OptionGroup chatGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Chat"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Chat beim Weltwechsel erhalten"))
						.binding(true, () -> config.persistentChatEnabled, v -> config.persistentChatEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Chat-Rechner"))
						.description(OptionDescription.of(Component.literal(
								"Zeigt beim Tippen das Ergebnis einer Rechnung am Ende der Eingabe, z. B. 64*3,5 oder 1,5k+200. Tab setzt das Ergebnis ein.")))
						.binding(true, () -> config.chatCalculatorEnabled, v -> config.chatCalculatorEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.build();
	}

	private static OptionGroup chatFilterGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Chat-Filter"))
				.description(OptionDescription.of(Component.literal(
						"Eigene Nachrichten werden nie ausgeblendet, unabhängig von diesen Einstellungen.")))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Öffentlicher Chat"))
						.binding(true, () -> config.publicChatEnabled, v -> config.publicChatEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Systemnachrichten"))
						.binding(true, () -> config.systemChatEnabled, v -> config.systemChatEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Private Nachrichten"))
						.binding(true, () -> config.msgChatEnabled, v -> config.msgChatEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Clan-Nachrichten"))
						.binding(true, () -> config.clanChatEnabled, v -> config.clanChatEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Freundes-Nachrichten"))
						.binding(true, () -> config.friendChatEnabled, v -> config.friendChatEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.build();
	}

	private static OptionGroup mainMenuGroup(ModConfig config) {
		return OptionGroup.createBuilder()
				.name(Component.literal("Hauptmenü"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("\"HugoSMP.net beitreten\"-Button anzeigen"))
						.binding(true, () -> config.mainMenuButtonEnabled, v -> config.mainMenuButtonEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Eigenes Hauptmenü"))
						.description(OptionDescription.of(Component.literal(
								"Ersetzt das Minecraft-Hauptmenü durch das HugoSMP-Menü mit Logo und eigenem Splash-Text.")))
						.binding(true, () -> config.mainMenuCustom, v -> config.mainMenuCustom = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<MainMenuLayout>createBuilder()
						.name(Component.literal("Layout"))
						.description(OptionDescription.of(Component.literal(
								"Lässt sich auch direkt im Hauptmenü oben rechts umschalten.")))
						.binding(MainMenuLayout.CLASSIC, () -> config.mainMenuLayout, v -> config.mainMenuLayout = v)
						.controller(opt -> EnumControllerBuilder.create(opt).enumClass(MainMenuLayout.class))
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Server-Karte (Seitenleiste)"))
						.description(OptionDescription.of(Component.literal(
								"Zeigt im Seitenleisten-Layout, ob HugoSMP.net online ist und wie viele Spieler drauf sind.")))
						.binding(true, () -> config.mainMenuServerCard, v -> config.mainMenuServerCard = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<SplashAnimation>createBuilder()
						.name(Component.literal("Splash-Animation"))
						.binding(SplashAnimation.PULSE, () -> config.splashAnimation, v -> config.splashAnimation = v)
						.controller(opt -> EnumControllerBuilder.create(opt).enumClass(SplashAnimation.class))
						.build())
				.option(Option.<SplashColor>createBuilder()
						.name(Component.literal("Splash-Farbe"))
						.binding(SplashColor.YELLOW, () -> config.splashColor, v -> config.splashColor = v)
						.controller(opt -> EnumControllerBuilder.create(opt).enumClass(SplashColor.class))
						.build())
				.build();
	}

	private static ListOption<String> splashTextsGroup(ModConfig config) {
		return ListOption.<String>createBuilder()
				.name(Component.literal("Splash-Texte"))
				.description(OptionDescription.of(Component.literal(
						"Bei jedem Öffnen des Hauptmenüs wird zufällig einer dieser Texte angezeigt. Leere Liste = kein Splash-Text.")))
				.initial("")
				.controller(StringControllerBuilder::create)
				.binding(new ModConfig().splashTexts, () -> config.splashTexts, v -> config.splashTexts = v)
				.build();
	}
}
