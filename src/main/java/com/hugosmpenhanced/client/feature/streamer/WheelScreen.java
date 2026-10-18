package com.hugosmpenhanced.client.feature.streamer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.glfw.GLFW;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.hugosmpenhanced.client.settings.Theme;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The wheel of fortune. The winner is drawn with a secure random number when the spin starts; the
 * wheel only shows the way there: it speeds off, ticks past every name and slowly runs out on the
 * winner under the pointer.
 */
public class WheelScreen extends Screen implements StyledScreen {
	private static final int[] COLORS = {0xFFE24848, 0xFFF0AA32, 0xFF5ABE6E, 0xFF50A0EB, 0xFFA06EEB, 0xFFE66EAA};
	private static final int TEX = 512;
	private static final int MAX_NAMES = 40;
	private static final double POINTER = -Math.PI / 2;
	private static final Identifier WHEEL = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "streamer/wheel");
	private static final Identifier WHEEL_WIN = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "streamer/wheel_win");
	private static final Identifier POINTER_TEX = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "streamer/pointer");
	private static boolean pointerMade;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final Confetti confetti = new Confetti();
	private List<Giveaway.Participant> wheel = List.of();
	private String wheelKey = "";
	private boolean hasWinTexture;

	private long openedAt;
	private double angle;
	private double spinFrom;
	private double spinTo;
	private long spinStart;
	private long spinMs;
	private boolean spinning;
	private Giveaway.Participant result;
	private long resultAt;
	private int lastIndex = -1;
	private long lastTickSound;
	private long lastFrame;

	public WheelScreen(Screen parent) {
		super(Component.literal("Glücksrad"));
		this.parent = parent;
		this.openedAt = System.currentTimeMillis();
		this.lastFrame = this.openedAt;
	}

	@Override
	protected void init() {
		if (this.wheel.isEmpty()) {
			this.refreshWheel();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float fade = StreamerUi.easeOut(StreamerUi.progress(this.openedAt, 300));
		graphics.fillGradient(0, 0, this.width, this.height, StreamerUi.alpha(0x40000000, fade), StreamerUi.alpha(0x80000000, fade));
	}

	@Override
	public void removed() {
		var textures = this.minecraft.getTextureManager();
		textures.release(WHEEL);
		textures.release(WHEEL_WIN);
		this.wheelKey = "";
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	// ------------------------------------------------------------------ spin

	@Override
	public void tick() {
		// Starts by itself a moment after opening, so one click on "Glücksrad drehen" is enough.
		if (!this.spinning && this.result == null && System.currentTimeMillis() - this.openedAt > 600 && this.wheel.size() >= 2) {
			this.spin();
		}
		if (!this.spinning && this.result == null) {
			this.refreshWheel();
		}
	}

	private void spin() {
		this.refreshWheel();
		Giveaway.Participant winner = Giveaway.draw();
		int index = winner == null ? -1 : this.indexOf(winner);
		if (index < 0) {
			return;
		}
		int n = this.wheel.size();
		double seg = Math.PI * 2 / n;
		// somewhere inside the segment, not on its edge, so nobody wonders who it hit
		double inside = 0.18 + ThreadLocalRandom.current().nextDouble() * 0.64;
		double base = POINTER - (index + inside) * seg;
		double start = this.angle;
		double delta = Mth.positiveModulo(base - start, Math.PI * 2);
		int seconds = Mth.clamp(ModConfigManager.CONFIG.streamerWheelSeconds, 3, 20);
		int turns = Math.max(3, Math.round(seconds * 0.85f));
		this.spinFrom = start;
		this.spinTo = start + turns * Math.PI * 2 + delta;
		this.spinStart = System.currentTimeMillis();
		this.spinMs = seconds * 1000L;
		this.spinning = true;
		this.result = null;
		this.makeWheelTexture(WHEEL_WIN, index);
		this.hasWinTexture = true;
		StreamerFeature.play(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.4f);
	}

	private int indexOf(Giveaway.Participant participant) {
		for (int i = 0; i < this.wheel.size(); i++) {
			if (this.wheel.get(i).key().equals(participant.key())) {
				return i;
			}
		}
		return -1;
	}

	private void updateSpin() {
		long now = System.currentTimeMillis();
		if (this.spinning) {
			float t = Mth.clamp((now - this.spinStart) / (float) this.spinMs, 0f, 1f);
			// full speed at once, then a long soft run-out
			double p = 1 - Math.pow(1 - t, 4);
			this.angle = this.spinFrom + (this.spinTo - this.spinFrom) * p;
			int index = this.indexAtPointer();
			if (index != this.lastIndex) {
				this.lastIndex = index;
				if (now - this.lastTickSound > 45) {
					this.lastTickSound = now;
					StreamerFeature.play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f + 0.4f * (1 - t), 0.35f);
				}
			}
			if (t >= 1) {
				this.spinning = false;
				this.angle = this.spinTo;
				this.result = Giveaway.winner();
				this.resultAt = now;
				StreamerFeature.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.6f);
				int cx = this.wheelX();
				int cy = this.wheelY();
				int r = this.radius();
				this.confetti.burst(cx, cy - r, 70, 260);
				this.confetti.rain(0, this.width, -10, 90);
			}
		} else if (this.result == null) {
			// idle: turn slowly so the wheel looks alive before the spin
			float dt = Math.min(0.1f, (now - this.lastFrame) / 1000f);
			this.angle += dt * 0.25;
		}
		this.lastFrame = now;
	}

	private int indexAtPointer() {
		int n = Math.max(1, this.wheel.size());
		double local = Mth.positiveModulo(POINTER - this.angle, Math.PI * 2);
		return Mth.clamp((int) (local / (Math.PI * 2 / n)), 0, n - 1);
	}

	private void respinWithout() {
		if (this.result == null) {
			return;
		}
		Giveaway.remove(this.result);
		Giveaway.clearWinner();
		this.result = null;
		this.hasWinTexture = false;
		this.wheelKey = "";
		if (Giveaway.count() >= 2) {
			this.spin();
		} else {
			this.refreshWheel();
		}
	}

	// ------------------------------------------------------------------ textures

	private void refreshWheel() {
		List<Giveaway.Participant> people = Giveaway.participants();
		StringBuilder key = new StringBuilder();
		for (Giveaway.Participant p : people) {
			key.append(p.key()).append('|');
		}
		if (!key.toString().equals(this.wheelKey)) {
			this.wheelKey = key.toString();
			this.wheel = List.copyOf(people);
			this.makeWheelTexture(WHEEL, -1);
		}
	}

	/** Draws the wheel into a texture: segments with dark seams, a gold rim with pegs and a hub. */
	private void makeWheelTexture(Identifier id, int highlight) {
		int n = Math.max(1, this.wheel.size());
		double seg = Math.PI * 2 / n;
		NativeImage image = new NativeImage(TEX, TEX, false);
		double c = TEX / 2.0;
		double unit = c; // pixels per radius unit
		int samples = 2;
		for (int py = 0; py < TEX; py++) {
			for (int px = 0; px < TEX; px++) {
				double a = 0;
				double r = 0;
				double g = 0;
				double b = 0;
				for (int sy = 0; sy < samples; sy++) {
					for (int sx = 0; sx < samples; sx++) {
						double dx = (px + (sx + 0.5) / samples - c) / unit;
						double dy = (py + (sy + 0.5) / samples - c) / unit;
						double d = Math.sqrt(dx * dx + dy * dy);
						int color = this.sample(dx, dy, d, n, seg, highlight, unit);
						double ca = (color >>> 24) / 255.0;
						a += ca;
						r += ((color >> 16) & 0xFF) * ca;
						g += ((color >> 8) & 0xFF) * ca;
						b += (color & 0xFF) * ca;
					}
				}
				int count = samples * samples;
				int out = 0;
				if (a > 0) {
					out = ((int) Math.round(a / count * 255) << 24) | ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8)
						| (int) Math.round(b / a);
				}
				image.setPixel(px, py, out);
			}
		}
		var textures = this.minecraft.getTextureManager();
		textures.release(id);
		textures.register(id, StreamerUi.linear("streamer wheel", image));
	}

	private int sample(double dx, double dy, double d, int n, double seg, int highlight, double unit) {
		double px = 1 / unit;
		if (d > 1) {
			return 0;
		}
		if (d > 0.94) {
			// gold rim with a darker edge on both sides
			if (d > 0.985 || d < 0.95) {
				return 0xFF5A3C0A;
			}
			// pegs on every seam
			if (n <= 60) {
				double phi = Mth.positiveModulo(Math.atan2(dy, dx), Math.PI * 2);
				double nearest = Math.round(phi / seg) * seg;
				double ex = Math.cos(nearest) * 0.967;
				double ey = Math.sin(nearest) * 0.967;
				double pd = Math.hypot(dx - ex, dy - ey);
				if (pd < 0.014) {
					return 0xFFFFF4D6;
				}
			}
			return 0xFFFFC83D;
		}
		if (d < 0.16) {
			if (d > 0.145) {
				return 0xFFFFC83D;
			}
			return d > 0.135 ? 0xFF5A3C0A : 0xF0141414;
		}
		double phi = Mth.positiveModulo(Math.atan2(dy, dx), Math.PI * 2);
		int index = Math.min(n - 1, (int) (phi / seg));
		// seams between segments, about 2 pixels wide whatever the radius
		if (n > 1 && n <= 150) {
			double into = phi - index * seg;
			double edge = Math.min(into, seg - into) * d;
			if (edge < px * 1.1) {
				return 0xFF141414;
			}
		}
		int color = COLORS[index % COLORS.length];
		if (n % COLORS.length == 1 && index == n - 1) {
			color = COLORS[2]; // never two equal colours side by side where the circle closes
		}
		if (highlight >= 0) {
			color = index == highlight ? StreamerUi.mix(color, 0xFFFFFFFF, 0.25f) : StreamerUi.mix(color, 0xFF101010, 0.62f);
		}
		// a soft inner shade towards the hub gives the wheel some depth
		float shade = (float) Mth.clamp((0.94 - d) / 0.78, 0, 1);
		color = StreamerUi.mix(color, StreamerUi.mix(color, 0xFF000000, 0.18f), shade);
		return (0xE6 << 24) | (color & 0xFFFFFF);
	}

	private static Identifier pointer() {
		if (!pointerMade) {
			int w = 32;
			int h = 40;
			NativeImage image = new NativeImage(w, h, false);
			int s = 4;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int inner = 0;
					int outer = 0;
					for (int sy = 0; sy < s; sy++) {
						for (int sx = 0; sx < s; sx++) {
							double fx = x + (sx + 0.5) / s;
							double fy = y + (sy + 0.5) / s;
							if (inTriangle(fx, fy, 2, 2, 30, 2, 16, 38)) {
								outer++;
							}
							if (inTriangle(fx, fy, 5.5, 4.5, 26.5, 4.5, 16, 33)) {
								inner++;
							}
						}
					}
					int total = s * s;
					int alpha = outer * 255 / total;
					float t = inner / (float) total;
					int color = StreamerUi.mix(0xFF4A3008, y < 14 ? 0xFFFFDC6E : 0xFFFFC83D, t);
					int argb = (alpha << 24) | (color & 0xFFFFFF);
					image.setPixel(x, y, argb);
				}
			}
			net.minecraft.client.Minecraft.getInstance().getTextureManager().register(POINTER_TEX, StreamerUi.linear("streamer pointer", image));
			pointerMade = true;
		}
		return POINTER_TEX;
	}

	private static boolean inTriangle(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
		double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
		double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
		double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
		boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
		boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
		return !(neg && pos);
	}

	// ------------------------------------------------------------------ layout

	private int panelW() {
		return Mth.clamp(this.width / 3, 170, 220);
	}

	private int radius() {
		int byHeight = this.height / 2 - 30;
		int byWidth = (this.width - this.panelW() - 40) / 2;
		return Math.max(50, Math.min(byHeight, byWidth));
	}

	private int wheelX() {
		int total = this.radius() * 2 + 24 + this.panelW();
		return (this.width - total) / 2 + this.radius();
	}

	private int wheelY() {
		return this.height / 2 + 8;
	}

	// ------------------------------------------------------------------ render

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.hits.clear();
		this.updateSpin();
		long now = System.currentTimeMillis();
		int cx = this.wheelX();
		int cy = this.wheelY();
		int r = this.radius();
		float intro = StreamerUi.easeBack(StreamerUi.progress(this.openedAt, 450));

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(Math.max(0.01f, intro), Math.max(0.01f, intro));
		StreamerUi.glow(graphics, 0, 6, r + 26, r + 26, 0x70000000);
		if (this.result != null) {
			float pulse = 0.55f + 0.45f * (float) Math.sin((now - this.resultAt) / 180.0);
			StreamerUi.glow(graphics, 0, 0, r + 40, r + 40, StreamerUi.alpha(0x60FFC83D, pulse));
		}
		pose.pushMatrix();
		pose.rotate((float) this.angle);
		graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL, -r, -r, 0, 0, r * 2, r * 2, TEX, TEX, TEX, TEX, 0xFFFFFFFF);
		if (this.hasWinTexture && this.result != null) {
			float fade = StreamerUi.easeOut(StreamerUi.progress(this.resultAt, 500));
			graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL_WIN, -r, -r, 0, 0, r * 2, r * 2, TEX, TEX, TEX, TEX, StreamerUi.alpha(0xFFFFFFFF, fade));
		}
		pose.popMatrix();
		this.renderNames(graphics, r);
		// hub: the prize, else the brand mark
		int hub = Math.max(16, (int) (r * 0.2f));
		if (!Giveaway.prize().isEmpty()) {
			pose.pushMatrix();
			float s = hub / 16f;
			pose.scale(s, s);
			graphics.renderItem(Giveaway.prize(), -8, -8);
			pose.popMatrix();
		} else {
			Theme.icon(graphics, "brand", -hub / 2, -hub / 2, hub, 0xFFFFFFFF);
		}
		pose.popMatrix();

		// pointer on top, with a little kick at every seam it passes
		int pw = Math.max(16, r / 8);
		int ph = pw * 5 / 4;
		float kick = 0;
		if (this.spinning) {
			double seg = Math.PI * 2 / Math.max(1, this.wheel.size());
			double local = Mth.positiveModulo(POINTER - this.angle, seg) / seg;
			kick = (float) Math.max(0, 1 - local * 4) * 0.35f;
		}
		pose.pushMatrix();
		pose.translate(cx, cy - r + ph * 0.35f);
		pose.rotate(-kick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, pointer(), -pw / 2, -ph, 0, 0, pw, ph, 32, 40, 32, 40, 0xFFFFFFFF);
		pose.popMatrix();

		// current name under the pointer while it spins
		if (this.spinning && !this.wheel.isEmpty()) {
			Giveaway.Participant current = this.wheel.get(this.indexAtPointer());
			String label = current.name();
			int lw = this.font.width(label) + 14;
			int ly = cy - r - ph - 16;
			StreamerUi.glass(graphics, cx - lw / 2, ly, lw, 14, Theme.GOLD);
			graphics.drawCenteredString(this.font, label, cx, ly + 3, Theme.WHITE);
		}

		this.renderPanel(graphics, mouseX, mouseY, cx + r + 24, now);
		this.confetti.render(graphics);
	}

	private void renderNames(GuiGraphics graphics, int r) {
		int n = this.wheel.size();
		if (n == 0 || n > MAX_NAMES) {
			return;
		}
		double seg = Math.PI * 2 / n;
		float arc = (float) (r * 0.6 * seg);
		float scale = Mth.clamp(arc / 11f, 0.55f, Math.max(0.75f, r / 110f));
		float inner = r * 0.2f;
		float outer = r * 0.9f;
		int maxWidth = (int) ((outer - inner) / scale);
		var pose = graphics.pose();
		for (int i = 0; i < n; i++) {
			double mid = this.angle + (i + 0.5) * seg;
			boolean left = Math.cos(mid) < 0;
			String name = this.font.plainSubstrByWidth(this.wheel.get(i).name(), maxWidth);
			boolean dim = this.result != null && !this.wheel.get(i).key().equals(this.result.key());
			int color = dim ? 0xFF8C8C8C : Theme.WHITE;
			pose.pushMatrix();
			pose.rotate((float) (left ? mid + Math.PI : mid));
			pose.scale(scale, scale);
			int w = this.font.width(name);
			// names sit at the outer end so short and long names line up on the rim
			float x = left ? -outer / scale : outer / scale - w;
			graphics.drawString(this.font, name, (int) x, -4, color, true);
			pose.popMatrix();
		}
	}

	private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, int x, long now) {
		int w = this.panelW();
		int h = Math.min(this.height - 40, 230);
		int y = this.wheelY() - h / 2;
		float slide = StreamerUi.easeOut(StreamerUi.progress(this.openedAt + 150, 350));
		x += Math.round((1 - slide) * 30);
		StreamerUi.glass(graphics, x, y, w, h, StreamerUi.alpha(Theme.GOLD, 0.75f));
		String prize = Giveaway.prizeName().isBlank() ? (Giveaway.prize().isEmpty() ? "" : Giveaway.prize().getHoverName().getString())
			: Giveaway.prizeName();
		String head = prize.isEmpty() ? "GIVEAWAY" : "GIVEAWAY  ·  " + prize.toUpperCase(java.util.Locale.ROOT);
		graphics.drawString(this.font, this.font.plainSubstrByWidth(head, w - 34), x + 10, y + 10, Theme.GOLD, false);
		if (!Giveaway.prize().isEmpty()) {
			graphics.renderItem(Giveaway.prize(), x + w - 26, y + 6);
		}
		int iy = y + 30;
		int bx = x + 10;
		int bw = w - 20;
		Giveaway.Participant winner = this.result;
		if (winner == null) {
			graphics.drawString(this.font, this.spinning ? "Das Rad dreht…" : "Bereit", bx, iy, Theme.MUTED, false);
			String count = String.valueOf(this.wheel.size());
			StreamerUi.text(graphics, this.font, count, bx, iy + 14, 3f, Theme.WHITE, true);
			graphics.drawString(this.font, "Teilnehmer", bx + (int) (this.font.width(count) * 3) + 6, iy + 30, Theme.TEXT, false);
			int ig = Giveaway.count(Giveaway.Source.INGAME);
			int tw = Giveaway.count(Giveaway.Source.TWITCH);
			graphics.drawString(this.font, ig + " ingame  ·  " + tw + " über Twitch", bx, iy + 48, Theme.MUTED, false);
			graphics.drawString(this.font, "Zufall entscheidet beim Start,", bx, iy + 66, Theme.FAINT, false);
			graphics.drawString(this.font, "das Rad zeigt nur den Weg.", bx, iy + 76, Theme.FAINT, false);
			boolean can = !this.spinning && this.wheel.size() >= 2;
			StreamerUi.button(graphics, this.font, bx, y + h - 46, bw, 18, "Drehen", StreamerUi.Kind.GOLD, can, mouseX, mouseY);
			this.hit(bx, y + h - 46, bw, 18, () -> {
				if (!this.spinning && this.wheel.size() >= 2) {
					this.spin();
				}
			});
		} else {
			float pop = StreamerUi.easeBack(StreamerUi.progress(this.resultAt, 400));
			graphics.drawString(this.font, "Gewinner", bx, iy, Theme.MUTED, false);
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(bx, iy + 14);
			pose.scale(pop, pop);
			if (winner.source() == Giveaway.Source.TWITCH) {
				pose.pushMatrix();
				pose.scale(2, 2);
				StreamerUi.twitchIcon(graphics, 1, 1, StreamerUi.TWITCH);
				pose.popMatrix();
			} else {
				StreamerUi.face(graphics, winner.name(), 0, 0, 20);
			}
			float nameScale = Math.min(2f, (bw - 26f) / Math.max(1, this.font.width(winner.name())));
			StreamerUi.text(graphics, this.font, winner.name(), 26, 10 - 4 * nameScale, nameScale, Theme.WHITE, true);
			pose.popMatrix();
			String source = (winner.source() == Giveaway.Source.TWITCH ? "Twitch" : "Ingame") + "  ·  1 von " + this.wheel.size();
			graphics.drawString(this.font, source, bx, iy + 40, Theme.MUTED, false);
			String mc = winner.source() == Giveaway.Source.INGAME ? winner.name() : winner.mcName();
			int by = iy + 56;
			if (mc != null) {
				StreamerUi.button(graphics, this.font, bx, by, bw, 18, "TPA zu " + mc, StreamerUi.Kind.GOLD, true, mouseX, mouseY);
				this.hit(bx, by, bw, 18, () -> {
					StreamerFeature.command("tpa " + mc);
					this.minecraft.setScreen(null);
				});
				int half = (bw - 4) / 2;
				StreamerUi.button(graphics, this.font, bx, by + 22, half, 16, "Zu mir holen", StreamerUi.Kind.NORMAL, true, mouseX, mouseY);
				this.hit(bx, by + 22, half, 16, () -> StreamerFeature.command("tpahere " + mc));
				StreamerUi.button(graphics, this.font, bx + half + 4, by + 22, bw - half - 4, 16, "/msg senden", StreamerUi.Kind.NORMAL, true, mouseX, mouseY);
				String what = prize.isEmpty() ? "das Giveaway" : prize;
				this.hit(bx + half + 4, by + 22, bw - half - 4, 16, () -> StreamerFeature.command("msg " + mc + " Glückwunsch, du hast " + what + " gewonnen!"));
			} else {
				graphics.drawString(this.font, "Kein MC-Name bekannt.", bx, by, StreamerUi.LAVENDER, false);
				graphics.drawString(this.font, "Im Twitch-Chat schreiben:", bx, by + 11, Theme.MUTED, false);
				graphics.drawString(this.font, "!join <MC-Name>", bx, by + 22, Theme.TEXT, false);
				graphics.drawString(this.font, "Dann geht TPA hier.", bx, by + 33, Theme.MUTED, false);
				// the name can still arrive later through the chat
				Giveaway.Participant updated = this.findCurrent(winner);
				if (updated != null && updated.mcName() != null) {
					this.result = updated;
				}
			}
			boolean again = this.wheel.size() > 2;
			boolean overAgain = StreamerUi.button(graphics, this.font, bx, y + h - 46, bw, 18, "Nochmal drehen", StreamerUi.Kind.NORMAL, again, mouseX, mouseY);
			if (overAgain) {
				graphics.setTooltipForNextFrame(this.font, Component.literal(winner.name() + " fliegt raus, dann dreht das Rad neu"), mouseX, mouseY);
			}
			this.hit(bx, y + h - 46, bw, 18, () -> {
				if (this.wheel.size() > 2) {
					this.respinWithout();
				}
			});
		}
		StreamerUi.button(graphics, this.font, bx, y + h - 24, bw, 16, "Zurück zum Panel", StreamerUi.Kind.GHOST, true, mouseX, mouseY);
		this.hit(bx, y + h - 24, bw, 16, this::onClose);
	}

	private Giveaway.Participant findCurrent(Giveaway.Participant participant) {
		for (Giveaway.Participant p : Giveaway.participants()) {
			if (p.key().equals(participant.key())) {
				return p;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ input

	private void hit(int x, int y, int w, int h, Runnable action) {
		this.hits.add(new Hit(x, y, w, h, action));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (int i = this.hits.size() - 1; i >= 0; i--) {
				Hit hit = this.hits.get(i);
				if (StreamerUi.inside(event.x(), event.y(), hit.x, hit.y, hit.w, hit.h)) {
					hit.action.run();
					StreamerFeature.play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_SPACE && !this.spinning) {
			if (this.result == null && this.wheel.size() >= 2) {
				this.spin();
			}
			return true;
		}
		return super.keyPressed(event);
	}
}
