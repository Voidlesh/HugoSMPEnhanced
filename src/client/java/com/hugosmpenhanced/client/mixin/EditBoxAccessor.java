package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.EditBox;

/** The other end of a text field's selection; the cursor is the one end vanilla exposes. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
	@Accessor("highlightPos")
	int hugosmpenhanced$getHighlightPos();
}
