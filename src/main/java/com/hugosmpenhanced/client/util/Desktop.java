//? only modern
package com.hugosmpenhanced.client.util;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

//? if v263
import com.mojang.blaze3d.Blaze3D;
//? else
import net.minecraft.util.Util;
//? end

/** Opens a link or a folder in the system's default program. */
public final class Desktop {
	private Desktop() {
	}

	public static void openUri(String uri) {
		//? if v263
		Blaze3D.openUri(URI.create(uri));
		//? else
		Util.getPlatform().openUri(uri);
		//? end
	}

	public static void openPath(Path path) {
		//? if v263
		Blaze3D.openPath(path);
		//? else
		Util.getPlatform().openPath(path);
		//? end
	}

	public static void openFile(File file) {
		openPath(file.toPath());
	}
}
