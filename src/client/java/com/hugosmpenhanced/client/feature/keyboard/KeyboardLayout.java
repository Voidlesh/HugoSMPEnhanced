package com.hugosmpenhanced.client.feature.keyboard;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A German ISO (QWERTZ) keyboard plus the mouse buttons, in key units. GLFW key codes name the
 * physical US position of a key, so the German labels are fixed here: the key GLFW calls Y is the
 * one printed Z on a German keyboard, and so on.
 */
public final class KeyboardLayout {
	/** One drawn key: position and size in key units, the input it stands for and its label. */
	public record KeyDef(double x, double y, double w, double h, InputConstants.Key key, String label) {
	}

	public static final double WIDTH = 22.5;
	public static final double HEIGHT = 7.5;
	public static final List<KeyDef> KEYS = build();

	private KeyboardLayout() {
	}

	private static List<KeyDef> build() {
		List<KeyDef> keys = new ArrayList<>();

		// Function row
		key(keys, 0, 0, 1, GLFW_KEY_ESCAPE, "Esc");
		for (int i = 0; i < 12; i++) {
			double x = 2 + i + (i / 4) * 0.5;
			key(keys, x, 0, 1, GLFW_KEY_F1 + i, "F" + (i + 1));
		}
		key(keys, 15.25, 0, 1, GLFW_KEY_PRINT_SCREEN, "Druck");
		key(keys, 16.25, 0, 1, GLFW_KEY_SCROLL_LOCK, "Rollen");
		key(keys, 17.25, 0, 1, GLFW_KEY_PAUSE, "Pause");

		// Number row
		double y = 1.25;
		key(keys, 0, y, 1, GLFW_KEY_GRAVE_ACCENT, "^");
		for (int i = 0; i < 10; i++) {
			int code = i == 9 ? GLFW_KEY_0 : GLFW_KEY_1 + i;
			key(keys, 1 + i, y, 1, code, String.valueOf((i + 1) % 10));
		}
		key(keys, 11, y, 1, GLFW_KEY_MINUS, "ß");
		key(keys, 12, y, 1, GLFW_KEY_EQUAL, "´");
		key(keys, 13, y, 2, GLFW_KEY_BACKSPACE, "Rück");
		key(keys, 15.25, y, 1, GLFW_KEY_INSERT, "Einfg");
		key(keys, 16.25, y, 1, GLFW_KEY_HOME, "Pos1");
		key(keys, 17.25, y, 1, GLFW_KEY_PAGE_UP, "Bild↑");
		key(keys, 18.5, y, 1, GLFW_KEY_NUM_LOCK, "Num");
		key(keys, 19.5, y, 1, GLFW_KEY_KP_DIVIDE, "/");
		key(keys, 20.5, y, 1, GLFW_KEY_KP_MULTIPLY, "*");
		key(keys, 21.5, y, 1, GLFW_KEY_KP_SUBTRACT, "-");

		// Top letter row
		y = 2.25;
		key(keys, 0, y, 1.5, GLFW_KEY_TAB, "Tab");
		row(keys, 1.5, y, new int[] {GLFW_KEY_Q, GLFW_KEY_W, GLFW_KEY_E, GLFW_KEY_R, GLFW_KEY_T, GLFW_KEY_Y, GLFW_KEY_U,
			GLFW_KEY_I, GLFW_KEY_O, GLFW_KEY_P, GLFW_KEY_LEFT_BRACKET}, "QWERTZUIOPÜ");
		key(keys, 12.5, y, 1.25, GLFW_KEY_RIGHT_BRACKET, "+");
		keys.add(new KeyDef(13.75, y, 1.25, 2, keyboard(GLFW_KEY_ENTER), "Enter"));
		key(keys, 15.25, y, 1, GLFW_KEY_DELETE, "Entf");
		key(keys, 16.25, y, 1, GLFW_KEY_END, "Ende");
		key(keys, 17.25, y, 1, GLFW_KEY_PAGE_DOWN, "Bild↓");
		key(keys, 18.5, y, 1, GLFW_KEY_KP_7, "7");
		key(keys, 19.5, y, 1, GLFW_KEY_KP_8, "8");
		key(keys, 20.5, y, 1, GLFW_KEY_KP_9, "9");
		keys.add(new KeyDef(21.5, y, 1, 2, keyboard(GLFW_KEY_KP_ADD), "+"));

		// Home row
		y = 3.25;
		key(keys, 0, y, 1.75, GLFW_KEY_CAPS_LOCK, "Caps");
		row(keys, 1.75, y, new int[] {GLFW_KEY_A, GLFW_KEY_S, GLFW_KEY_D, GLFW_KEY_F, GLFW_KEY_G, GLFW_KEY_H, GLFW_KEY_J,
			GLFW_KEY_K, GLFW_KEY_L, GLFW_KEY_SEMICOLON, GLFW_KEY_APOSTROPHE, GLFW_KEY_BACKSLASH}, "ASDFGHJKLÖÄ#");
		key(keys, 18.5, y, 1, GLFW_KEY_KP_4, "4");
		key(keys, 19.5, y, 1, GLFW_KEY_KP_5, "5");
		key(keys, 20.5, y, 1, GLFW_KEY_KP_6, "6");

		// Bottom letter row
		y = 4.25;
		key(keys, 0, y, 1.25, GLFW_KEY_LEFT_SHIFT, "Shift");
		row(keys, 1.25, y, new int[] {GLFW_KEY_WORLD_2, GLFW_KEY_Z, GLFW_KEY_X, GLFW_KEY_C, GLFW_KEY_V, GLFW_KEY_B,
			GLFW_KEY_N, GLFW_KEY_M, GLFW_KEY_COMMA, GLFW_KEY_PERIOD, GLFW_KEY_SLASH}, "<YXCVBNM,.-");
		key(keys, 12.25, y, 2.75, GLFW_KEY_RIGHT_SHIFT, "Shift");
		key(keys, 16.25, y, 1, GLFW_KEY_UP, "↑");
		key(keys, 18.5, y, 1, GLFW_KEY_KP_1, "1");
		key(keys, 19.5, y, 1, GLFW_KEY_KP_2, "2");
		key(keys, 20.5, y, 1, GLFW_KEY_KP_3, "3");
		keys.add(new KeyDef(21.5, y, 1, 2, keyboard(GLFW_KEY_KP_ENTER), "Enter"));

		// Space row
		y = 5.25;
		key(keys, 0, y, 1.25, GLFW_KEY_LEFT_CONTROL, "Strg");
		key(keys, 1.25, y, 1.25, GLFW_KEY_LEFT_SUPER, "Win");
		key(keys, 2.5, y, 1.25, GLFW_KEY_LEFT_ALT, "Alt");
		key(keys, 3.75, y, 6.25, GLFW_KEY_SPACE, "Leertaste");
		key(keys, 10, y, 1.25, GLFW_KEY_RIGHT_ALT, "AltGr");
		key(keys, 11.25, y, 1.25, GLFW_KEY_RIGHT_SUPER, "Win");
		key(keys, 12.5, y, 1.25, GLFW_KEY_MENU, "Menü");
		key(keys, 13.75, y, 1.25, GLFW_KEY_RIGHT_CONTROL, "Strg");
		key(keys, 15.25, y, 1, GLFW_KEY_LEFT, "←");
		key(keys, 16.25, y, 1, GLFW_KEY_DOWN, "↓");
		key(keys, 17.25, y, 1, GLFW_KEY_RIGHT, "→");
		key(keys, 18.5, y, 2, GLFW_KEY_KP_0, "0");
		key(keys, 20.5, y, 1, GLFW_KEY_KP_DECIMAL, ",");

		// Mouse buttons
		y = 6.5;
		String[] mouse = {"Linksklick", "Rechtsklick", "Mausrad", "Maus 4", "Maus 5"};
		for (int i = 0; i < mouse.length; i++) {
			keys.add(new KeyDef(i * 2.5, y, 2.5, 1, InputConstants.Type.MOUSE.getOrCreate(i), mouse[i]));
		}
		return List.copyOf(keys);
	}

	private static void row(List<KeyDef> keys, double x, double y, int[] codes, String labels) {
		for (int i = 0; i < codes.length; i++) {
			key(keys, x + i, y, 1, codes[i], String.valueOf(labels.charAt(i)));
		}
	}

	private static void key(List<KeyDef> keys, double x, double y, double w, int code, String label) {
		keys.add(new KeyDef(x, y, w, 1, keyboard(code), label));
	}

	private static InputConstants.Key keyboard(int code) {
		return InputConstants.Type.KEYSYM.getOrCreate(code);
	}
}
