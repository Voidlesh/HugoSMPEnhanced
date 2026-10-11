package com.hugosmpenhanced.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int hugosmpenhanced$getLeftPos();

	@Accessor("topPos")
	int hugosmpenhanced$getTopPos();

	@Accessor("imageWidth")
	int hugosmpenhanced$getImageWidth();

	@Accessor("imageHeight")
	int hugosmpenhanced$getImageHeight();
}
