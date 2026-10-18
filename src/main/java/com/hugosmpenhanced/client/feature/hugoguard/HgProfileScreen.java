package com.hugosmpenhanced.client.feature.hugoguard;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The {@code /hg <name>} page: the player's skin (drag to turn it) next to their HugoGuard status,
 * reputation, report count and newest report, with a button to the full profile on hugoguard.net.
 */
public class HgProfileScreen extends Screen implements StyledScreen {
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 190;
	private static final int SKIN_WIDTH = 80;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final Screen parent;
	private final String requestedName;

	private volatile State state = State.LOADING;
	private volatile JsonObject data;
	/** Read by the skin widget every frame; starts as the default skin until the real one is loaded. */
	private volatile Supplier<PlayerSkin> skin;
	private Button profileButton;

	private enum State {
		LOADING, LOADED, NOT_FOUND, FAILED
	}

	public HgProfileScreen(Screen parent, String name) {
		super(Component.literal("HugoGuard: " + name));
		this.parent = parent;
		this.requestedName = name;
		this.skin = DefaultPlayerSkin::getDefaultSkin;
		CompletableFuture.supplyAsync(() -> {
			try {
				return HugoGuard.fetchPlayer(name);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, Util.ioPool()).whenComplete((body, error) -> {
			if (error != null) {
				HugoSmpEnhancedClient.LOGGER.warn("HugoGuard lookup for {} failed: {}", name, error.toString());
				this.state = State.FAILED;
			} else if (body == null || !body.has("player") || !body.get("player").isJsonObject()) {
				this.state = State.NOT_FOUND;
			} else {
				this.data = body;
				this.state = State.LOADED;
				this.loadSkin(body.getAsJsonObject("player"));
			}
		});
	}

	/** Uses the online player's profile if they are on the server, otherwise asks Mojang for it. */
	private void loadSkin(JsonObject player) {
		UUID uuid = parseUuid(string(player, "uuid"));
		if (uuid == null) {
			return;
		}
		this.skin = () -> DefaultPlayerSkin.get(uuid);
		var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
		PlayerInfo online = connection != null ? connection.getPlayerInfo(uuid) : null;
		if (online != null) {
			this.skin = online::getSkin;
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			ProfileResult result = net.minecraft.client.Minecraft.getInstance().services().sessionService().fetchProfile(uuid, false);
			return result != null ? result.profile() : new GameProfile(uuid, string(player, "username"));
		}, Util.ioPool()).thenAccept(profile ->
			this.skin = net.minecraft.client.Minecraft.getInstance().getSkinManager().createLookup(profile, false));
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;
		this.addRenderableWidget(new PlayerSkinWidget(SKIN_WIDTH - 10, PANEL_HEIGHT - 30,
			this.minecraft.getEntityModels(), () -> this.skin.get()))
			.setPosition(left + 8, top + 8);

		this.profileButton = this.addRenderableWidget(Button.builder(Component.literal("Profil auf hugoguard.net"), b -> {
			JsonObject player = this.player();
			if (player != null && string(player, "uuid") != null) {
				Util.getPlatform().openUri("https://hugoguard.net/players/" + string(player, "uuid").replace("-", ""));
			}
		}).bounds(left + SKIN_WIDTH + 4, top + PANEL_HEIGHT - 26, 150, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
			.bounds(left + PANEL_WIDTH - 8 - 70, top + PANEL_HEIGHT - 26, 70, 20).build());
	}

	@Override
	public void tick() {
		this.profileButton.active = this.state == State.LOADED;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;
		JsonObject player = this.player();
		String status = player != null ? string(player, "status") : null;
		int accent = accent(status);

		HugoStyle.drawBox(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, HugoStyle.State.NORMAL);
		graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 3, accent);
		graphics.fill(left + 4, top + 6, left + SKIN_WIDTH, top + PANEL_HEIGHT - 4, 0x60000000);
		super.render(graphics, mouseX, mouseY, partialTick);

		int x = left + SKIN_WIDTH + 8;
		int y = top + 10;
		String name = player != null && string(player, "username") != null ? string(player, "username") : this.requestedName;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(1.6f, 1.6f);
		graphics.drawString(this.font, name, 0, 0, 0xFFFFFFFF);
		graphics.pose().popMatrix();
		y += 20;

		switch (this.state) {
			case LOADING -> {
				graphics.drawString(this.font, "Lade HugoGuard-Daten…", x, y, 0xFFAAAAAA);
				return;
			}
			case NOT_FOUND -> {
				graphics.drawString(this.font, "HugoGuard kennt diesen Spieler nicht.", x, y, 0xFFAAAAAA);
				return;
			}
			case FAILED -> {
				graphics.drawString(this.font, "HugoGuard ist gerade nicht erreichbar.", x, y, 0xFFFF6060);
				return;
			}
			default -> {
			}
		}

		// Status badge, plus the admin label HugoGuard shows (e.g. a role), if any.
		String badge = switch (status == null ? "" : status) {
			case "SCAMMER" -> "⚠ SCAMMER";
			case "TRUSTED" -> "✔ TRUSTED";
			default -> "Nicht gelistet";
		};
		int badgeWidth = this.font.width(badge) + 10;
		graphics.fill(x, y - 2, x + badgeWidth, y + 11, (accent & 0x00FFFFFF) | 0x50000000);
		HugoStyle.drawOutline(graphics, x, y - 2, badgeWidth, 13, accent);
		graphics.drawString(this.font, badge, x + 5, y + 1, accent);
		String label = string(player, "adminLabel");
		if (label != null && !label.isBlank()) {
			graphics.drawString(this.font, label, x + badgeWidth + 6, y + 1, 0xFFDDDDDD);
		}
		y += 20;

		int repUp = integer(player, "repUp");
		int repDown = integer(player, "repDown");
		int repScore = integer(player, "repScore");
		int scoreColor = repScore > 0 ? 0xFF55FF55 : repScore < 0 ? 0xFFFF5555 : 0xFFDDDDDD;
		y = this.row(graphics, x, y, "Reputation", (repScore > 0 ? "+" : "") + repScore, scoreColor);
		graphics.drawString(this.font, "▲ " + repUp, x + 150, y - 12, 0xFF55FF55);
		graphics.drawString(this.font, "▼ " + repDown, x + 185, y - 12, 0xFFFF5555);
		int reports = integer(player, "approvedReportCount");
		y = this.row(graphics, x, y, "Reports", String.valueOf(reports), reports > 0 ? 0xFFFF5555 : 0xFFDDDDDD);
		String last = date(string(player, "lastReportedAt"));
		if (last != null) {
			y = this.row(graphics, x, y, "Letzter Report", last, 0xFFDDDDDD);
		}

		// The newest report's description, cut to what fits above the buttons.
		String newest = this.newestReport();
		if (newest != null) {
			y += 4;
			int maxLines = Math.max(0, (top + PANEL_HEIGHT - 32 - y) / 10);
			List<FormattedCharSequence> lines = this.font.split(Component.literal("„" + newest + "“"), PANEL_WIDTH - SKIN_WIDTH - 20);
			for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
				graphics.drawString(this.font, lines.get(i), x, y, 0xFFBBBBBB);
				y += 10;
			}
		}
	}

