package com.skyraax.logisticmatica.mixin.render;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits floating item icons for marked containers at the end of the world render's submit pass.
 *
 * <p>MC 26.2's deferred render pipeline only hands out a {@link SubmitNodeCollector} inside
 * {@code LevelRenderer.submitFeatures}, and items must go through that collector (there is no more
 * immediate-mode buffer), so this is the one place the icons can be submitted. The Litematica guard
 * runs before {@code ContainerIconRenderer} is referenced, so a client without our optional deps
 * never loads it. Lives in a {@code "required": false} config so any mapping drift just disables the
 * icons instead of crashing.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
	@Inject(method = "submitFeatures", at = @At("TAIL"))
	private void logisticmatica$submitContainerIcons(LevelRenderState levelRenderState,
			SubmitNodeCollector submitNodeCollector, boolean renderOutline, CallbackInfo ci) {
		if (FabricLoader.getInstance().isModLoaded("litematica")) {
			com.skyraax.logisticmatica.client.ContainerIconRenderer.submit(levelRenderState, submitNodeCollector);
		}
	}
}
