package com.hugosmpenhanced.client.feature.keyboard;

import com.hugosmpenhanced.client.util.KeyCodes;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;


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
		key(keys, 0, 0, 1, InputConstants.KEY_ESCAPE, "Esc");
		for (int i = 0; i < 12; i++) {
			double x = 2 + i + (i / 4) * 0.5;
			key(keys, x, 0, 1, InputConstants.KEY_F1 + i, "F" + (i + 1));
		}
		key(keys, 15.25, 0, 1, InputConstants.KEY_PRINTSCREEN, "Druck");
		key(keys, 16.25, 0, 1, InputConstants.KEY_SCROLLLOCK, "Rollen");
		key(keys, 17.25, 0, 1, InputConstants.KEY_PAUSE, "Pause");

		// Number row
		double y = 1.25;
		key(keys, 0, y, 1, InputConstants.KEY_GRAVE, "^");
		for (int i = 0; i < 10; i++) {
			int code = i == 9 ? InputConstants.KEY_0 : InputConstants.KEY_1 + i;
			key(keys, 1 + i, y, 1, code, String.valueOf((i + 1) % 10));
		}
		key(keys, 11, y, 1, InputConstants.KEY_MINUS, "ß");
		key(keys, 12, y, 1, InputConstants.KEY_EQUALS, "´");
		key(keys, 13, y, 2, InputConstants.KEY_BACKSPACE, "Rück");
		key(keys, 15.25, y, 1, InputConstants.KEY_INSERT, "Einfg");
		key(keys, 16.25, y, 1, InputConstants.KEY_HOME, "Pos1");
		key(keys, 17.25, y, 1, InputConstants.KEY_PAGEUP, "Bild↑");
		key(keys, 18.5, y, 1, InputConstants.KEY_NUMLOCK, "Num");
		key(keys, 19.5, y, 1, KeyCodes.KP_DIVIDE, "/");
		key(keys, 20.5, y, 1, InputConstants.KEY_MULTIPLY, "*");
		key(keys, 21.5, y, 1, KeyCodes.KP_SUBTRACT, "-");

		// Top letter row
		y = 2.25;
		key(keys, 0, y, 1.5, InputConstants.KEY_TAB, "Tab");
		row(keys, 1.5, y, new int[] {InputConstants.KEY_Q, InputConstants.KEY_W, InputConstants.KEY_E, InputConstants.KEY_R, InputConstants.KEY_T, InputConstants.KEY_Y, InputConstants.KEY_U,
			InputConstants.KEY_I, InputConstants.KEY_O, InputConstants.KEY_P, InputConstants.KEY_LBRACKET}, "QWERTZUIOPÜ");
		key(keys, 12.5, y, 1.25, InputConstants.KEY_RBRACKET, "+");
		keys.add(new KeyDef(13.75, y, 1.25, 2, keyboard(InputConstants.KEY_RETURN), "Enter"));
		key(keys, 15.25, y, 1, InputConstants.KEY_DELETE, "Entf");
		key(keys, 16.25, y, 1, InputConstants.KEY_END, "Ende");
		key(keys, 17.25, y, 1, InputConstants.KEY_PAGEDOWN, "Bild↓");
		key(keys, 18.5, y, 1, InputConstants.KEY_NUMPAD7, "7");
		key(keys, 19.5, y, 1, InputConstants.KEY_NUMPAD8, "8");
		key(keys, 20.5, y, 1, InputConstants.KEY_NUMPAD9, "9");
		keys.add(new KeyDef(21.5, y, 1, 2, keyboard(InputConstants.KEY_ADD), "+"));

		// Home row
		y = 3.25;
		key(keys, 0, y, 1.75, InputConstants.KEY_CAPSLOCK, "Caps");
		row(keys, 1.75, y, new int[] {InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_F, InputConstants.KEY_G, InputConstants.KEY_H, InputConstants.KEY_J,
			InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_SEMICOLON, InputConstants.KEY_APOSTROPHE, InputConstants.KEY_BACKSLASH}, "ASDFGHJKLÖÄ#");
		key(keys, 18.5, y, 1, InputConstants.KEY_NUMPAD4, "4");
		key(keys, 19.5, y, 1, InputConstants.KEY_NUMPAD5, "5");
		key(keys, 20.5, y, 1, InputConstants.KEY_NUMPAD6, "6");

		// Bottom letter row
		y = 4.25;
		key(keys, 0, y, 1.25, InputConstants.KEY_LSHIFT, "Shift");
		row(keys, 1.25, y, new int[] {KeyCodes.WORLD_2, InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C, InputConstants.KEY_V, InputConstants.KEY_B,
			InputConstants.KEY_N, InputConstants.KEY_M, InputConstants.KEY_COMMA, InputConstants.KEY_PERIOD, InputConstants.KEY_SLASH}, "<YXCVBNM,.-");
		key(keys, 12.25, y, 2.75, InputConstants.KEY_RSHIFT, "Shift");
		key(keys, 16.25, y, 1, InputConstants.KEY_UP, "↑");
		key(keys, 18.5, y, 1, InputConstants.KEY_NUMPAD1, "1");
		key(keys, 19.5, y, 1, InputConstants.KEY_NUMPAD2, "2");
		key(keys, 20.5, y, 1, InputConstants.KEY_NUMPAD3, "3");
		keys.add(new KeyDef(21.5, y, 1, 2, keyboard(InputConstants.KEY_NUMPADENTER), "Enter"));

		// Space row
		y = 5.25;
		key(keys, 0, y, 1.25, InputConstants.KEY_LCONTROL, "Strg");
		key(keys, 1.25, y, 1.25, InputConstants.KEY_LGUI, "Win");
		key(keys, 2.5, y, 1.25, InputConstants.KEY_LALT, "Alt");
		key(keys, 3.75, y, 6.25, InputConstants.KEY_SPACE, "Leertaste");
		key(keys, 10, y, 1.25, InputConstants.KEY_RALT, "AltGr");
		key(keys, 11.25, y, 1.25, InputConstants.KEY_RGUI, "Win");
		key(keys, 12.5, y, 1.25, KeyCodes.MENU, "Menü");
		key(keys, 13.75, y, 1.25, InputConstants.KEY_RCONTROL, "Strg");
		key(keys, 15.25, y, 1, InputConstants.KEY_LEFT, "←");
		key(keys, 16.25, y, 1, InputConstants.KEY_DOWN, "↓");
		key(keys, 17.25, y, 1, InputConstants.KEY_RIGHT, "→");
		key(keys, 18.5, y, 2, InputConstants.KEY_NUMPAD0, "0");
		key(keys, 20.5, y, 1, KeyCodes.KP_PERIOD, ",");

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
		return InputConstants.Type.KEYBOARD.getOrCreate(code);
	}
}
