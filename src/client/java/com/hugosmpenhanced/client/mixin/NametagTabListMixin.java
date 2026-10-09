package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;

/**
 * Applies the user's nametag style effects to their own row in the tab list (player list),
 * by intercepting the single spot where {@link PlayerTabOverlay} draws a player's name.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class NametagTabListMixin {
	@Redirect(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
		)
	)
	private void hugosmpenhanced$drawStyledName(GuiGraphics graphics, Font font, Component component, int x, int y, int color) {
		if (NametagStyleRenderer.hasAnyEffect() && NametagStyleRenderer.matchesLocalName(component.getString())) {
			NametagStyleRenderer.renderComponent2D(graphics, font, component, x, y, color, true);
		} else {
			graphics.drawString(font, component, x, y, color);
		}
	}
}
