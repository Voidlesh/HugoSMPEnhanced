package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hugosmpenhanced.client.feature.nametag.NametagStyleRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Applies the user's nametag style effects to their own row in the sidebar scoreboard. The
 * redirect also sees the objective header and score text draws in the same method, but those
 * never contain the local player's name so {@link NametagStyleRenderer#matchesLocalName} filters
 * them out.
 */
@Mixin(Hud.class)
public abstract class NametagScoreboardMixin {
	@Redirect(
		method = "displayScoreboardSidebar",
		at = @At(
			value = "INVOKE",
			//? if modern
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
			//? else
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
			//? end
		)
	)
	private void hugosmpenhanced$drawStyledSidebarName(GuiGraphicsExtractor graphics, Font font, Component component, int x, int y, int color, boolean dropShadow) {
		if (NametagStyleRenderer.hasAnyEffect() && NametagStyleRenderer.matchesLocalName(component.getString())) {
			NametagStyleRenderer.renderComponent2D(graphics, font, component, x, y, color, dropShadow);
		} else {
			graphics.text(font, component, x, y, color, dropShadow);
		}
	}
}
