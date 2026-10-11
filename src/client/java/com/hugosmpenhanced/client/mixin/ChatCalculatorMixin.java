package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.calculator.ChatCalculator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Shows the result of a calculation at the end of the chat input in a small box right above the
 * input while typing; Tab replaces the calculation with its result.
 */
@Mixin(ChatScreen.class)
public abstract class ChatCalculatorMixin {
	private static final int BACKGROUND = 0xE0101010;
	private static final int BORDER = 0xFF555555;

	@Shadow
	protected EditBox input;

	@Inject(method = "render", at = @At("TAIL"))
	private void hugosmpenhanced$renderResult(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!ModConfigManager.CONFIG.chatCalculatorEnabled) {
			return;
		}
		ChatCalculator.Result result = ChatCalculator.find(this.input.getValue());
		if (result == null) {
			return;
		}
		var font = net.minecraft.client.Minecraft.getInstance().font;
		String stacks = result.stacks();
		String text = "= " + result.pretty() + (stacks != null ? "   §7" + stacks : "") + "   §8[Tab]";
		int width = font.width(text) + 8;
		int x = this.input.getX() - 2 + font.width(this.input.getValue().substring(0, result.start()));
		x = Math.max(2, Math.min(x, graphics.guiWidth() - width - 2));
		int y = this.input.getY() - 2 - 14;
		graphics.fill(x, y, x + width, y + 13, BACKGROUND);
		graphics.renderOutline(x, y, width, 13, BORDER);
		graphics.drawString(font, text, x + 4, y + 3, 0xFF7CFC7C);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$applyResult(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (event.key() != GLFW.GLFW_KEY_TAB || !ModConfigManager.CONFIG.chatCalculatorEnabled) {
			return;
		}
		String value = this.input.getValue();
		ChatCalculator.Result result = ChatCalculator.find(value);
		if (result != null) {
			this.input.setValue(value.substring(0, result.start()) + result.plain());
			this.input.moveCursorToEnd(false);
			cir.setReturnValue(true);
		}
	}
}
