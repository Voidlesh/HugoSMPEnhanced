package com.hugosmpenhanced.client.feature.antileak;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

/**
 * "Random" coordinates for F3: a made-up position that jumps to a new place every second. It is
 * never an offset from the real one (a fixed offset would give itself away the moment viewers see
 * F3 at a known place like spawn), and chunk, region and targeted block are derived from it so a
 * screen still looks consistent.
 */
public final class FakeCoordinates {
	private static final long ROLL_MILLIS = 1000;

	private static long nextRoll;
	private static int x;
	private static int y;
	private static int z;

	private FakeCoordinates() {
	}

	/** What one number on the F3 screen stands for. */
	public enum Kind {
		POS_X, POS_Y, POS_Z,
		BLOCK_X, BLOCK_Y, BLOCK_Z,
		CHUNK_X, SECTION_Y, CHUNK_Z,
		REGION_LOCAL_X, REGION_LOCAL_Z, REGION_X, REGION_Z,
		RELATIVE_X, RELATIVE_Y, RELATIVE_Z,
		TARGET_X, TARGET_Y, TARGET_Z
	}

	/** The made-up value for {@code token}, written like it (decimals, comma or point). */
	public static synchronized String value(Kind kind, String token) {
		roll();
		return switch (kind) {
			case POS_X -> decimal(x, token);
			case POS_Y -> decimal(y, token);
			case POS_Z -> decimal(z, token);
			case BLOCK_X -> String.valueOf(x);
			case BLOCK_Y -> String.valueOf(y);
			case BLOCK_Z -> String.valueOf(z);
			case CHUNK_X -> String.valueOf(x >> 4);
			case SECTION_Y -> String.valueOf(y >> 4);
			case CHUNK_Z -> String.valueOf(z >> 4);
			case REGION_LOCAL_X -> String.valueOf((x >> 4) & 31);
			case REGION_LOCAL_Z -> String.valueOf((z >> 4) & 31);
			case REGION_X -> String.valueOf(x >> 9);
			case REGION_Z -> String.valueOf(z >> 9);
			case RELATIVE_X -> pad(x & 15, token);
			case RELATIVE_Y -> pad(y & 15, token);
			case RELATIVE_Z -> pad(z & 15, token);
			case TARGET_X -> String.valueOf(x + relative(token, real(0)));
			case TARGET_Y -> String.valueOf(y + relative(token, real(1)));
			case TARGET_Z -> String.valueOf(z + relative(token, real(2)));
		};
	}

	private static void roll() {
		long now = Util.getMillis();
		if (now < nextRoll) {
			return;
		}
		nextRoll = now + ROLL_MILLIS;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		x = coordinate(random);
		z = coordinate(random);
		y = random.nextInt(-58, 140);
	}

	/** A plausible horizontal coordinate, away from the centre of the map. */
	private static int coordinate(ThreadLocalRandom random) {
		int value = random.nextInt(300, 24000);
		return random.nextBoolean() ? value : -value;
	}

	/** Targeted blocks keep their distance to the player, which on its own says nothing. */
	private static int relative(String token, int real) {
		try {
			return Integer.parseInt(token.trim()) - real;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int real(int axis) {
		Entity camera = Minecraft.getInstance().getCameraEntity();
		if (camera == null) {
			return 0;
		}
		BlockPos pos = camera.blockPosition();
		return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
	}

	private static String decimal(int whole, String token) {
		int point = Math.max(token.lastIndexOf('.'), token.lastIndexOf(','));
		int decimals = point < 0 ? 0 : token.length() - point - 1;
		// The fraction is made up as well, it would otherwise tell where in the block you stand
		double value = whole + (whole < 0 ? -fraction(decimals) : fraction(decimals));
		String text = String.format(Locale.ROOT, "%." + decimals + "f", value);
		return point >= 0 && token.charAt(point) == ',' ? text.replace('.', ',') : text;
	}

	private static double fraction(int decimals) {
		return decimals == 0 ? 0 : (Math.abs(x * 31 + z * 17) % 1000) / 1000.0;
	}

	private static String pad(int value, String token) {
		return token.length() == 2 ? String.format(Locale.ROOT, "%02d", value) : String.valueOf(value);
	}
}
