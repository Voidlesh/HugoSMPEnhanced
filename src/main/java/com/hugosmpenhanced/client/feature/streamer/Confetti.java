package com.hugosmpenhanced.client.feature.streamer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Paper confetti in GUI pixels: bursts out, tumbles, falls and fades. */
public final class Confetti {
	private static final int[] COLORS = {0xFFFFC83D, 0xFFFF6E6E, 0xFF6EC8FF, 0xFF82E68C, 0xFFC88CFF, 0xFFFFFFFF};

	private static final class Piece {
		float x;
		float y;
		float vx;
		float vy;
		float angle;
		float spin;
		float wobble;
		int color;
		float life;
		float maxLife;
		int w;
		int h;
	}

	private final List<Piece> pieces = new ArrayList<>();
	private final Random random = new Random();
	private long lastUpdate;

	/** A burst from a point, flying up and to the sides. */
	public void burst(float x, float y, int count, float power) {
		for (int i = 0; i < count; i++) {
			Piece p = new Piece();
			p.x = x + (this.random.nextFloat() - 0.5f) * 20;
			p.y = y + (this.random.nextFloat() - 0.5f) * 10;
			double angle = Math.PI * (1.1 + this.random.nextFloat() * 0.8);
			float speed = power * (0.5f + this.random.nextFloat());
			p.vx = (float) Math.cos(angle) * speed;
			p.vy = (float) Math.sin(angle) * speed;
			this.init(p);
			this.pieces.add(p);
		}
	}

	/** Rain over a strip, for a celebration that lasts a moment. */
	public void rain(float x0, float x1, float y, int count) {
		for (int i = 0; i < count; i++) {
			Piece p = new Piece();
			p.x = x0 + this.random.nextFloat() * (x1 - x0);
			p.y = y - this.random.nextFloat() * 60;
			p.vx = (this.random.nextFloat() - 0.5f) * 30;
			p.vy = 20 + this.random.nextFloat() * 40;
			this.init(p);
			this.pieces.add(p);
		}
	}

	private void init(Piece p) {
		p.angle = this.random.nextFloat() * 360;
		p.spin = (this.random.nextFloat() - 0.5f) * 720;
		p.wobble = this.random.nextFloat() * 6.28f;
		p.color = COLORS[this.random.nextInt(COLORS.length)];
		p.maxLife = 2.2f + this.random.nextFloat() * 1.6f;
		p.life = p.maxLife;
		p.w = 1 + this.random.nextInt(2);
		p.h = 2 + this.random.nextInt(2);
	}

	public boolean active() {
		return !this.pieces.isEmpty();
	}

	public void clear() {
		this.pieces.clear();
	}

	public void render(GuiGraphicsExtractor graphics) {
		long now = System.nanoTime();
		float dt = this.lastUpdate == 0 ? 0 : Math.min(0.05f, (now - this.lastUpdate) / 1e9f);
		this.lastUpdate = now;
		for (int i = this.pieces.size() - 1; i >= 0; i--) {
			Piece p = this.pieces.get(i);
			p.life -= dt;
			if (p.life <= 0) {
				this.pieces.remove(i);
				continue;
			}
			p.vy += 160 * dt;
			p.vx *= 1 - 1.6f * dt;
			p.vy = Math.min(p.vy, 70);
			p.wobble += dt * 5;
			p.x += (p.vx + (float) Math.sin(p.wobble) * 12) * dt;
			p.y += p.vy * dt;
			p.angle += p.spin * dt;
			float fade = Math.min(1f, p.life / 0.6f);
			// Turning paper: the width shrinks and grows as it flips.
			float flip = Math.abs((float) Math.cos(p.wobble * 1.3f));
			graphics.pose().pushMatrix();
			graphics.pose().translate(p.x, p.y);
			graphics.pose().rotate((float) Math.toRadians(p.angle));
			graphics.pose().scale(Math.max(0.25f, flip), 1f);
			graphics.fill(-p.w, -p.h, p.w, p.h, StreamerUi.alpha(p.color, fade));
			graphics.pose().popMatrix();
		}
		if (this.pieces.isEmpty()) {
			this.lastUpdate = 0;
		}
	}
}
