package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.feature.aliases.ChatAlias;
import com.hugosmpenhanced.client.feature.aliases.ChatAliases;
import com.hugosmpenhanced.client.feature.calculator.ChatCalculator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Sends the text of a chat alias when the chat input is exactly the alias name, and shows that text
 * above the input while typing the name, so it is clear what Enter will send.
 */
@Mixin(ChatScreen.class)
public abstract class ChatAliasMixin {
	private static final int BACKGROUND = 0xE0101010;
	private static final int BORDER = 0xFF555555;

	@Shadow
	protected EditBox input;

	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
	private String hugosmpenhanced$expandAlias(String input) {
		return ChatAliases.expand(input);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void hugosmpenhanced$renderAliasPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!ModConfigManager.CONFIG.chatAliasPreview) {
			return;
		}
		String value = this.input.getValue();
		ChatAlias alias = ChatAliases.find(value);
		if (alias == null || ChatCalculator.find(value) != null) {
			return;
		}
		var font = Minecraft.getInstance().font;
		String text = alias.text.trim();
		boolean cut = text.length() > ChatAliases.MAX_TEXT;
		String hint = cut ? "§cwird gekürzt" : "§8Enter sendet";
		String label = "Senden: " + (cut ? text.substring(0, ChatAliases.MAX_TEXT) : text);
		int room = graphics.guiWidth() - 24 - font.width(hint);
		String shown = font.width(label) <= room ? label : font.plainSubstrByWidth(label, Math.max(0, room - font.width("…"))) + "…";
		String full = shown + "   " + hint;
		int width = font.width(full) + 8;
		int x = Math.max(2, this.input.getX() - 2);
		int y = this.input.getY() - 2 - 14;
		graphics.fill(x, y, x + width, y + 13, BACKGROUND);
		graphics.renderOutline(x, y, width, 13, BORDER);
		graphics.drawString(font, full, x + 4, y + 3, 0xFFFFFFFF);
	}
}
