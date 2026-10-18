package com.hugosmpenhanced.client.feature.spawnerstorage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import com.google.gson.reflect.TypeToken;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.auction.AuctionLore;
import com.hugosmpenhanced.client.feature.tpa.TpaToast;
import com.hugosmpenhanced.client.formats.HugoFormats;
import com.hugosmpenhanced.client.util.JsonStore;
import com.hugosmpenhanced.client.util.MenuContext;
import com.hugosmpenhanced.client.util.ServerCheck;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/**
 * Remembers each spawner whose menu you opened: how full its storage was and how fast it filled
 * between two visits. From that it works out when it will be full and reminds you (toast, HUD,
 * {@code /hse spawner}) before loot is lost.
 * <p>
 * HugoSMP gives no rate, so it is measured: the first visit only gives a level, the second the
 * speed. Emptying the storage in between starts the measuring over from the new level, the speed
 * found so far stays. The fill comes from a "Lager: 1.234/2.304" line (formats key
 * "spawner.storage"), otherwise from the loot slots of the menu, which is an estimate.
 */
public final class SpawnerStorage {
	private static final long OPEN_WINDOW_MS = 3_000;

	/** One spawner as last seen. Times in ms; rate in items per hour, -1 while unknown. */
	public static final class Spawner {
		public String dimension;
		public int x;
		public int y;
		public int z;
		public String mob;
		public int stack = 1;
		public long stored;
		public long capacity;
		public boolean estimated;
		public long seenAt;
		public double rate = -1;
		public boolean reminded;

		public BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		/** Stored items now, projected from the last visit. */
		public long storedNow(long now) {
			if (this.rate <= 0) {
				return this.stored;
			}
			return Math.min(this.capacity, this.stored + Math.round(this.rate * (now - this.seenAt) / 3_600_000.0));
		}

		/** 0–100, projected. */
		public int percent(long now) {
			return this.capacity <= 0 ? 0 : (int) Math.min(100, this.storedNow(now) * 100 / this.capacity);
		}

		/** When it will be full, or -1 when unknown, or 0 when full already. */
		public long fullAt() {
			if (this.capacity <= 0 || this.rate <= 0) {
				return -1;
			}
			if (this.stored >= this.capacity) {
				return 0;
			}
			return this.seenAt + Math.round((this.capacity - this.stored) * 3_600_000.0 / this.rate);
		}

		public String label() {
			return (this.stack > 1 ? this.stack + "× " : "") + (this.mob == null ? "Spawner" : this.mob);
		}
	}

	private static final JsonStore<List<Spawner>> STORE = new JsonStore<>("spawner.json", new TypeToken<ArrayList<Spawner>>() { }.getType(), ArrayList::new);
	private static List<Spawner> spawners;
	private static BlockPos clickedPos;
	private static String clickedDimension;
	private static String clickedMob;
	private static long clickedAt;
	private static int ticks;

