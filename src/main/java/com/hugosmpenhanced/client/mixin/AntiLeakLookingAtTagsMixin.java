package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.components.debug.DebugEntryLookingAt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Anti-Leak: F3's "Targeted Block" names the block you see, not the ore or bedrock under the disguise. */
@Mixin(DebugEntryLookingAt.BlockTagInfo.class)
public abstract class AntiLeakLookingAtTagsMixin {
	@WrapOperation(method = "getInstance", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState hugosmpenhanced$disguise(Level level, BlockPos pos, Operation<BlockState> original) {
		return AntiLeak.disguise(original.call(level, pos), level, pos.getX(), pos.getY(), pos.getZ());
	}
}
