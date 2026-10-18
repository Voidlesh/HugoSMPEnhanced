package com.hugosmpenhanced.client.feature.notes;

/** One note in the notepad; pinned notes are also shown on the HUD. Saved with the config. */
public class Note {
	public String title = "";
	public String text = "";
	public boolean pinned;

	public String displayTitle() {
		return this.title.isBlank() ? "Notiz" : this.title;
	}
}
