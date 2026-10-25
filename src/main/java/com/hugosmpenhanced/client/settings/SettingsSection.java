package com.hugosmpenhanced.client.settings;

import java.util.List;

/** A titled box of rows. */
public record SettingsSection(String title, List<SettingsOption> options) {
	public SettingsSection(String title, SettingsOption... options) {
		this(title, List.of(options));
	}
}
