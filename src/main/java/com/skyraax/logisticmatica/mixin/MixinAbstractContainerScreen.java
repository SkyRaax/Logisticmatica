package com.skyraax.logisticmatica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.loader.api.FabricLoader;

import com.skyraax.logisticmatica.client.ContainerBlocks;
import com.skyraax.logisticmatica.client.ContainerScan;
import com.skyraax.logisticmatica.client.ContainerTracker;

/**
 * On a server the client only sees a container's contents while its screen is open. When the player
 * opens a marked container, snapshot its contents into the tracker so they count towards the list
 * (single-player uses a direct world read instead).
 *
 * <p>This targets a vanilla class, so it always applies; it therefore stays Litematica-free and
 * defers to {@link ContainerScan} only after checking Litematica is present, so a client without
 * Litematica is unaffected.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {
	@Unique
	private boolean logisticmatica$scanned;

	@Inject(method = "init", at = @At("TAIL"))
	private void logisticmatica$scanTrackedContainer(CallbackInfo ci) {
		if (this.logisticmatica$scanned) {
			return;
		}
		this.logisticmatica$scanned = true;

		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || mc.player == null || mc.hasSingleplayerServer()
				|| !FabricLoader.getInstance().isModLoaded("litematica")) {
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos canonical = ContainerBlocks.canonical(mc.level, blockHit.getBlockPos());

		if (ContainerTracker.getInstance().isMarked(canonical)) {
			ContainerScan.snapshot(canonical, ((AbstractContainerScreen<?>) (Object) this).getMenu());
		}
	}
}
