package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.hugosmpenhanced.client.duck.GuiRenderStateAccess;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

/** Hands the nametag renderer the GUI render state so it can submit glyph quads itself. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsRenderStateMixin implements GuiRenderStateAccess {
	@Shadow
	@Final
	GuiRenderState guiRenderState;

	@Override
	public GuiRenderState hugosmpenhanced$renderState() {
		return this.guiRenderState;
	}
}
