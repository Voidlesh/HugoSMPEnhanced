package com.hugosmpenhanced.client.config;

import java.util.ArrayList;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
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
						.group(tabListGroup(config))
						.group(chatGroup(config))
						.group(chatFilterGroup(config))
						.group(mainMenuGroup(config))
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
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Eigenen Namen oben anzeigen"))
						.binding(false, () -> config.tabListSelfOnTopEnabled, v -> config.tabListSelfOnTopEnabled = v)
						.controller(BooleanControllerBuilder::create)
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
				.build();
	}
}
