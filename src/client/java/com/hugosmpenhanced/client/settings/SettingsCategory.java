package com.hugosmpenhanced.client.settings;

import java.util.List;

/** One entry of the sidebar: an icon, a name and its tabs. */
public record SettingsCategory(String name, String icon, String description, List<SettingsTab> tabs) {
	public SettingsCategory(String name, String icon, String description, SettingsTab... tabs) {
		this(name, icon, description, List.of(tabs));
	}
}
