package com.hugosmpenhanced.client.feature.auction;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/**
 * HugoSMP's own items are vanilla items with a name, lore and a model from the server's resource
 * pack. Every such item seen in the auction house, in orders or in the own inventory is remembered
 * here (name, colour and a copy of the item for its icon), so the auction search can suggest it
 * by name and draw it with its real model.
 * <p>
 * An item counts as custom when it carries a plugin item id in its custom data, an own item model
 * or custom model data, or (only for items that are offered for a price) a coloured name the server
 * gave it. Plain names from an anvil are not custom items.
 */
public final class CustomItems {
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced-items.json");
	private static final Gson GSON = new Gson();
	private static final Type TYPE = new TypeToken<LinkedHashMap<String, Known>>() { }.getType();
	/** Keys plugins use for their item id: "id", "item_id", "hugosmp:item-id", "MMOITEMS_ITEM_ID", "custom_item"... */
	private static final Pattern ID_KEY = Pattern.compile("^(custom[_-]?)?(item[_-]?)?id$|^custom[_-]?item$|^item[_-]?type$"
		+ "|^mmoitems_item_id$|^item[_-]?key$");
	private static final Pattern UUID_LIKE = Pattern.compile("^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$|^\\d+$",
		Pattern.CASE_INSENSITIVE);
	private static final int MAX_KNOWN = 2000;

	private static Map<String, Known> known;
	private static boolean dirty;

	/** One remembered item. The stack is stored as JSON so its model and colours survive a restart. */
	public static final class Known {
		public String id;
		public String name;
		public int color;
		public String item;
		public String stack;
		public boolean limited;
		public long firstSeen;
		public long lastSeen;
		transient ItemStack icon;
	}

	private CustomItems() {
	}

