package com.hugosmpenhanced.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public final class ModConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced.json");

	public static ModConfig CONFIG = load();

	private ModConfigManager() {
	}

	private static ModConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				ModConfig config = GSON.fromJson(reader, ModConfig.class);
				if (config != null) {
					return config;
				}
			} catch (IOException ignored) {
				// fall through to defaults below
			}
		}

		ModConfig config = new ModConfig();
		save(config);
		return config;
	}

	public static void save() {
		save(CONFIG);
	}

	private static void save(ModConfig config) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException ignored) {
			// keep working with the in-memory config if saving fails
		}
	}
}
