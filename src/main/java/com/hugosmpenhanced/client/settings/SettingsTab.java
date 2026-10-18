package com.hugosmpenhanced.client.settings;

import java.util.List;

/** A tab at the top of a category, holding one or more sections. */
public record SettingsTab(String name, List<SettingsSection> sections) {
	public SettingsTab(String name, SettingsSection... sections) {
		this(name, List.of(sections));
	}
}
