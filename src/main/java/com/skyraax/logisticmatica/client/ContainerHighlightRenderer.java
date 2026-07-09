package com.skyraax.logisticmatica.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Draws a wall-penetrating outline around every marked container. Registered as a MaLiLib
 * world-last renderer; {@link RenderUtils#renderBlockOutline} with {@code renderThrough = true}
 * uses a no-depth pipeline so the boxes are visible through walls. Double chests are drawn as both
 * halves so the whole chest is outlined.
 */
public class ContainerHighlightRenderer implements IRenderer {
	private static final float EXPAND = 0.01f;
	private static final float LINE_WIDTH = 4.0f;

	@Override
	public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
			Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog, Vector4f fogColor,
			ProfilerFiller profiler) {
		ContainerTracker tracker = ContainerTracker.getInstance();

		if (tracker.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}

		Color4f color = Configs.Colors.CONTAINER_HIGHLIGHT.getColor();

		for (BlockPos canonical : tracker.getMarked()) {
			for (BlockPos block : ContainerBlocks.blocks(mc.level, canonical)) {
				RenderUtils.renderBlockOutline(block, EXPAND, LINE_WIDTH, color, true);
			}
		}
	}
}
