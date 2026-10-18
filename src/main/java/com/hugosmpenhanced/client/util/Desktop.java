package com.hugosmpenhanced.client.util;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

import com.mojang.blaze3d.Blaze3D;

/** Opens a link or a folder in the system's default program. */
public final class Desktop {
	private Desktop() {
	}

	public static void openUri(String uri) {
		Blaze3D.openUri(URI.create(uri));
	}

	public static void openPath(Path path) {
		Blaze3D.openPath(path);
	}

	public static void openFile(File file) {
		Blaze3D.openPath(file.toPath());
	}
}
