package com.hugosmpenhanced.client.feature.colorcodes;

/**
 * Colour helpers for the editor: HSV for the picker and OKLab for mixing, so a gradient from red
 * to green passes through a warm yellow instead of a muddy brown.
 */
public final class ColorMath {
	private ColorMath() {
	}

	/** RGB from hue (0-1), saturation and value. */
	public static int hsv(float h, float s, float v) {
		float hue = (h - (float) Math.floor(h)) * 6f;
		int sector = (int) hue;
		float f = hue - sector;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	/** {hue, saturation, value} of an RGB colour. */
	public static float[] toHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h = 0;
		if (delta > 0) {
			if (max == r) {
				h = ((g - b) / delta) / 6f;
			} else if (max == g) {
				h = ((b - r) / delta + 2) / 6f;
			} else {
				h = ((r - g) / delta + 4) / 6f;
			}
			if (h < 0) {
				h += 1;
			}
		}
		return new float[] {h, max == 0 ? 0 : delta / max, max};
	}

	public static int mix(int from, int to, float t) {
		double[] a = toOklab(from);
		double[] b = toOklab(to);
		return fromOklab(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
	}

	/** The colour at {@code t} (0-1) along evenly spaced stops. */
	public static int sample(int[] stops, float t) {
		if (stops.length == 1) {
			return stops[0];
		}
		float pos = Math.max(0, Math.min(1, t)) * (stops.length - 1);
		int index = Math.min((int) pos, stops.length - 2);
		return mix(stops[index], stops[index + 1], pos - index);
	}

	public static double distance(int a, int b) {
		double[] x = toOklab(a);
		double[] y = toOklab(b);
		return Math.pow(x[0] - y[0], 2) + Math.pow(x[1] - y[1], 2) + Math.pow(x[2] - y[2], 2);
	}

	/** Black or white, whichever reads better on the colour. */
	public static int contrastText(int rgb) {
		return toOklab(rgb)[0] > 0.65 ? 0xFF000000 : 0xFFFFFFFF;
	}

	private static double toLinear(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static int fromLinear(double c) {
		double v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
		return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
	}

	private static double[] toOklab(int rgb) {
		double r = toLinear((rgb >> 16) & 0xFF);
		double g = toLinear((rgb >> 8) & 0xFF);
		double b = toLinear(rgb & 0xFF);
		double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
		double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
		double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
		return new double[] {
			0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
			1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
			0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
	}

	private static int fromOklab(double lightness, double a, double b) {
		double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
		double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
		double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
		int r = fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
		int g = fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
		int bl = fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
		return (r << 16) | (g << 8) | bl;
	}
}
