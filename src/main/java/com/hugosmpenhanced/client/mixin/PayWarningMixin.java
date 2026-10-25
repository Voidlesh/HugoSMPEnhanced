package com.hugosmpenhanced.client.mixin;

//? if modern
import com.mojang.blaze3d.platform.InputConstants;
//? else
import org.lwjgl.glfw.GLFW;
//? end
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.calculator.ChatCalculator;
import com.hugosmpenhanced.client.feature.pay.PayWarning;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;

/** The box above the chat input while typing {@code /pay}, and Enter twice for scammers. */
@Mixin(ChatScreen.class)
public abstract class PayWarningMixin {
	@Shadow
	protected EditBox input;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void hugosmpenhanced$renderPayWarning(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		String value = this.input.getValue();
		PayWarning.Info info = PayWarning.info(value);
		if (info == null) {
			return;
		}
		// Above the line where Minecraft shows the command's usage or error.
		int bottom = this.input.getY() - 15;
		if (ModConfigManager.CONFIG.chatCalculatorEnabled && ChatCalculator.find(value) != null) {
			// Above the calculator's result box.
			bottom -= 15;
		}
		int right = this.input.getX() + this.input.getWidth();
		PayWarning.render(graphics, Minecraft.getInstance().font, info, value, right, bottom, graphics.guiWidth() - 8);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$confirmScammerPay(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && PayWarning.blockEnter(this.input.getValue())) {
			cir.setReturnValue(true);
		}
	}
}