	private SpawnerStorage() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && level.getBlockEntity(hit.getBlockPos()) instanceof SpawnerBlockEntity spawner) {
				clickedPos = hit.getBlockPos().immutable();
				clickedDimension = level.dimension().identifier().toString();
				clickedAt = System.currentTimeMillis();
				clickedMob = null;
				try {
					Entity shown = spawner.getSpawner().getOrCreateDisplayEntity(level, hit.getBlockPos());
					if (shown != null) {
						clickedMob = shown.getType().getDescription().getString();
					}
				} catch (Exception ignored) {
					// Some servers send spawners without data; the menu title may still name the mob.
				}
			}
			return InteractionResult.PASS;
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				ScreenEvents.afterTick(container).register(s -> read(container));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks % 100 == 0) {
				remind();
			}
		});
	}

	public static boolean active() {
		return ModConfigManager.CONFIG.spawnerReminderEnabled && ServerCheck.isHugoSmp();
	}

	public static synchronized List<Spawner> spawners() {
		if (spawners == null) {
			spawners = new ArrayList<>(STORE.load());
			spawners.removeIf(s -> s == null || s.dimension == null);
		}
		return spawners;
	}

	// ------------------------------------------------------------------ reading the menu

	private static void read(AbstractContainerScreen<?> container) {
		if (!active() || clickedPos == null || System.currentTimeMillis() - clickedAt > OPEN_WINDOW_MS + 60_000) {
			return;
		}
		String title = HugoFormats.clean(container.getTitle().getString());
		if (MenuContext.kindOf(title) != MenuContext.Kind.SPAWNER) {
			return;
		}
		if (container.getMenu().slots.stream().allMatch(slot -> slot.getItem().isEmpty())) {
			// Contents not there yet.
			return;
		}
		record(title, container.getMenu().slots, System.currentTimeMillis());
	}

	/** Reads one spawner menu; public for game tests. */
	public static void record(String title, List<Slot> slots, long now) {
		if (clickedPos == null || now - clickedAt > OPEN_WINDOW_MS + 60_000) {
			return;
		}
		long stored = -1;
		long capacity = -1;
		int stack = -1;
		for (String line : linesOf(title, slots)) {
			if (stored < 0) {
				Matcher matcher = HugoFormats.find("spawner.storage", line);
				if (matcher != null) {
					Double a = AuctionLore.parseAmount(HugoFormats.group(matcher, "done"));
					Double b = AuctionLore.parseAmount(HugoFormats.group(matcher, "total"));
					if (a != null && b != null && b > 0) {
						stored = Math.round(a);
						capacity = Math.round(b);
					}
				}
			}
			if (stack < 0) {
				Matcher matcher = HugoFormats.find("spawner.stack", line);
				if (matcher != null) {
					try {
						stack = Integer.parseInt(HugoFormats.group(matcher, "count"));
					} catch (NumberFormatException ignored) {
						// Not a number after all.
					}
				}
			}
		}
		boolean estimated = false;
		if (stored < 0) {
			long[] fromSlots = fromSlots(slots);
			stored = fromSlots[0];
			capacity = fromSlots[1];
			estimated = true;
		}
		if (capacity <= 0) {
			return;
		}
		Spawner spawner = find(clickedDimension, clickedPos);
		if (spawner == null) {
			spawner = new Spawner();
			spawner.dimension = clickedDimension;
			spawner.x = clickedPos.getX();
			spawner.y = clickedPos.getY();
			spawner.z = clickedPos.getZ();
			spawners().add(spawner);
		} else if (now - spawner.seenAt < 30_000 && spawner.stored == stored) {
			// Same visit, nothing new.
			return;
		} else if (stored > spawner.stored && now - spawner.seenAt >= 5 * 60_000 && spawner.stored < spawner.capacity) {
			double measured = (stored - spawner.stored) * 3_600_000.0 / (now - spawner.seenAt);
			// Smooth a little, so one odd visit does not throw it off.
			spawner.rate = spawner.rate <= 0 ? measured : spawner.rate * 0.3 + measured * 0.7;
		}
		spawner.mob = clickedMob != null ? clickedMob : mobFromTitle(title, spawner.mob);
		if (stack > 0) {
			spawner.stack = stack;
		}
		if (now - spawner.seenAt >= 30_000 || stored < spawner.stored) {
			spawner.stored = stored;
			spawner.seenAt = now;
		} else {
			spawner.stored = stored;
		}
		spawner.capacity = capacity;
		spawner.estimated = estimated;
		spawner.reminded = spawner.percent(now) >= ModConfigManager.CONFIG.spawnerReminderPercent;
		save();
	}

	private static List<String> linesOf(String title, List<Slot> slots) {
		List<String> lines = new ArrayList<>();
		lines.add(title);
		for (Slot slot : slots) {
			if (slot.container instanceof Inventory || slot.getItem().isEmpty()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			lines.add(HugoFormats.clean(stack.getHoverName().getString()));
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					lines.add(HugoFormats.clean(line.getString()));
				}
			}
		}
		return lines;
	}

	/**
	 * {stored, capacity} from the loot slots: every menu slot but the bottom row of buttons, and no
	 * item with a lore (buttons have one, loot has none).
	 */
	static long[] fromSlots(List<Slot> slots) {
		List<Slot> menu = new ArrayList<>();
		for (Slot slot : slots) {
			if (!(slot.container instanceof Inventory)) {
				menu.add(slot);
			}
		}
		int size = menu.size();
		int lootSlots = size >= 18 && size % 9 == 0 ? size - 9 : size;
		long stored = 0;
		long capacity = 0;
		for (int i = 0; i < lootSlots; i++) {
			ItemStack stack = menu.get(i).getItem();
			if (!stack.isEmpty() && (stack.has(DataComponents.LORE) || stack.is(Items.STAINED_GLASS_PANE.black()) || stack.is(Items.STAINED_GLASS_PANE.gray()))) {
				continue;
			}
			stored += stack.getCount();
			capacity += stack.isEmpty() ? 64 : stack.getMaxStackSize();
		}
		return new long[] {stored, capacity};
	}

	private static String mobFromTitle(String title, String fallback) {
		String name = title.replaceAll("(?i)spawner|lager|storage|[\\[\\]()|:»>-]", " ").replaceAll("\\d+\\s*[x×]|[x×]\\s*\\d+", " ")
			.replaceAll("\\s+", " ").strip();
		return name.isEmpty() || name.length() > 24 ? fallback : name;
	}

	private static Spawner find(String dimension, BlockPos pos) {
		for (Spawner spawner : spawners()) {
			if (spawner.dimension.equals(dimension) && spawner.x == pos.getX() && spawner.y == pos.getY() && spawner.z == pos.getZ()) {
				return spawner;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ reminders

	private static void remind() {
		if (!active() || !ModConfigManager.CONFIG.spawnerReminderToast) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Spawner spawner : spawners()) {
			if (spawner.reminded || spawner.rate <= 0 || spawner.percent(now) < ModConfigManager.CONFIG.spawnerReminderPercent) {
				continue;
			}
			spawner.reminded = true;
			save();
			boolean full = spawner.percent(now) >= 100;
			TpaToast.show(Component.literal(full ? "Spawner-Lager voll" : "Spawner-Lager fast voll"),
				Component.literal(spawner.label() + " · " + spawner.percent(now) + " %" + where(spawner)),
				new ItemStack(Items.SPAWNER), 8_000, full ? TpaToast.WARNING_COLOR : 0xFFF0BE3C);
		}
	}

	/** " · 120 Blöcke" when in the same world, else "". */
	static String where(Spawner spawner) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || !client.level.dimension().identifier().toString().equals(spawner.dimension)) {
			return "";
		}
		double distance = Math.sqrt(client.player.blockPosition().distSqr(spawner.pos()));
		return " · " + (int) distance + " Blöcke";
	}

	public static synchronized void forget(Spawner spawner) {
		spawners().remove(spawner);
		save();
	}

	public static synchronized void forgetAll() {
		spawners().clear();
		save();
	}

	private static void save() {
		STORE.save(new ArrayList<>(spawners()));
	}

	/** "voll in 2 Std. 10 Min.", "voll", "Tempo unbekannt". */
	public static String fullText(Spawner spawner, long now) {
		long at = spawner.fullAt();
		if (at < 0) {
			return "Tempo nach 2. Besuch";
		}
		if (at <= now) {
			return "voll";
		}
		long minutes = (at - now) / 60_000;
		if (minutes < 60) {
			return "voll in " + Math.max(1, minutes) + " Min.";
		}
		long hours = minutes / 60;
		if (hours < 48) {
			return "voll in " + hours + " Std." + (minutes % 60 >= 5 && hours < 10 ? " " + minutes % 60 + " Min." : "");
		}
		return "voll in " + hours / 24 + " Tagen";
	}

	/** For game tests: as if this spawner had just been right-clicked. */
	public static void clicked(String dimension, BlockPos pos, String mob) {
		clickedDimension = dimension;
		clickedPos = pos;
		clickedMob = mob;
		clickedAt = System.currentTimeMillis();
	}
}
