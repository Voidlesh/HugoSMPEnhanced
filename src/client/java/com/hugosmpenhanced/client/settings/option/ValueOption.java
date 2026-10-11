package com.hugosmpenhanced.client.settings.option;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.SettingsOption;

/** A row bound to one field of {@link ModConfig}, with its default taken from a fresh config. */
public abstract class ValueOption<T> extends SettingsOption {
	private static final ModConfig DEFAULTS = new ModConfig();

	private final Function<ModConfig, T> getter;
	private final BiConsumer<ModConfig, T> setter;

	protected ValueOption(String label, String description, Function<ModConfig, T> getter, BiConsumer<ModConfig, T> setter) {
		super(label, description);
		this.getter = getter;
		this.setter = setter;
	}

	protected T get() {
		return this.getter.apply(ModConfigManager.CONFIG);
	}

	protected void set(T value) {
		this.setter.accept(ModConfigManager.CONFIG, value);
	}

	protected T defaultValue() {
		return this.getter.apply(DEFAULTS);
	}

	@Override
	public boolean hasDefault() {
		return true;
	}

	@Override
	public boolean isDefault() {
		return Objects.equals(this.get(), this.defaultValue());
	}

	@Override
	public void reset() {
		this.set(this.copy(this.defaultValue()));
	}

	/** Lists are copied so editing the live config never touches the defaults. */
	protected T copy(T value) {
		return value;
	}
}
