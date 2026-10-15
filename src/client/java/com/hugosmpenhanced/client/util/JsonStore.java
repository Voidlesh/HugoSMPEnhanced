package com.hugosmpenhanced.client.util;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/**
 * A small JSON file in config/hugosmpenhanced/ for data that is not a setting (payment log, money
 * history, spawners). Writes go through a temporary file off the render thread, so a crash never
 * leaves half a file behind.
 */
public final class JsonStore<T> {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final Path path;
	private final Type type;
	private final Supplier<T> empty;
	private long savedAt;
	private final AtomicReference<String> pending = new AtomicReference<>();

	public JsonStore(String fileName, Type type, Supplier<T> empty) {
		this.path = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced").resolve(fileName);
		this.type = type;
		this.empty = empty;
	}

	public T load() {
		try {
			if (Files.exists(this.path)) {
				T value = GSON.fromJson(Files.readString(this.path, StandardCharsets.UTF_8), this.type);
				if (value != null) {
					return value;
				}
			}
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Could not read {}, starting empty", this.path.getFileName(), e);
		}
		return this.empty.get();
	}

	/** Saves a snapshot in the background. Pass a copy if the data keeps changing. */
	public void save(T value) {
		this.pending.set(GSON.toJson(value, this.type));
		this.savedAt = Util.getMillis();
		// Whichever task runs takes the newest text, so an older save never overwrites a newer one.
		Util.ioPool().execute(() -> {
			synchronized (this) {
				String json = this.pending.getAndSet(null);
				if (json != null) {
					this.write(json);
				}
			}
		});
	}

	/** Saves right away on this thread; for shutdown. */
	public void saveNow(T value) {
		this.savedAt = Util.getMillis();
		synchronized (this) {
			this.pending.set(null);
			this.write(GSON.toJson(value, this.type));
		}
	}

	/** Saves at most every {@code everyMs}; for data that changes all the time. */
	public void saveThrottled(T value, long everyMs) {
		if (Util.getMillis() - this.savedAt >= everyMs) {
			this.save(value);
		}
	}

	private synchronized void write(String json) {
		try {
			Files.createDirectories(this.path.getParent());
			Path temp = this.path.resolveSibling(this.path.getFileName() + ".tmp");
			Files.writeString(temp, json, StandardCharsets.UTF_8);
			Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			try {
				Files.writeString(this.path, json, StandardCharsets.UTF_8);
			} catch (Exception again) {
				HugoSmpEnhancedClient.LOGGER.warn("Could not save {}", this.path.getFileName(), again);
			}
		}
	}
}
