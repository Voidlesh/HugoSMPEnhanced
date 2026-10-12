package com.hugosmpenhanced.client.feature.colorcodes;

/** A saved text with colour codes. Its look can also be put on any other text ("Stil"). */
public class ColorTemplate {
	public String name = "";
	public String text = "";

	public ColorTemplate() {
	}

	public ColorTemplate(String name, String text) {
		this.name = name;
		this.text = text;
	}
}
