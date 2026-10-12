package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Anti-Leak with BetterF3: its "Targeted Block" names the block you see, not a hidden ore. The
 * call is in BetterF3's code, so it is not remapped; both the production and the development name
 * are listed.
 */
@Pseudo
@Mixin(targets = "me.cominixo.betterf3.modules.TargetModule", remap = false)
public abstract class AntiLeakBetterF3TargetMixin {
	@WrapOperation(method = "update", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/class_638;method_8320(Lnet/minecraft/class_2338;)Lnet/minecraft/class_2680;"))
	private BlockState hugosmpenhanced$disguise(ClientLevel level, BlockPos pos, Operation<BlockState> original) {
		return AntiLeak.disguise(original.call(level, pos), level, pos.getX(), pos.getY(), pos.getZ());
	}

	@WrapOperation(method = "update", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState hugosmpenhanced$disguiseDev(ClientLevel level, BlockPos pos, Operation<BlockState> original) {
		return AntiLeak.disguise(original.call(level, pos), level, pos.getX(), pos.getY(), pos.getZ());
	}
}
