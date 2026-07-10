package com.skyraax.logisticmatica.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Draws a wall-penetrating outline around every marked container. Registered as a MaLiLib
 * world-last renderer; the no-depth pipeline is what makes the boxes visible through walls, and
 * double chests are drawn as both halves so the whole chest is outlined.
 *
 * <p>All boxes go into a <em>single</em> buffer and are drawn with one render context and one draw
 * call. Drawing each box on its own (which is what {@code RenderUtils.renderBlockOutline} does) costs
 * a pipeline setup, a mesh build and a draw call <em>per box, per frame</em> — with a few dozen
 * marked containers that alone is thousands of allocations per second. Boxes further away than
 * {@link #MAX_DISTANCE} blocks are skipped: at that range they are a couple of pixels wide.
 */
public class ContainerHighlightRenderer implements IRenderer {
	private static final float EXPAND = 0.01f;
	private static final float LINE_WIDTH = 4.0f;

	/** Beyond this distance (in blocks) a highlight is not worth drawing. */
	private static final double MAX_DISTANCE = 128.0;
	private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;

	/** Reused every frame so a full render pass allocates nothing. Render thread only. */
	private final List<BlockPos> visible = new ArrayList<>();

	@Override
	public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
			Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog, Vector4f fogColor,
			ProfilerFiller profiler) {
		ContainerTracker tracker = ContainerTracker.getInstance();

		if (tracker.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}

		Vec3 eye = mc.player.position();
		this.visible.clear();

		for (BlockPos canonical : tracker.getMarked()) {
			if (isTooFarAway(canonical, eye)) {
				continue;
			}

			this.visible.addAll(ContainerBlocks.blocks(mc.level, canonical));
		}

		if (this.visible.isEmpty()) {
			return;
		}

		Color4f color = Configs.Colors.CONTAINER_HIGHLIGHT.getColor();
		RenderContext ctx = new RenderContext(() -> "logisticmatica:container_highlight",
				MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0);
		BufferBuilder buffer = ctx.getBuilder();

		for (BlockPos block : this.visible) {
			RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(block, color, EXPAND, LINE_WIDTH, buffer);
		}

		try {
			MeshData meshData = buffer.build();

			if (meshData != null) {
				ctx.draw(meshData, false, true);
				meshData.close();
			}

			ctx.close();
		} catch (Exception e) {
			Logisticmatica.LOGGER.error("[{}] Failed to draw container highlights: {}",
					Logisticmatica.MOD_NAME, e.getMessage());
		}
	}

	private static boolean isTooFarAway(BlockPos pos, Vec3 eye) {
		double dx = pos.getX() + 0.5 - eye.x;
		double dy = pos.getY() + 0.5 - eye.y;
		double dz = pos.getZ() + 0.5 - eye.z;

		return dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ;
	}
}
