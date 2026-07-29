package com.skyraax.logisticmatica.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.skyraax.logisticmatica.server.ServerContainerDirtyListener;

/** Notices removal or replacement of a marked container even when no block entity remains. */
@Mixin(Level.class)
public abstract class MixinLevel {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
			at = @At("RETURN"))
	private void logisticmatica$blockChanged(BlockPos pos, BlockState state, int flags,
			int updateLimit, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			ServerContainerDirtyListener.mark((Level) (Object) this, pos);
		}
	}
}
