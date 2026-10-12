package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.state.BlockBehaviour;

@Mixin(BlockBehaviour.class)
public interface BlockBehaviourAccessor {
	@Accessor("hasCollision")
	boolean hugosmpenhanced$hasCollision();
}
