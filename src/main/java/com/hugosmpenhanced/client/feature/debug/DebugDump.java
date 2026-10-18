package com.hugosmpenhanced.client.feature.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.feature.discord.StatusReader;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.mixin.AbstractContainerScreenAccessor;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * Copies what HugoSMP really sends (chat lines, an item's tooltip and data, a menu, the sidebar)
 * as text, so a player can report it in one go and a broken detection can be fixed in the formats
 * file. Each dump goes to the clipboard and to config/hugosmpenhanced/debug/.
 * <p>
 * Ctrl+Shift+C in any menu copies the menu and the item under the mouse; outside menus
 * {@code /hse debug} does it.
 */
public final class DebugDump {
	private static final Path FOLDER = FabricLoader.getInstance().getConfigDir().resolve("hugosmpenhanced").resolve("debug");
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int KEEP_LINES = 200;

	private record Line(LocalDateTime time, String kind, Component message) {
	}

	private static final Deque<Line> LINES = new ArrayDeque<>();
	/** The last menu seen, so /hse debug menu works after it was closed for typing the command. */
	private static String lastMenu;
	private static ItemStack lastHovered = ItemStack.EMPTY;

	private DebugDump() {
	}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> remember("Spieler-Chat", message));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> remember(overlay ? "Actionbar" : "System", message));
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> remember(overlay ? "Actionbar (ausgeblendet)" : "System (ausgeblendet)", message));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			ScreenKeyboardEvents.allowKeyPress(container).register((s, event) -> {
				if (event.key() == GLFW.GLFW_KEY_C && event.hasControlDownWithQuirk() && event.hasShiftDown()) {
					copyMenu(container);
					return false;
				}
				return true;
			});
			ScreenEvents.afterRender(container).register((s, graphics, mouseX, mouseY, delta) -> {
				Slot hovered = ((AbstractContainerScreenAccessor) container).hugosmpenhanced$getHoveredSlot();
				if (hovered != null && hovered.hasItem()) {
					lastHovered = hovered.getItem().copy();
				}
			});
			ScreenEvents.remove(container).register(s -> lastMenu = menuText(container));
		});
	}

	private static void remember(String kind, Component message) {
		synchronized (LINES) {
			LINES.addLast(new Line(LocalDateTime.now(), kind, message));
			while (LINES.size() > KEEP_LINES) {
				LINES.removeFirst();
			}
		}
	}

	// ------------------------------------------------------------------ dumps

	public static void chat(int count) {
		List<Line> lines;
		synchronized (LINES) {
			lines = new ArrayList<>(LINES);
		}
		lines = lines.subList(Math.max(0, lines.size() - count), lines.size());
		StringBuilder out = header("Chat (letzte " + lines.size() + " Zeilen)");
		for (Line line : lines) {
			out.append('[').append(line.time().format(LINE_TIME)).append("] ").append(line.kind()).append('\n');
			out.append("  Text:  ").append(HugoFormats.clean(line.message().getString())).append('\n');
			out.append("  JSON:  ").append(json(line.message())).append("\n\n");
		}
		save("chat", out, lines.size() + (lines.size() == 1 ? " Chatzeile" : " Chatzeilen"));
	}

	/** The item under the mouse in the last menu, else the item in hand. */
	public static void item() {
		Minecraft client = Minecraft.getInstance();
		ItemStack stack = !lastHovered.isEmpty() ? lastHovered : client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
		if (stack.isEmpty()) {
			feedback(Component.literal("Kein Item: halte eins in der Hand oder fahre in einem Menü darüber.").withStyle(ChatFormatting.RED));
			return;
		}
		StringBuilder out = header("Item");
		out.append(itemText(stack));
		save("item", out, "Item " + HugoFormats.clean(stack.getHoverName().getString()));
		lastHovered = ItemStack.EMPTY;
	}

	public static void menu() {
		if (lastMenu == null) {
			feedback(Component.literal("Noch kein Menü gesehen. Öffne eins und drücke dort Strg+Shift+C.").withStyle(ChatFormatting.RED));
			return;
		}
		save("menu", new StringBuilder(lastMenu), "das letzte Menü");
	}

	public static void sidebar() {
		Minecraft client = Minecraft.getInstance();
		StatusReader.Reading reading = StatusReader.read(client);
		StringBuilder out = header("Scoreboard und Tabliste");
		out.append("Scoreboard-Titel: ").append(reading.sidebarTitle()).append('\n');
		for (String line : reading.sidebar()) {
			out.append("  ").append(line).append('\n');
		}
		out.append("\nTabliste oben:\n  ").append(reading.tabHeader().replace("\n", "\n  ")).append('\n');
		out.append("Tabliste unten:\n  ").append(reading.tabFooter().replace("\n", "\n  ")).append('\n');
		out.append("\nWelt: ").append(reading.world()).append("\nErkannt: Ort ").append(reading.location())
			.append(", Geld ").append(reading.money()).append(" (").append(reading.moneySource()).append(")\n");
		save("scoreboard", out, "Scoreboard und Tabliste");
	}

	public static void all() {
		StringBuilder out = header("Alles");
		out.append(sidebarAndChat());
		if (lastMenu != null) {
			out.append("\n\n").append(lastMenu);
		}
		save("alles", out, "Scoreboard, Chat und letztes Menü");
	}

	private static String sidebarAndChat() {
		Minecraft client = Minecraft.getInstance();
		StatusReader.Reading reading = StatusReader.read(client);
		StringBuilder out = new StringBuilder("== Scoreboard ==\n").append(reading.sidebarTitle()).append('\n');
		reading.sidebar().forEach(line -> out.append("  ").append(line).append('\n'));
		out.append("\n== Chat (letzte 40) ==\n");
		List<Line> lines;
		synchronized (LINES) {
			lines = new ArrayList<>(LINES);
		}
		for (Line line : lines.subList(Math.max(0, lines.size() - 40), lines.size())) {
			out.append('[').append(line.time().format(LINE_TIME)).append("] ").append(line.kind()).append(": ")
				.append(HugoFormats.clean(line.message().getString())).append('\n');
		}
		return out.toString();
	}

	/** Ctrl+Shift+C in a menu: the menu with every slot, plus the item under the mouse. */
	public static void copyMenu(AbstractContainerScreen<?> screen) {
		StringBuilder out = header("Menü");
		out.append(menuText(screen));
		Slot hovered = ((AbstractContainerScreenAccessor) screen).hugosmpenhanced$getHoveredSlot();
		if (hovered != null && hovered.hasItem()) {
			out.append("\n== Item unter der Maus (Slot ").append(hovered.index).append(") ==\n").append(itemText(hovered.getItem()));
		}
		save("menue", out, "Menü \"" + HugoFormats.clean(screen.getTitle().getString()) + "\"");
	}

	private static String menuText(AbstractContainerScreen<?> screen) {
		StringBuilder out = new StringBuilder();
		out.append("Titel: ").append(HugoFormats.clean(screen.getTitle().getString())).append('\n');
		out.append("Titel JSON: ").append(json(screen.getTitle())).append('\n');
		out.append("Menü-Typ: ").append(screen.getMenu().getClass().getSimpleName()).append('\n');
		int container = 0;
		for (Slot slot : screen.getMenu().slots) {
			if (!(slot.container instanceof Inventory)) {
				container++;
			}
		}
		out.append("Slots: ").append(container).append(" im Menü\n\n");
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory || !slot.hasItem()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			out.append("[").append(slot.index).append("] ").append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
				.append(" x").append(stack.getCount()).append("  ").append(HugoFormats.clean(stack.getHoverName().getString())).append('\n');
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					out.append("      ").append(HugoFormats.clean(line.getString())).append('\n');
				}
			}
		}
		return out.toString();
	}

	private static String itemText(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		StringBuilder out = new StringBuilder();
		out.append("Item: ").append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(" x").append(stack.getCount()).append('\n');
		out.append("Name: ").append(HugoFormats.clean(stack.getHoverName().getString())).append('\n');
		out.append("Name JSON: ").append(json(stack.getHoverName())).append("\n\nTooltip:\n");
		if (client.level != null) {
			List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.Default.ADVANCED);
			for (Component line : tooltip) {
				out.append("  ").append(HugoFormats.clean(line.getString())).append('\n');
			}
		}
		out.append("\nDaten:\n");
		try {
			HolderLookup.Provider registries = client.level != null ? client.level.registryAccess() : null;
			JsonElement data = registries == null ? null
				: ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow();
			out.append(data == null ? stack.getComponentsPatch().toString() : GSON.toJson(data));
		} catch (Exception e) {
			out.append(stack.getComponentsPatch().toString());
		}
		return out.append('\n').toString();
	}

	private static String json(Component component) {
		Minecraft client = Minecraft.getInstance();
		try {
			if (client.level != null) {
				return ComponentSerialization.CODEC.encodeStart(client.level.registryAccess()
					.createSerializationContext(JsonOps.INSTANCE), component).getOrThrow().toString();
			}
			return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow().toString();
		} catch (Exception e) {
			return component.toString();
		}
	}

	private static StringBuilder header(String what) {
		Minecraft client = Minecraft.getInstance();
		String server = client.getCurrentServer() == null ? "kein Server" : client.getCurrentServer().ip;
		String version = FabricLoader.getInstance().getModContainer(HugoSmpEnhancedClient.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
		return new StringBuilder("HugoSMP Enhanced ").append(version).append(" · ").append(what).append(" · ")
			.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append(" · ").append(server)
			.append(" · Formate v").append(HugoFormats.version()).append("\n\n");
	}

	private static void save(String kind, CharSequence text, String what) {
		Minecraft client = Minecraft.getInstance();
		client.keyboardHandler.setClipboard(text.toString());
		Path file = FOLDER.resolve(LocalDateTime.now().format(FILE_TIME) + "-" + kind + ".txt");
		try {
			Files.createDirectories(FOLDER);
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (Exception e) {
			HugoSmpEnhancedClient.LOGGER.warn("Debug dump could not be saved", e);
			file = null;
		}
		MutableComponent line = Component.literal("Kopiert: " + what + ". ").withStyle(ChatFormatting.GRAY);
		if (file != null) {
			line.append(Component.literal("[Datei]").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
				.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath().toString()))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(file.getFileName().toString())))));
			line.append(" ");
			line.append(Component.literal("[Ordner]").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
				.withClickEvent(new ClickEvent.OpenFile(FOLDER.toAbsolutePath().toString()))));
		}
		feedback(line);
	}

	private static void feedback(Component text) {
		Minecraft client = Minecraft.getInstance();
		Component line = Component.literal("[HugoSMP Enhanced] ").withStyle(ChatFormatting.GOLD).append(text);
		if (client.player != null) {
			client.gui.getChat().addMessage(line);
		}
	}
}