	private int row(GuiGraphics graphics, int x, int y, String label, String value, int color) {
		graphics.drawString(this.font, label, x, y, 0xFF888888);
		graphics.drawString(this.font, value, x + 90, y, color);
		return y + 12;
	}

	private JsonObject player() {
		JsonObject body = this.data;
		return body != null ? body.getAsJsonObject("player") : null;
	}

	private String newestReport() {
		JsonObject body = this.data;
		if (body == null || !body.has("reports") || !body.get("reports").isJsonArray()) {
			return null;
		}
		JsonArray reports = body.getAsJsonArray("reports");
		for (JsonElement report : reports) {
			if (report.isJsonObject()) {
				String description = string(report.getAsJsonObject(), "description");
				if (description != null && !description.isBlank()) {
					return description.strip().replaceAll("\\s+", " ");
				}
			}
		}
		return null;
	}

	private static int accent(String status) {
		if ("SCAMMER".equals(status)) {
			return 0xFFFF4040;
		}
		if ("TRUSTED".equals(status)) {
			return 0xFF40E060;
		}
		return 0xFF888888;
	}

	private static String date(String iso) {
		if (iso == null) {
			return null;
		}
		try {
			LocalDate date = OffsetDateTime.parse(iso).toLocalDate();
			return DATE.format(date);
		} catch (Exception e) {
			return null;
		}
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.replace("-", "").length() != 32) {
			return null;
		}
		String hex = raw.replace("-", "");
		return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
	}

	private static String string(JsonObject object, String key) {
		if (object == null) {
			return null;
		}
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static int integer(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
	}
}
