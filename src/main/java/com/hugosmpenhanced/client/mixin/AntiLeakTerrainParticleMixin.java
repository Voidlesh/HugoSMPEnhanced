package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;

import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.level.block.state.BlockState;

/** Anti-Leak: mining and breaking a hidden ore shows stone crumbs, not the ore's. */
@Mixin(TerrainParticle.class)
public abstract class AntiLeakTerrainParticleMixin {
	@ModifyArg(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
		at = @At(value = "INVOKE",
			//? if modern
			target = "Lnet/minecraft/client/renderer/block/BlockStateModelSet;getParticleMaterial(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/resources/model/sprite/Material$Baked;"))
			//? else
			target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getParticleIcon(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
			//? end
	private static BlockState hugosmpenhanced$disguise(BlockState state) {
		return AntiLeak.disguiseParticle(state);
	}
}
