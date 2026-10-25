package com.hugosmpenhanced.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.JsonParser;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.mainmenu.MainMenuLayout;
import com.hugosmpenhanced.client.feature.update.ModUpdates;
import com.hugosmpenhanced.client.feature.update.UpdateInstaller;
import com.hugosmpenhanced.client.feature.update.UpdateScreen;
import com.hugosmpenhanced.client.settings.SettingsScreen;
import com.hugosmpenhanced.updater.UpdateHelper;
import com.sun.net.httpserver.HttpServer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Update notice and installer against a local fake of the GitHub API: badge on both title screens,
 * the update screen, a release whose jar needs another Minecraft version (refused), a good one
 * (downloaded, checked, ready), the settings tab, the chat line and its command. The helper that
 * swaps the jar is tested directly here on files; the real one is started when this client closes
 * and swaps {@code update-test/mods/hugosmpenhanced-0.1.0-alpha.jar} for the 9.9.9 jar.
 */
@SuppressWarnings("UnstableApiUsage")
public class UpdateGameTest implements FabricClientGameTest {
	/** The running game, so the fixture jars depend on the version this client actually has. */
	private static final String GAME_VERSION = net.minecraft.SharedConstants.getCurrentVersion().name();

	private static final String CHANGELOG = """
		## Neu
		- **Update-Hinweis**: neue Versionen erscheinen im Hauptmenü, mit Klick installieren
		- Streamer-Modus versteckt Namen, siehe [Wiki](https://example.com)
		- Eigene `Client`-Einstellungen
		  - Unterpunkt mit Einrückung, der so lang ist, dass er auf jeden Fall in die nächste Zeile umbrechen muss

		## Behoben
		- Taste O öffnet das Menü wieder zuverlässig
		- Farbcodes nutzen nur noch &7 und Co. (35 Zeichen Limit)
		- F3-Koordinaten auch mit BetterF3 versteckt

		> Danke an alle, die Fehler gemeldet haben!
		""";

