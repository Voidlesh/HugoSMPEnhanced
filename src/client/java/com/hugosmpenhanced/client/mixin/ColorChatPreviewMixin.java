package com.hugosmpenhanced.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodeScreen;
import com.hugosmpenhanced.client.feature.colorcodes.ColorCodes;
import com.hugosmpenhanced.client.feature.colorcodes.ColorText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * While typing /rename or /sign, shows the colour codes as they will look in a small box at the
 * right above the input, with a link into the colour code editor (click or Ctrl+E).
 */
@Mixin(ChatScreen.class)
public abstract class ColorChatPreviewMixin {
	@Unique
	private static final int BACKGROUND = 0xE0101010;
	@Unique
	private static final int BORDER = 0xFF555555;
	@Unique
	private static final String LINK = "Editor  Strg+E";

	@Shadow
	protected EditBox input;

	@Unique
	private int hugosmpenhanced$boxX = -1;
	@Unique
	private int hugosmpenhanced$boxY;
	@Unique
	private int hugosmpenhanced$boxW;

	@Inject(method = "render", at = @At("TAIL"))
	private void hugosmpenhanced$renderColorPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		this.hugosmpenhanced$boxX = -1;
		ColorCodes.Match match = this.hugosmpenhanced$match();
		if (match == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		ColorText text = ColorText.parse(this.input.getValue().substring(match.textStart()));
		int linkWidth = font.width(LINK);
		int maxText = Math.max(40, graphics.guiWidth() / 2 - linkWidth - 20);
		boolean cut = false;
		while (!text.glyphs.isEmpty() && font.width(text.toComponent()) > maxText) {
			text.glyphs.remove(0);
			cut = true;
		}
		Component shown = text.glyphs.isEmpty() ? Component.literal("Farbvorschau").withColor(0xFF777777) : text.toComponent();
		if (cut) {
			shown = Component.literal("…").withColor(0xFF777777).append(shown);
		}
		int width = font.width(shown) + 8 + 9 + linkWidth + 6;
		int x = this.input.getX() + this.input.getWidth() - width;
		int y = this.input.getY() - 2 - 14;
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 13;
		graphics.fill(x, y, x + width, y + 13, BACKGROUND);
		graphics.renderOutline(x, y, width, 13, over ? 0xFFFFC83D : BORDER);
		graphics.drawString(font, shown, x + 4, y + 3, 0xFFFFFFFF);
		int linkX = x + width - linkWidth - 5;
		graphics.fill(linkX - 5, y + 2, linkX - 4, y + 11, BORDER);
		graphics.drawString(font, LINK, linkX, y + 3, over ? 0xFFFFC83D : 0xFFAAAAAA);
		this.hugosmpenhanced$boxX = x;
		this.hugosmpenhanced$boxY = y;
		this.hugosmpenhanced$boxW = width;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$clickColorPreview(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (this.hugosmpenhanced$boxX < 0 || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return;
		}
		if (event.x() >= this.hugosmpenhanced$boxX && event.x() < this.hugosmpenhanced$boxX + this.hugosmpenhanced$boxW
			&& event.y() >= this.hugosmpenhanced$boxY && event.y() < this.hugosmpenhanced$boxY + 13 && this.hugosmpenhanced$openEditor()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$colorEditorKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (event.key() == GLFW.GLFW_KEY_E && event.hasControlDownWithQuirk() && this.hugosmpenhanced$openEditor()) {
			cir.setReturnValue(true);
		}
	}

	@Unique
	private ColorCodes.Match hugosmpenhanced$match() {
		if (!ModConfigManager.CONFIG.colorChatPreview) {
			return null;
		}
		return ColorCodes.match(this.input.getValue());
	}

	@Unique
	private boolean hugosmpenhanced$openEditor() {
		ColorCodes.Match match = this.hugosmpenhanced$match();
		if (match == null) {
			return false;
		}
		String value = this.input.getValue();
		Minecraft.getInstance().setScreen(new ColorCodeScreen(null, match.command(), value.substring(match.textStart()), value));
		return true;
	}
}