	/** Stable id of a custom item, or null. Name-only items count only when {@code offered} (they have a price). */
	public static String idOf(ItemStack stack, boolean offered) {
		if (stack.isEmpty()) {
			return null;
		}
		String dataId = dataId(stack.get(DataComponents.CUSTOM_DATA));
		if (dataId != null) {
			return "data:" + dataId;
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		Identifier usual = stack.getPrototype().get(DataComponents.ITEM_MODEL);
		if (model != null && !model.equals(usual)) {
			return "model:" + model;
		}
		CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (modelData != null && !modelData.equals(CustomModelData.EMPTY)) {
			return "cmd:" + itemId(stack) + ":" + modelData.floats() + modelData.strings() + modelData.flags();
		}
		if (offered && hasServerName(stack)) {
			return "name:" + itemId(stack) + ":" + AuctionSearch.fold(stack.getHoverName().getString().strip());
		}
		return null;
	}

	/** A name the server set: an item name, or a custom name with colour or formatting (an anvil name has neither). */
	static boolean hasServerName(ItemStack stack) {
		Component itemName = stack.get(DataComponents.ITEM_NAME);
		if (itemName != null && !itemName.equals(stack.getPrototype().get(DataComponents.ITEM_NAME))) {
			return true;
		}
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		return custom != null && isStyled(custom);
	}

	private static boolean isStyled(Component component) {
		Style style = component.getStyle();
		if (style.getColor() != null || style.isBold() || style.isUnderlined() || style.isObfuscated() || style.isStrikethrough()) {
			return true;
		}
		for (Component sibling : component.getSiblings()) {
			if (isStyled(sibling)) {
				return true;
			}
		}
		// Legacy section codes inside a plain literal.
		return component.getString().contains("§");
	}

	static String dataId(CustomData data) {
		if (data == null || data.isEmpty()) {
			return null;
		}
		return findId(data.copyTag(), "", 0);
	}

	private static String findId(CompoundTag tag, String path, int depth) {
		if (depth > 3) {
			return null;
		}
		for (String key : tag.keySet()) {
			Tag value = tag.get(key);
			String local = key.substring(key.indexOf(':') + 1).toLowerCase(Locale.ROOT);
			if (ID_KEY.matcher(local).matches()) {
				Optional<String> text = value.asString();
				if (text.isPresent()) {
					String id = text.get().strip();
					if (id.length() >= 2 && id.length() <= 64 && !UUID_LIKE.matcher(id).matches()) {
						return (path + key + "=" + id).toLowerCase(Locale.ROOT);
					}
				}
			}
		}
		for (String key : tag.keySet()) {
			Optional<CompoundTag> nested = tag.get(key).asCompound();
			if (nested.isPresent()) {
				String id = findId(nested.get(), path + key + "/", depth + 1);
				if (id != null) {
					return id;
				}
			}
		}
		return null;
	}

	private static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** Remembers a custom item. Returns its id, or null if the stack is not one. */
	public static String learn(ItemStack stack, boolean offered) {
		if (!ModConfigManager.CONFIG.customItemsEnabled) {
			return null;
		}
		String id = idOf(stack, offered);
		if (id == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		Known entry = all().get(id);
		boolean limited = LimitedItems.isLimited(stack, lifetimeMs());
		String name = stack.getHoverName().getString().strip();
		if (entry != null && entry.name.equals(name) && entry.limited == limited) {
			if (now - entry.lastSeen > 60_000) {
				entry.lastSeen = now;
				dirty = true;
			}
			return id;
		}
		if (name.isEmpty()) {
			return null;
		}
		if (entry == null) {
			if (all().size() >= MAX_KNOWN) {
				dropOldest();
			}
			entry = new Known();
			entry.id = id;
			entry.firstSeen = now;
			all().put(id, entry);
		}
		entry.name = name;
		entry.color = colorOf(stack.getHoverName());
		entry.item = itemId(stack);
		entry.limited = entry.limited || limited;
		entry.lastSeen = now;
		entry.stack = encode(stack);
		entry.icon = null;
		dirty = true;
		AuctionSearch.invalidate();
		return id;
	}

	private static void dropOldest() {
		all().values().stream().min((a, b) -> Long.compare(a.lastSeen, b.lastSeen))
			.ifPresent(oldest -> all().remove(oldest.id));
	}

	/** The first colour in the name, so the search can show it like the game does. 0 if none. */
	static int colorOf(Component name) {
		TextColor color = name.getStyle().getColor();
		if (color != null) {
			return 0xFF000000 | color.getValue();
		}
		for (Component sibling : name.getSiblings()) {
			int found = colorOf(sibling);
			if (found != 0) {
				return found;
			}
		}
		return 0;
	}

	public static Collection<Known> known() {
		return all().values();
	}

	public static int count() {
		return all().size();
	}

	public static Known get(String id) {
		return id == null ? null : all().get(id);
	}

	/** The item to draw for a remembered entry, with its own model; a plain item if it cannot be read back. */
	public static ItemStack icon(Known entry) {
		if (entry.icon != null) {
			return entry.icon;
		}
		ItemStack icon = decode(entry);
		// Without a world the components cannot be read yet; try again later then.
		if (Minecraft.getInstance().level != null) {
			entry.icon = icon;
		}
		return icon;
	}

	public static long lifetimeMs() {
		return Math.max(1, ModConfigManager.CONFIG.limitedItemsLifetimeHours) * 3_600_000L;
	}

	private static DynamicOps<JsonElement> ops() {
		Minecraft client = Minecraft.getInstance();
		return client.level == null ? null : client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	private static String encode(ItemStack stack) {
		DynamicOps<JsonElement> ops = ops();
		if (ops == null) {
			return null;
		}
		// Only what changes the look: lore and plugin data would just make the file big.
		ItemStack copy = stack.copyWithCount(1);
		copy.remove(DataComponents.LORE);
		copy.remove(DataComponents.CUSTOM_DATA);
		return ItemStack.CODEC.encodeStart(ops, copy).result().map(JsonElement::toString).orElse(null);
	}

	private static ItemStack decode(Known entry) {
		DynamicOps<JsonElement> ops = ops();
		if (ops != null && entry.stack != null) {
			try {
				Optional<ItemStack> stack = ItemStack.CODEC.parse(ops, JsonParser.parseString(entry.stack)).result();
				if (stack.isPresent()) {
					return stack.get();
				}
			} catch (RuntimeException ignored) {
				// fall back to the plain item
			}
		}
		Identifier id = entry.item == null ? null : Identifier.tryParse(entry.item);
		return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}

	/** Called on disconnect: icons are decoded with the server's registries. */
	static void forgetIcons() {
		if (known != null) {
			known.values().forEach(entry -> entry.icon = null);
		}
	}

	private static Map<String, Known> all() {
		if (known == null) {
			known = new LinkedHashMap<>();
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					Map<String, Known> loaded = GSON.fromJson(reader, TYPE);
					if (loaded != null) {
						loaded.values().removeIf(entry -> entry == null || entry.id == null || entry.name == null);
						known.putAll(loaded);
					}
				} catch (IOException | RuntimeException ignored) {
					// start empty
				}
			}
		}
		return known;
	}

	public static void saveIfDirty() {
		if (!dirty || known == null) {
			return;
		}
		dirty = false;
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(known, TYPE, writer);
			}
		} catch (IOException ignored) {
			// keep them in memory
		}
	}

	public static void clear() {
		all().clear();
		dirty = true;
		saveIfDirty();
		AuctionSearch.invalidate();
	}

	/** For tests: all remembered names. */
	public static List<String> names() {
		List<String> names = new ArrayList<>();
		all().values().forEach(entry -> names.add(entry.name));
		return names;
	}
}
