package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.feature.pay.PayWarning;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

/** Colours a scammer's name red in a typed {@code /pay}. */
@Mixin(CommandSuggestions.class)
public abstract class PayNameColorMixin {
	@Shadow
	@Final
	EditBox input;

	@Shadow
	@Final
	private Screen screen;

	@Inject(method = "formatChat", at = @At("RETURN"), cancellable = true)
	private void hugosmpenhanced$colorScammerName(String text, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
		if (!(this.screen instanceof ChatScreen)) {
			return;
		}
		FormattedCharSequence colored = PayWarning.colorName(cir.getReturnValue(), text, offset, this.input.getValue());
		if (colored != cir.getReturnValue()) {
			cir.setReturnValue(colored);
		}
	}
}
