package com.hugosmpenhanced.client.feature.secondnametag;

public enum SecondNametagPosition {
	ABOVE("Über dem Namen"),
	BELOW("Unter dem Namen");

	private final String displayName;

	SecondNametagPosition(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}
}
