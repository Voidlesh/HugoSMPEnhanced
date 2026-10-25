package com.hugosmpenhanced.client.feature.mainmenu;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfig;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.quickjoin.QuickJoinAction;
import com.hugosmpenhanced.client.feature.update.UpdateBadge;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.mixin.ScreenAccessor;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The mod's title screen: the HugoSMP logo with a configurable splash text, in a classic centred
 * layout or with the buttons in a side panel and a live server card. Replaces the vanilla title
 * screen while enabled (see {@code MinecraftTitleScreenMixin}). It extends {@link TitleScreen} so the
 * game and other mods still treat it as the title screen, but draws and lays out everything itself.
 */
public class HugoTitleScreen extends TitleScreen implements StyledScreen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "textures/gui/title/logo.png");
	private static final int LOGO_TEXTURE_WIDTH = 622;
	private static final int LOGO_TEXTURE_HEIGHT = 318;
	private static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";
	private static final String SERVER_ADDRESS = "hugosmp.net";
	private static final int PANEL_FILL = 0xBE0A0A0A;
	private static final int PANEL_BORDER = 0xFF555555;
	private static final int CARD_WIDTH = 170;
	private static final int CARD_HEIGHT = 74;

	private final String splash;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private ServerData server;
	private volatile boolean serverFailed;
	private long pingStart;
	private boolean relayoutPending;
	private boolean relayingOut;

	private MainMenuLayout layout;
	private int logoX;
	private int logoY;
	private int logoWidth;
	private int logoHeight;
	private int panelWidth;
	private boolean showCard;
	private int cardX;
	private int cardY;

	public HugoTitleScreen() {
		super();
		this.splash = pickSplash(ModConfigManager.CONFIG.splashTexts);
	}

	private static String pickSplash(List<String> texts) {
		List<String> usable = texts == null ? List.of() : texts.stream().filter(s -> s != null && !s.isBlank()).toList();
		if (usable.isEmpty()) {
			return "";
		}
		return usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
	}

	@Override
	protected void init() {
		// other mods (Mod Menu) move title screen buttons after init, so lay out once more on the first frame
		this.relayoutPending = !this.relayingOut;
		ModConfig config = ModConfigManager.CONFIG;
		this.layout = config.mainMenuLayout;
		boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
		boolean join = config.mainMenuButtonEnabled;

		if (this.layout == MainMenuLayout.SIDEBAR) {
			initSidebar(config, join, modMenu);
		} else {
			initClassic(join, modMenu);
		}

		String switchLabel = "⇄ " + this.layout.next().label();
		int switchWidth = this.font.width(switchLabel) + 12;
		this.addRenderableWidget(new MenuButton(this.width - switchWidth - 4, 4, switchWidth, 14,
				Component.literal(switchLabel), false, false, b -> {
					config.mainMenuLayout = this.layout.next();
					ModConfigManager.save();
					this.rebuildWidgets();
				}));

		UpdateBadge badge = this.layout == MainMenuLayout.SIDEBAR
				? UpdateBadge.create(this, this.panelWidth + 8, 4)
				: UpdateBadge.create(this, 4, 4);
		if (badge != null) {
			this.addRenderableWidget(badge);
		}
	}

	private boolean versionFits() {
		return this.font.width(versionText()) + this.font.width(COPYRIGHT) + 12 <= this.width;
	}

	private static String versionText() {
		return "HugoSMP Enhanced " + modVersion() + " - Minecraft " + SharedConstants.getCurrentVersion().name();
	}

	private void initClassic(boolean join, boolean modMenu) {
		int rows = 2 + (join ? 1 : 0) + (modMenu ? 1 : 0);
		int buttonsHeight = rows * 24 + 12 + 20;
		this.logoWidth = Math.min(200, this.width - 40);
		this.logoHeight = this.logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH;
		int room = this.height - 24 - buttonsHeight - 10;
		if (this.logoHeight > room - 10) {
			this.logoHeight = Math.max(30, room - 10);
			this.logoWidth = this.logoHeight * LOGO_TEXTURE_WIDTH / LOGO_TEXTURE_HEIGHT;
		}
		this.logoX = (this.width - this.logoWidth) / 2;
		this.logoY = Math.max(4, Math.min(12, room - this.logoHeight));

		int buttonWidth = 200;
		int x = (this.width - buttonWidth) / 2;
		int y = this.logoY + this.logoHeight + 10;
		y = addMainButtons(x, y, buttonWidth, join, modMenu, false);

		y += 8;
		int half = (buttonWidth - 4) / 2;
		this.addRenderableWidget(new MenuButton(x, y, half, 20, Component.translatable("menu.options"), false, false,
				b -> this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options))));
		this.addRenderableWidget(new MenuButton(x + half + 4, y, half, 20, Component.translatable("menu.quit"), false, false,
				b -> this.minecraft.stop()));
		var language = CommonButtons.language(20, b -> this.minecraft.setScreenAndShow(
				new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), true);
		language.setPosition(x - 24, y);
		this.addRenderableWidget(language);
		var accessibility = CommonButtons.accessibility(20, b -> this.minecraft.setScreenAndShow(
				new AccessibilityOptionsScreen(this, this.minecraft.options)), true);
		accessibility.setPosition(x + buttonWidth + 4, y);
		this.addRenderableWidget(accessibility);
		this.showCard = false;
	}

	private void initSidebar(ModConfig config, boolean join, boolean modMenu) {
		this.panelWidth = Math.min(220, Math.max(160, this.width / 3));
		this.logoWidth = this.panelWidth - 60;
		this.logoHeight = this.logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH;
		this.logoX = (this.panelWidth - this.logoWidth) / 2;
		this.logoY = 12;

		int x = 16;
		int buttonWidth = this.panelWidth - 32;
		int y = this.logoY + this.logoHeight + 12;
		y = addMainButtons(x, y, buttonWidth, join, modMenu, true);
		y += 8;
		this.addRenderableWidget(new MenuButton(x, y, buttonWidth, 20, Component.translatable("menu.options"), true, false,
				b -> this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options))));
		y += 24;
		this.addRenderableWidget(new MenuButton(x, y, buttonWidth, 20, Component.translatable("menu.quit"), true, false,
				b -> this.minecraft.stop()));

		this.showCard = config.mainMenuServerCard && this.width - this.panelWidth >= CARD_WIDTH + 24;
		if (this.showCard) {
			this.cardX = this.width - CARD_WIDTH - 12;
			this.cardY = 24;
			this.addRenderableWidget(new MenuButton(this.cardX + 8, this.cardY + CARD_HEIGHT - 24, CARD_WIDTH - 16, 16,
					Component.literal("Beitreten"), false, false, b -> QuickJoinAction.join(this)));
			startPing();
		}
	}

	private int addMainButtons(int x, int y, int width, boolean join, boolean modMenu, boolean left) {
		if (join && left) {
			this.addRenderableWidget(new MenuButton(x, y, width, 20, Component.literal("HugoSMP.net beitreten"), true, true,
					b -> QuickJoinAction.join(this)));
			y += 24;
		}
		this.addRenderableWidget(new MenuButton(x, y, width, 20, Component.translatable("menu.singleplayer"), left, false,
				b -> this.minecraft.setScreenAndShow(new SelectWorldScreen(this))));
		y += 24;
		this.addRenderableWidget(new MenuButton(x, y, width, 20, Component.translatable("menu.multiplayer"), left, false,
				b -> this.minecraft.setScreenAndShow(this.minecraft.options.skipMultiplayerWarning
						? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
		y += 24;
		if (join && !left) {
			this.addRenderableWidget(new MenuButton(x, y, width, 20, Component.literal("HugoSMP.net beitreten"), false, true,
					b -> QuickJoinAction.join(this)));
			y += 24;
		}
		if (modMenu) {
			this.addRenderableWidget(new MenuButton(x, y, width, 20, Component.literal("Mods"), left, false,
					b -> ModMenuLink.open(this)));
			y += 24;
		}
		return y;
	}

	private void startPing() {
		if (this.server != null) {
			return;
		}
		ServerData data = new ServerData("HugoSMP", SERVER_ADDRESS, ServerData.Type.OTHER);
		data.ping = -2L;
		this.server = data;
		this.pingStart = Util.getMillis();
		boolean nativeTransport = this.minecraft.options.useNativeTransport();
		Util.nonCriticalIoPool().execute(() -> {
			try {
				this.pinger.pingServer(data, () -> {
				}, () -> {
				}, EventLoopGroupHolder.remote(nativeTransport));
			} catch (Exception e) {
				this.serverFailed = true;
			}
		});
	}

	@Override
	public void tick() {
		this.pinger.tick();
	}

	@Override
	public void removed() {
		this.pinger.removeAll();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		//? if modern
		this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);
		//? else
		this.renderPanorama(graphics, partialTick);
		//? end
		if (this.layout == MainMenuLayout.SIDEBAR) {
			graphics.fill(0, 0, this.panelWidth, this.height, PANEL_FILL);
			graphics.fill(this.panelWidth, 0, this.panelWidth + 1, this.height, PANEL_BORDER);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (this.relayoutPending) {
			this.relayoutPending = false;
			this.relayingOut = true;
			this.rebuildWidgets();
			this.relayingOut = false;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, this.logoX, this.logoY, 0, 0, this.logoWidth, this.logoHeight,
				LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);
		if (this.showCard) {
			renderServerCard(graphics);
		}
		// TitleScreen.render would draw the vanilla logo and splash, so render the widgets directly
		for (Renderable renderable : ((ScreenAccessor) this).hugosmpenhanced$getRenderables()) {
			renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}
		renderSplash(graphics);

		String version = versionText();
		int copyrightWidth = this.font.width(COPYRIGHT);
		if (this.versionFits()) {
			graphics.text(this.font, version, 2, this.height - 10, 0xFFFFFFFF);
		} else if (this.layout == MainMenuLayout.SIDEBAR) {
			graphics.text(this.font, version, this.width - this.font.width(version) - 2, this.height - 20, 0xFFFFFFFF);
		} else {
			graphics.text(this.font, version, 2, this.height - 20, 0xFFFFFFFF);
		}
		graphics.text(this.font, COPYRIGHT, this.width - copyrightWidth - 2, this.height - 10, 0xFFFFFFFF);
	}

	private void renderSplash(GuiGraphicsExtractor graphics) {
		ModConfig config = ModConfigManager.CONFIG;
		if (this.splash.isEmpty()) {
			return;
		}
		float x;
		float y;
		if (this.layout == MainMenuLayout.SIDEBAR) {
			x = this.logoX + this.logoWidth * 0.78f + 64;
			y = this.logoY + this.logoHeight * 0.62f;
		} else {
			x = this.logoX + this.logoWidth * 0.78f + 66;
			y = this.logoY + this.logoHeight * 0.66f;
		}
		long millis = Util.getMillis();
		SplashAnimation animation = config.splashAnimation;
		float angle = -20.0f;
		float scale = 1.8f;
		if (animation == SplashAnimation.PULSE || animation == SplashAnimation.RAINBOW) {
			scale -= Mth.abs(Mth.sin((millis % 1000L) / 1000.0f * Mth.TWO_PI)) * 0.1f;
		}
		if (animation == SplashAnimation.WOBBLE) {
			angle += Mth.sin((millis % 2000L) / 2000.0f * Mth.TWO_PI) * 6.0f;
		}
		scale = scale * 100.0f / (this.font.width(this.splash) + 32);
		int color = config.splashColor.argb();
		if (animation == SplashAnimation.RAINBOW) {
			color = 0xFF000000 | Mth.hsvToRgb((millis % 3000L) / 3000.0f, 0.75f, 1.0f);
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		//? if modern
		pose.rotate(angle * Mth.DEG_TO_RAD);
		//? else
		pose.rotate(angle * Mth.DEG_TO_RAD);
		//? end
		pose.scale(scale, scale);
		graphics.centeredText(this.font, this.splash, 0, -8, color);
		pose.popMatrix();
	}

	private void renderServerCard(GuiGraphicsExtractor graphics) {
		int x = this.cardX;
		int y = this.cardY;
		HugoStyle.drawBox(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, HugoStyle.State.NORMAL);
		graphics.text(this.font, "HugoSMP.net", x + 8, y + 8, 0xFFFFFFFF);

		ServerData data = this.server;
		String status;
		int statusColor;
		String players = "";
		String ping = "";
		if (data != null && data.players != null && data.ping >= 0) {
			status = "Online";
			statusColor = 0xFF55DC55;
			players = data.players.online() + " / " + data.players.max() + " Spieler";
			ping = "Ping " + data.ping + " ms";
		} else if (this.serverFailed || (data != null && data.ping == -1L) || Util.getMillis() - this.pingStart > 10_000L) {
			status = "Offline";
			statusColor = 0xFFFF5555;
			players = "Server nicht erreichbar";
		} else {
			status = "Verbinde...";
			statusColor = 0xFFAAAAAA;
		}
		int statusX = x + CARD_WIDTH - 8 - 6 - 4 - this.font.width(status);
		graphics.text(this.font, status, statusX, y + 8, statusColor);
		graphics.fill(x + CARD_WIDTH - 14, y + 9, x + CARD_WIDTH - 8, y + 15, statusColor);
		graphics.text(this.font, players, x + 8, y + 24, 0xFFBEBEBE);
		graphics.text(this.font, ping, x + 8, y + 36, 0xFF969696);
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(HugoSmpEnhancedClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}
}
