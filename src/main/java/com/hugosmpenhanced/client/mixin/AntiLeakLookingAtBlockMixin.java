package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

//? if modern
import net.minecraft.client.gui.components.debug.DebugEntryLookingAt;
//? else
import net.minecraft.client.gui.components.debug.DebugEntryLookingAtBlock;
//? end
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Anti-Leak: F3's "Targeted Block" names the block you see, not the ore or bedrock under the disguise. */
//? if modern
@Mixin(DebugEntryLookingAt.BlockStateInfo.class)
//? else
@Mixin(DebugEntryLookingAtBlock.class)
//? end
public abstract class AntiLeakLookingAtBlockMixin {
	//? if modern
	@WrapOperation(method = "getInstance", at = @At(value = "INVOKE",
	//? else
	@WrapOperation(method = "display", at = @At(value = "INVOKE",
	//? end
		target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState hugosmpenhanced$disguise(Level level, BlockPos pos, Operation<BlockState> original) {
		return AntiLeak.disguise(original.call(level, pos), level, pos.getX(), pos.getY(), pos.getZ());
	}
}
