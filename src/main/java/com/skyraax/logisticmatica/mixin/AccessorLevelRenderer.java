package com.skyraax.logisticmatica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Exposes the current-frame camera matrices to projected GUI overlays. */
@Mixin(LevelRenderer.class)
public interface AccessorLevelRenderer {
	@Accessor("levelRenderState")
	LevelRenderState logisticmatica$getLevelRenderState();
}