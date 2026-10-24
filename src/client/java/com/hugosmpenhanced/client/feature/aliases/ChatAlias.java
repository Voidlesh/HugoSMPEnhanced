package com.hugosmpenhanced.client.feature.aliases;

/** A short name that stands for a whole chat message. Saved with the config. */
public class ChatAlias {
	public String name = "";
	public String text = "";

	public ChatAlias() {
	}

	public ChatAlias(String name, String text) {
		this.name = name;
		this.text = text;
	}
}
