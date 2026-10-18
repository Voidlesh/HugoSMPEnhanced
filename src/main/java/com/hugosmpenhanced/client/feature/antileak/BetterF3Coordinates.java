package com.hugosmpenhanced.client.feature.antileak;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.feature.antileak.FakeCoordinates.Kind;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Anti-Leak for the BetterF3 mod, which draws its own F3 screen from "lines" with ids. Their values
 * are swapped as BetterF3 sets them: blurred and blacked-out numbers are placeholder digits in a
 * font of smudges or solid blocks, so again no real digit is ever drawn.
 */
public final class BetterF3Coordinates {
	private static final FontDescription BLUR_FONT = new FontDescription.Resource(
		Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "antileak_blur"));
	private static final FontDescription BAR_FONT = new FontDescription.Resource(
		Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "antileak_bar"));
	private static final String[] FAKE = {"4817", "2093", "7360"};

	/** BetterF3 line ids holding x, y, z as their last three values (XYZ has a label in front). */
	private static final Map<String, Kind[]> TRIPLES = Map.of(
		"player_coords", new Kind[] {Kind.POS_X, Kind.POS_Y, Kind.POS_Z},
		"block_coords", new Kind[] {Kind.BLOCK_X, Kind.BLOCK_Y, Kind.BLOCK_Z},
		"chunk_relative_coords", new Kind[] {Kind.RELATIVE_X, Kind.RELATIVE_Y, Kind.RELATIVE_Z},
		"chunk_coords", new Kind[] {Kind.CHUNK_X, Kind.SECTION_Y, Kind.CHUNK_Z});
	private static final Kind[] TARGET = {Kind.TARGET_X, Kind.TARGET_Y, Kind.TARGET_Z};

	private static Field disableMod;
	private static boolean lookedUp;

	private BetterF3Coordinates() {
	}

	/** True when BetterF3 is installed and draws the F3 screen itself. */
	public static boolean drawsF3() {
		if (!lookedUp) {
			lookedUp = true;
			if (FabricLoader.getInstance().isModLoaded("betterf3")) {
				try {
					disableMod = Class.forName("me.cominixo.betterf3.config.GeneralOptions").getField("disableMod");
				} catch (ReflectiveOperationException e) {
					HugoSmpEnhancedClient.LOGGER.warn("BetterF3 found, but not its options; assuming it draws F3", e);
				}
			}
		}
		if (!FabricLoader.getInstance().isModLoaded("betterf3")) {
			return false;
		}
		try {
			return disableMod == null || !disableMod.getBoolean(null);
		} catch (IllegalAccessException e) {
			return true;
		}
	}

	/** The value BetterF3 should show for line {@code id}. */
	public static Object mask(String id, Object value) {
		if (!AntiLeak.maskF3() || value == null) {
			return value;
		}
		Kind[] kinds = TRIPLES.get(id);
		if (kinds != null && value instanceof List<?> list && list.size() >= 3) {
			int first = list.size() - 3;
			List<Object> out = new ArrayList<>(list.subList(0, first));
			for (int i = 0; i < 3; i++) {
				Object part = list.get(first + i);
				out.add(i == 1 && AntiLeak.showY() ? part : replace(part, kinds[i], i));
			}
			return out;
		}
		if (("targeted_block".equals(id) || "targeted_fluid".equals(id)) && value instanceof String text) {
			return target(text);
		}
		return value;
	}

	/** "x, y, z" of the block or fluid you look at. */
	private static Object target(String text) {
		String[] numbers = text.split(", ");
		if (numbers.length != 3) {
			return text;
		}
		boolean random = AntiLeak.maskStyle() == CoordinateMask.RANDOM;
		MutableComponent line = Component.empty();
		StringBuilder plain = new StringBuilder();
		for (int i = 0; i < 3; i++) {
			String number = numbers[i];
			if (!(i == 1 && AntiLeak.showY())) {
				number = random ? FakeCoordinates.value(TARGET[i], number) : null;
			}
			if (i > 0) {
				line.append(", ");
				plain.append(", ");
			}
			if (number == null) {
				line.append(hidden(Style.EMPTY, i));
			} else {
				line.append(number);
				plain.append(number);
			}
		}
		// A plain string keeps BetterF3's own colour for the value
		return random ? plain.toString() : line;
	}

	private static Object replace(Object part, Kind kind, int index) {
		Style style = part instanceof Component component ? component.getStyle() : Style.EMPTY;
		String token = part instanceof Component component ? component.getString() : String.valueOf(part);
		if (AntiLeak.maskStyle() == CoordinateMask.RANDOM) {
			return Component.literal(FakeCoordinates.value(kind, token)).withStyle(style);
		}
		return hidden(style, index);
	}

	private static Component hidden(Style style, int index) {
		if (AntiLeak.maskStyle() == CoordinateMask.BAR) {
			return Component.literal("0000").withStyle(style.withFont(BAR_FONT).withColor(0x000000));
		}
		return Component.literal(FAKE[index % FAKE.length]).withStyle(style.withFont(BLUR_FONT));
	}
}
