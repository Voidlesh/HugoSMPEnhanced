package com.hugosmpenhanced.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hugosmpenhanced.client.feature.antileak.AntiLeak;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

/**
 * Anti-Leak: the model variant (texture rotation of dirt, grass, sand, ...) and the random offset of
 * flowers and grass are picked from the block position alone. With a fixed seed every block looks
 * the same; offsets go to the centre only for blocks you walk through (bamboo and dripstone keep
 * theirs, their hitbox depends on it). Vanilla and Sodium both read these two methods.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class AntiLeakBlockStateMixin {
	@Shadow
	public abstract Block getBlock();

	@Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$fixedSeed(BlockPos pos, CallbackInfoReturnable<Long> cir) {
		if (AntiLeak.noRotation()) {
			cir.setReturnValue(0L);
		}
	}

	@Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
	private void hugosmpenhanced$noOffset(BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
		if (AntiLeak.noRotation() && !((BlockBehaviourAccessor) this.getBlock()).hugosmpenhanced$hasCollision()) {
			cir.setReturnValue(Vec3.ZERO);
		}
	}
}
