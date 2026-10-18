package com.hugosmpenhanced.client.feature.gallery;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;

import net.minecraft.util.Util;

/**
 * Puts a PNG file on the system clipboard as an image. Minecraft runs with AWT headless, so this
 * goes through the operating system's own tools: PowerShell on Windows, osascript on macOS and
 * wl-copy or xclip on Linux.
 */
public final class ImageClipboard {
	private ImageClipboard() {
	}

	/** Copies the image in the background; the future tells whether it worked. */
	public static CompletableFuture<Boolean> copy(Path file) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Process process = new ProcessBuilder(command(file.toAbsolutePath().toString()))
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
				if (!process.waitFor(15, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					return false;
				}
				return process.exitValue() == 0;
			} catch (Exception e) {
				HugoSmpEnhancedClient.LOGGER.warn("Could not copy screenshot to clipboard", e);
				return false;
			}
		}, Util.ioPool());
	}

	private static List<String> command(String path) {
		return switch (Util.getPlatform()) {
			case WINDOWS -> List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-Command",
				"Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; "
					+ "$img = [System.Drawing.Image]::FromFile('" + path.replace("'", "''") + "'); "
					+ "[System.Windows.Forms.Clipboard]::SetImage($img); $img.Dispose()");
			case OSX -> List.of("osascript", "-e",
				"set the clipboard to (read (POSIX file \"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\") as «class PNGf»)");
			default -> System.getenv("WAYLAND_DISPLAY") != null
				? List.of("sh", "-c", "wl-copy --type image/png < \"$0\"", path)
				: List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i", path);
		};
	}
}
