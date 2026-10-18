package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Anti-Leak: chunk meshing sees ores as stone and the bedrock pattern layers as solid bedrock. The
 * region is only used to build chunk meshes, so the real world (collisions, mining) is untouched.
 */
@Mixin(RenderSectionRegion.class)
public abstract class AntiLeakSectionRegionMixin {
	@ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
	private BlockState hugosmpenhanced$disguise(BlockState state, BlockPos pos) {
		return AntiLeak.disguise(state, (BlockGetter) this, pos.getX(), pos.getY(), pos.getZ());
	}
}
