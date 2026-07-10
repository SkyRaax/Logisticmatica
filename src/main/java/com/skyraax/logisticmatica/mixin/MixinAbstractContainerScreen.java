package com.skyraax.logisticmatica.mixin;

import javax.annotation.Nullable;

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

/**
 * On a server the client only sees a container's contents while its screen is open, so every
 * container the player looks into is cached from its open screen.
 *
 * <p><strong>Timing matters:</strong> the server sends a container's contents in a separate packet
 * <em>after</em> the screen is opened, so at {@code init()} the slots are still empty. We therefore
 * only <em>remember</em> which block was opened in {@code init()} (while the crosshair still points
 * at it) and take the snapshot in {@code onClose()}, when the contents have definitely arrived.
 *
 * <p>This targets a vanilla class, so it always applies; it therefore stays Litematica-free and
 * defers to {@link ContainerScan} only after checking Litematica is present.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {
	@Unique
	@Nullable
	private BlockPos logisticmatica$openedContainer;

	@Inject(method = "init", at = @At("TAIL"))
	private void logisticmatica$rememberOpenedContainer(CallbackInfo ci) {
		this.logisticmatica$openedContainer = null;

		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || mc.player == null || mc.hasSingleplayerServer()
				|| !FabricLoader.getInstance().isModLoaded("litematica")) {
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		this.logisticmatica$openedContainer = ContainerBlocks.canonical(mc.level, blockHit.getBlockPos());
	}

	@Inject(method = "onClose", at = @At("HEAD"))
	private void logisticmatica$snapshotOnClose(CallbackInfo ci) {
		BlockPos pos = this.logisticmatica$openedContainer;

		if (pos != null) {
			ContainerScan.snapshotIfContainer(pos, ((AbstractContainerScreen<?>) (Object) this).getMenu());
			this.logisticmatica$openedContainer = null;
		}
	}
}
