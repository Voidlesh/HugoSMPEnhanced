package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Anti-Leak with Sodium (and Iris): Sodium builds chunk meshes from its own LevelSlice instead of
 * the vanilla region. Its BlockPos lookup goes through this one. The method is Sodium's own, so it
 * is not remapped; the descriptor is listed with the production (intermediary) and the development
 * name of BlockState.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class AntiLeakSodiumLevelSliceMixin {
	@ModifyReturnValue(method = {
		"getBlockState(III)Lnet/minecraft/class_2680;",
		"getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"
	}, at = @At("RETURN"), require = 0)
	private BlockState hugosmpenhanced$disguise(BlockState state, int x, int y, int z) {
		return AntiLeak.disguise(state, (BlockGetter) (Object) this, x, y, z);
	}
}