	private final Map<String, byte[]> files = new ConcurrentHashMap<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		server.createContext("/", exchange -> {
			byte[] body = this.files.get(exchange.getRequestURI().getPath());
			exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
			if (body == null) {
				exchange.sendResponseHeaders(404, -1);
			} else {
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			}
			exchange.close();
		});
		server.start();
		try {
			run(context, "http://127.0.0.1:" + server.getAddress().getPort());
		} finally {
			server.stop(0);
			System.clearProperty("hugosmpenhanced.updateUrl");
		}
	}

	private void run(ClientGameTestContext context, String base) {
		Path game = FabricLoader.getInstance().getGameDir().resolve("update-test");
		Path oldJar = game.resolve("mods/hugosmpenhanced-0.1.0-alpha.jar");
		try {
			testHelper(game.resolve("helper-check"));
			Files.createDirectories(oldJar.getParent());
			try (var stream = Files.list(oldJar.getParent())) {
				for (Path file : stream.toList()) {
					Files.delete(file);
				}
			}
			// stands in for the installed mod: the helper runs from a copy of it
			Files.write(oldJar, jar("0.1.0-alpha", GAME_VERSION, true));
			byte[] good = jar("9.9.9", GAME_VERSION, false);
			byte[] future = jar("9.9.10", "1.22", false);
			this.files.put("/dl/hugosmpenhanced-9.9.9.jar", good);
			this.files.put("/dl/hugosmpenhanced-9.9.10.jar", future);
			this.files.put("/good", releases(base, "9.9.9", good).getBytes(StandardCharsets.UTF_8));
			this.files.put("/future", releases(base, "9.9.10", future).getBytes(StandardCharsets.UTF_8));
			this.files.put("/none", "[]".getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		System.setProperty("hugosmpenhanced.updateModJar", oldJar.toString());

		context.runOnClient(client -> {
			client.options.menuBackgroundBlurriness().set(0);
			ModConfigManager.CONFIG.updateCheck = true;
			ModConfigManager.CONFIG.updateAutoInstall = false;
			ModConfigManager.CONFIG.updateSkippedVersion = "";
			ModConfigManager.CONFIG.mainMenuCustom = true;
			ModConfigManager.CONFIG.mainMenuLayout = MainMenuLayout.CLASSIC;
		});

		// nothing newer: no badge
		check(context, base + "/none");
		assertTrue(ModUpdates.status() == ModUpdates.Status.UP_TO_DATE, "empty list means up to date, was " + ModUpdates.status());

		// a release for another Minecraft version is found but refused after the download
		check(context, base + "/future");
		ModUpdates.Release future = ModUpdates.available();
		assertTrue(future != null && future.version().equals("9.9.10"), "9.9.10 found");
		context.setScreen(() -> new UpdateScreen(null, future));
		context.runOnClient(client -> UpdateInstaller.download(future));
		waitFor(context, () -> UpdateInstaller.state(future) == UpdateInstaller.State.INCOMPATIBLE, "incompatible jar refused");
		assertTrue(!UpdateInstaller.canInstall(future), "incompatible release is not offered for install");
		assertTrue(UpdateInstaller.message().contains("Minecraft 1.22"), "reason names Minecraft 1.22: " + UpdateInstaller.message());
		context.waitTicks(3);
		context.takeScreenshot("update-inkompatibel");

		// the good release: badge on both title screens, screen, download
		check(context, base + "/good");
		ModUpdates.Release release = ModUpdates.available();
		assertTrue(release != null && release.version().equals("9.9.9") && release.jar() != null, "9.9.9 with jar");
		// one release with a jar per Minecraft version: each game gets its own, sources are never offered
		com.google.gson.JsonObject both = JsonParser.parseString("{\"assets\":["
			+ "{\"name\":\"hugosmpenhanced-1.0.0-sources.jar\",\"browser_download_url\":\"http://x/s\"},"
			+ "{\"name\":\"hugosmpenhanced-1.0.0.jar\",\"browser_download_url\":\"http://x/a\"},"
			+ "{\"name\":\"hugosmpenhanced-1.0.0-mc26.3.jar\",\"browser_download_url\":\"http://x/b\"}]}").getAsJsonObject();
		assertTrue(ModUpdates.pickJar(both, "26.3").name().equals("hugosmpenhanced-1.0.0-mc26.3.jar"), "26.3 gets its own jar");
		assertTrue(ModUpdates.pickJar(both, "1.21.11").name().equals("hugosmpenhanced-1.0.0.jar"), "1.21.11 gets the untagged jar");
		com.google.gson.JsonObject onlyTagged = JsonParser.parseString("{\"assets\":["
			+ "{\"name\":\"hugosmpenhanced-1.0.0-mc26.3.jar\",\"browser_download_url\":\"http://x/b\"}]}").getAsJsonObject();
		assertTrue(ModUpdates.pickJar(onlyTagged, "1.21.11") == null, "a jar for another game is not offered");
		context.setScreen(TitleScreen::new);
		context.waitTicks(10);
		context.takeScreenshot("update-hauptmenue-klassisch");
		context.runOnClient(client -> ModConfigManager.CONFIG.mainMenuLayout = MainMenuLayout.SIDEBAR);
		context.setScreen(TitleScreen::new);
		context.waitTicks(10);
		context.takeScreenshot("update-hauptmenue-seitenleiste");
		context.runOnClient(client -> {
			ModConfigManager.CONFIG.mainMenuLayout = MainMenuLayout.CLASSIC;
			ModConfigManager.CONFIG.mainMenuCustom = false;
		});
		context.setScreen(TitleScreen::new);
		context.waitTicks(10);
		context.takeScreenshot("update-hauptmenue-vanilla");
		boolean badge = context.computeOnClient(client -> client.gui.screen().children().stream()
			.anyMatch(w -> w.getClass().getSimpleName().equals("UpdateBadge")));
		assertTrue(badge, "vanilla title screen has the badge");
		context.runOnClient(client -> ModConfigManager.CONFIG.mainMenuCustom = true);

		context.setScreen(() -> new UpdateScreen(new TitleScreen(), release));
		context.waitTicks(3);
		context.takeScreenshot("update-fenster");
		context.runOnClient(client -> UpdateInstaller.download(release));
		waitFor(context, () -> UpdateInstaller.isReady(release), "good jar downloaded and checked: " + UpdateInstaller.message());
		context.waitTicks(3);
		context.takeScreenshot("update-fenster-bereit");

		// skipping hides the badge; the settings tab still shows the version
		context.runOnClient(client -> ModConfigManager.CONFIG.updateSkippedVersion = "9.9.9");
		assertTrue(ModUpdates.notifiable() == null, "skipped version is not announced");
		context.runOnClient(client -> ModConfigManager.CONFIG.updateSkippedVersion = "");

		context.setScreen(() -> new SettingsScreen(null).select("Allgemein", "Menü"));
		context.waitTicks(3);
		context.takeScreenshot("update-einstellungen");

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.getNormalPresetList().stream()
				.filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(settings::setWorldType))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(110);
			context.runOnClient(client -> client.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen("", false)));
			context.waitTicks(3);
			context.takeScreenshot("update-chat");
			// what clicking [Ansehen] does
			context.runOnClient(client -> client.player.connection.sendUnattendedCommand("hugosmpenhanced update", null));
			context.waitTicks(3);
			boolean opened = context.computeOnClient(client -> client.gui.screen() instanceof UpdateScreen);
			assertTrue(opened, "/hugosmpenhanced update opens the update screen");
			context.setScreen(() -> null);
		}
		// stays READY: the real helper swaps the test jar when this client closes
	}

	private static void check(ClientGameTestContext context, String url) {
		System.setProperty("hugosmpenhanced.updateUrl", url);
		// the first call may hand back a check that was already running against the real GitHub
		ModUpdates.check().join();
		ModUpdates.check().join();
		context.waitTicks(2);
	}

	private static void waitFor(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String what) {
		for (int i = 0; i < 400 && !condition.getAsBoolean(); i++) {
			context.waitTicks(1);
		}
		assertTrue(condition.getAsBoolean(), what);
	}

	/** The swap itself, on plain files: new name in, old jar gone, nothing left over. */
	private static void testHelper(Path dir) throws Exception {
		Files.createDirectories(dir);
		try (var stream = Files.list(dir)) {
			for (Path file : stream.toList()) {
				Files.delete(file);
			}
		}
		Path old = Files.writeString(dir.resolve("hugosmpenhanced-1.0.jar"), "old");
		Path staged = Files.writeString(dir.resolve("staged.jar"), "new");
		Path target = dir.resolve("hugosmpenhanced-2.0.jar");
		UpdateHelper.install(old, staged, target);
		assertTrue(!Files.exists(old) && Files.readString(target).equals("new") && !Files.exists(staged)
			&& !Files.exists(dir.resolve("hugosmpenhanced-2.0.jar.tmp")) && !Files.exists(dir.resolve("hugosmpenhanced-1.0.jar.old")),
			"helper swapped the jar cleanly");
		// same file name (re-released jar): replaced in place
		Path again = Files.writeString(dir.resolve("staged2.jar"), "newer");
		UpdateHelper.install(target, again, target);
		assertTrue(Files.readString(target).equals("newer"), "helper replaced a jar of the same name");
	}

	private static String releases(String base, String version, byte[] jar) throws Exception {
		String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
		String body = CHANGELOG.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return "[" + release("10.0.0-beta.1", true, false, base, null, "")
			+ "," + release(version, false, false, base, "{\"name\":\"hugosmpenhanced-" + version + ".jar\",\"size\":" + jar.length
				+ ",\"digest\":\"sha256:" + sha + "\",\"browser_download_url\":\"" + base + "/dl/hugosmpenhanced-" + version + ".jar\"},"
				+ "{\"name\":\"hugosmpenhanced-" + version + "-sources.jar\",\"size\":1,\"browser_download_url\":\"" + base + "/x\"}", body)
			+ "," + release("11.0.0", false, true, base, null, "")
			+ "," + release("0.1.0-alpha", false, false, base, null, "") + "]";
	}

	private static String release(String version, boolean prerelease, boolean draft, String base, String assets, String body) {
		return "{\"tag_name\":\"v" + version + "\",\"name\":\"v" + version + "\",\"draft\":" + draft + ",\"prerelease\":" + prerelease
			+ ",\"published_at\":\"2026-10-12T18:00:00Z\",\"html_url\":\"" + base + "/release\",\"body\":\"" + body
			+ "\",\"assets\":[" + (assets != null ? assets : "") + "]}";
	}

	/** A minimal mod jar; {@code withHelper} adds the helper class so the real helper can run from it. */
	private static byte[] jar(String version, String minecraft, boolean withHelper) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"hugosmpenhanced\",\"version\":\"" + version + "\",\"depends\":{\"minecraft\":\""
				+ minecraft + "\",\"fabricloader\":\">=0.19.5\",\"fabric-api\":\"*\",\"java\":\">=21\"}}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			if (withHelper) {
				String name = UpdateHelper.class.getName().replace('.', '/') + ".class";
				try (InputStream in = UpdateHelper.class.getClassLoader().getResourceAsStream(name)) {
					zip.putNextEntry(new ZipEntry(name));
					zip.write(in.readAllBytes());
					zip.closeEntry();
				}
			}
		}
		return bytes.toByteArray();
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
