package com.skyraax.logisticmatica.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.skyraax.logisticmatica.server.ServerContainerDirtyListener;

/** Enqueues inventory mutations without scanning unrelated projects or chunks. */
@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity {
	@Inject(method = "setChanged()V", at = @At("HEAD"))
	private void logisticmatica$containerChanged(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		ServerContainerDirtyListener.mark(self.getLevel(), self.getBlockPos());
	}
}
