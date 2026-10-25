package com.hugosmpenhanced.loader;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		try {
			Class<?> type = Class.forName(Line.className("com.hugosmpenhanced.client.ModMenuIntegration"));
			return ((ModMenuApi) type.getDeclaredConstructor().newInstance()).getModConfigScreenFactory();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("HugoSMP Enhanced settings could not open", e);
		}
	}
}
