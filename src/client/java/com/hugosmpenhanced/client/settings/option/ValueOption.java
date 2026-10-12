package com.hugosmpenhanced.client.settings.option;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.settings.SettingsOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * A row bound to one value: usually a field of {@link ModConfig} with its default taken from a
 * fresh config, or one of Minecraft's own options (see {@link #vanilla}).
 */
public abstract class ValueOption<T> extends SettingsOption {
	private static final ModConfig DEFAULTS = new ModConfig();

	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final Supplier<T> defaults;
	private final Runnable save;
	private final boolean minecraft;

	protected ValueOption(String label, String description, Function<ModConfig, T> getter, BiConsumer<ModConfig, T> setter) {
		this(label, description, new Binding<>(() -> getter.apply(ModConfigManager.CONFIG),
			value -> setter.accept(ModConfigManager.CONFIG, value), () -> getter.apply(DEFAULTS), ModConfigManager::save, false));
	}

	protected ValueOption(String label, String description, Binding<T> binding) {
		super(label, description);
		this.getter = binding.getter();
		this.setter = binding.setter();
		this.defaults = binding.defaults();
		this.save = binding.save();
		this.minecraft = binding.minecraft();
	}

	/** Where a row reads, writes and saves its value, and what it resets to. */
	public record Binding<T>(Supplier<T> getter, Consumer<T> setter, Supplier<T> defaults, Runnable save, boolean minecraft) {
	}

	/** Binds to one of Minecraft's options; it is saved to options.txt like in the video settings. */
	public static <T> Binding<T> vanilla(Function<net.minecraft.client.Options, OptionInstance<T>> option, T defaultValue) {
		return new Binding<>(() -> option.apply(Minecraft.getInstance().options).get(),
			value -> option.apply(Minecraft.getInstance().options).set(value), () -> defaultValue,
			() -> Minecraft.getInstance().options.save(), true);
	}

	protected T get() {
		return this.getter.get();
	}

	protected void set(T value) {
		this.setter.accept(value);
	}

	protected T defaultValue() {
		return this.defaults.get();
	}

	/** Writes the value to disk; rows call this instead of saving the mod config directly. */
	protected void save() {
		this.save.run();
	}

	@Override
	protected void saveChange() {
		this.save();
	}

	@Override
	public boolean minecraftOption() {
		return this.minecraft;
	}

	@Override
	public boolean hasDefault() {
		return this.defaults != null;
	}

	@Override
	public boolean isDefault() {
		return this.defaults == null || Objects.equals(this.get(), this.defaultValue());
	}

	@Override
	public void reset() {
		if (this.defaults != null) {
			this.set(this.copy(this.defaultValue()));
		}
	}

	/** Lists are copied so editing the live config never touches the defaults. */
	protected T copy(T value) {
		return value;
	}
}
