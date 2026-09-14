package com.hugosmpenhanced.client.config;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
	// Mention-Highlighting
	public boolean mentionsEnabled = true;
	public boolean mentionsSoundEnabled = true;
	public List<String> mentionTriggers = new ArrayList<>();

	// Preis pro Item
	public boolean pricePerItemEnabled = true;

	// TPA-Toast
	public boolean tpaToastEnabled = true;

	// Chat beim Weltwechsel erhalten
	public boolean persistentChatEnabled = true;

	// Hauptmenü-Button zum direkten Beitreten
	public boolean mainMenuButtonEnabled = true;

	// Chat-Filter
	public boolean publicChatEnabled = true;
	public boolean systemChatEnabled = true;
	public boolean msgChatEnabled = true;
	public boolean clanChatEnabled = true;
	public boolean friendChatEnabled = true;

	// Tabliste
	public boolean tabListCompactEnabled = false;
	public double tabListScale = 0.75;
}
