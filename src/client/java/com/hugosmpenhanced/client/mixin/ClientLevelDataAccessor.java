package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.multiplayer.ClientLevel;

/** Whether the server's world is a superflat one (sent on join, otherwise only used for the horizon). */
@Mixin(ClientLevel.ClientLevelData.class)
public interface ClientLevelDataAccessor {
	@Accessor("isFlat")
	boolean hugosmpenhanced$isFlat();
}
