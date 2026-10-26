package com.hugosmpenhanced.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.fakeclan.FakeClan;
import com.hugosmpenhanced.client.feature.fakeclan.FakeClanRules;
import com.hugosmpenhanced.client.feature.fakeclan.RemoteClanLists;
import com.sun.net.httpserver.HttpServer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The remote clan lists against a local server: a good answer, merging with the config lists, and that
 * HTTP errors, broken JSON, a wrong shape, an empty answer and a dead address all leave the stored
 * lists alone. Also that the lists survive a restart (cache file).
 */
public class RemoteClanListsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		AtomicReference<Integer> status = new AtomicReference<>(200);
		AtomicReference<String> body = new AtomicReference<>();
		HttpServer server;
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		server.createContext("/v1/fake-clans", exchange -> {
			byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/fake-clans";
		try {
			ModConfigManager.CONFIG.fakeClanReal = new ArrayList<>(List.of("Nova"));
			ModConfigManager.CONFIG.fakeClanFake = new ArrayList<>(List.of("Hexen"));
			RemoteClanLists.resetForTest(false);

			body.set("{\"ok\":true,\"updatedAt\":1,\"real\":[\"Vanguard\",\" nova \",\"\"],\"fake\":[\"Schatten\",\"hexen\",5]}");
			check(RemoteClanLists.fetch(url), "a good answer loads: " + RemoteClanLists.lastError());
			check(RemoteClanLists.lists().real().equals(List.of("Vanguard", "nova")), "real list is cleaned: " + RemoteClanLists.lists().real());
			check(RemoteClanLists.lists().fake().equals(List.of("Schatten", "hexen")), "fake list is cleaned: " + RemoteClanLists.lists().fake());
			check(FakeClan.realClans().equals(List.of("Nova", "Vanguard")), "real = config + remote, no duplicate: " + FakeClan.realClans());
			check(FakeClan.fakeClans().equals(List.of("Hexen", "Schatten")), "fake = config + remote: " + FakeClan.fakeClans());
			check(FakeClanRules.check("Schatten", FakeClan.realClans(), FakeClan.fakeClans()) != null, "a remote fake is flagged");
			check(FakeClanRules.isReal("Vanguard", FakeClan.realClans()), "a remote real clan is trusted");

			String before = RemoteClanLists.lists().toString();
			status.set(500);
			check(!RemoteClanLists.fetch(url) && RemoteClanLists.lastError().contains("500"), "HTTP 500 fails");
			status.set(200);
			for (String bad : new String[] {"not json", "[]", "{}", "{\"ok\":false}", "{\"ok\":true,\"real\":[\"A\"]}",
				"{\"ok\":true,\"real\":[],\"fake\":[]}", "{\"ok\":true,\"real\":\"x\",\"fake\":[]}", ""}) {
				body.set(bad);
				check(!RemoteClanLists.fetch(url), "bad answer is refused: " + bad);
				check(RemoteClanLists.lists().toString().equals(before), "bad answer leaves the lists alone: " + bad);
			}
			check(RemoteClanLists.fetch("http://127.0.0.1:1/nothing") == false, "a dead address fails");
			check(RemoteClanLists.lists().toString().equals(before), "offline leaves the lists alone");

			RemoteClanLists.resetForTest(true);
			check(RemoteClanLists.lists() != null && RemoteClanLists.lists().real().contains("Vanguard"), "lists come back from the cache file");

			body.set("{\"ok\":true,\"real\":[\"Nova\"],\"fake\":[]}");
			check(RemoteClanLists.fetch(url) && RemoteClanLists.lists().real().equals(List.of("Nova")), "a changed answer replaces the old lists");
			check(!FakeClan.fakeClans().contains("Schatten"), "a removed remote fake is gone");

			RemoteClanLists.resetForTest(false);
			check(FakeClan.realClans().equals(List.of("Nova")), "without remote lists the config lists stand alone");
		} finally {
			server.stop(0);
			RemoteClanLists.resetForTest(false);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
