package com.skyraax.logisticmatica.client;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.GuiUtils;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.ContainerData;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * Draws a compact panel of item icons + counts above each nearby marked container. The panel is a
 * crisp 2D overlay positioned by projecting the container's world position to the screen — much more
 * readable than in-world text, and it lets us lay out icons next to text and wrap long lists into
 * columns. The camera matrices are captured during the world render pass and used in the GUI overlay
 * pass to do the projection.
 */
public class ContainerLabelRenderer implements IRenderer {
	private static final double LABEL_DISTANCE = 48.0;
	private static final double LABEL_DISTANCE_SQ = LABEL_DISTANCE * LABEL_DISTANCE;
	private static final int MAX_LABELS = 12;
	private static final int MAX_ITEMS = 30;
	private static final int ROWS_PER_COLUMN = 10;

	private static final int ROW_HEIGHT = 18;
	private static final int ICON_GAP = 20;
	private static final int COL_GAP = 12;
	private static final int PAD = 3;
	private static final double ANCHOR_HEIGHT = 1.15;

	/** Scale per screen-pixel of one world block; keeps the panel a fixed world size, like a name tag. */
	private static final float PANEL_WORLD_SCALE = 0.022f;

	private final Matrix4f viewMatrix = new Matrix4f();
	private final Matrix4f projMatrix = new Matrix4f();
	private final Vector4f scratch = new Vector4f();
	@Nullable private Vec3 cameraPos;

	// --- World pass: capture the camera transform for this frame. ---
	@Override
	public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
			Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog, org.joml.Vector4f fogColor,
			ProfilerFiller profiler) {
		// The camera render state carries the very matrices used to draw the world this frame.
		this.viewMatrix.set(cameraState.viewRotationMatrix);
		this.projMatrix.set(cameraState.projectionMatrix);
		this.cameraPos = cameraState.pos;
	}

	// --- GUI pass: project each marked container to the screen and draw its panel. ---
	@Override
	public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
		if (!Configs.Hud.SHOW_CONTAINER_LABELS.getBooleanValue() || this.cameraPos == null) {
			return;
		}

		if (ContainerTracker.getInstance().isEmpty() || GuiUtils.getCurrentScreen() != null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}

		Font font = mc.font;
		boolean seeThrough = Configs.Hud.LABEL_SEE_THROUGH.getBooleanValue();
		int scaledW = GuiUtils.getScaledWindowWidth();
		int scaledH = GuiUtils.getScaledWindowHeight();
		Vec3 eye = mc.player.position();

		Map<String, LitematicaSchematic> loaded = SchematicKey.loadedByKey();
		int shown = 0;
		for (Snapshot snapshot : ContainerData.collectMarked()) {
			// Only show containers whose schematic is currently loaded.
			LitematicaSchematic schematic = snapshot.schematicKey() != null ? loaded.get(snapshot.schematicKey()) : null;
			if (schematic == null) {
				continue;
			}

			BlockPos pos = snapshot.pos();
			if (distanceSq(pos, eye) > LABEL_DISTANCE_SQ) {
				continue;
			}

			double wy = pos.getY() + ANCHOR_HEIGHT;
			float[] screen = this.project(pos.getX() + 0.5, wy, pos.getZ() + 0.5, scaledW, scaledH);
			float[] screenUp = this.project(pos.getX() + 0.5, wy + 1.0, pos.getZ() + 0.5, scaledW, scaledH);
			if (screen == null || screenUp == null) {
				continue;
			}

			if (!seeThrough && this.isOccluded(mc, pos)) {
				continue;
			}

			// The screen span of one world block at this depth drives the scale, so the panel behaves
			// like a name tag: it tracks perspective, FOV and zoom exactly, as if fixed to the container.
			float unitPixels = Math.abs(screen[1] - screenUp[1]);
			float scale = Math.max(0.02f, unitPixels * PANEL_WORLD_SCALE);

			drawPanel(ctx, font, screen[0], screen[1], snapshot, scale,
					schematic.getMetadata().getName(), SchematicColors.argb(snapshot.schematicKey()));

			if (++shown >= MAX_LABELS) {
				break;
			}
		}
	}

	/** Projects a world position to GUI-scaled screen coords {@code [x, y]}, or null if behind the camera. */
	@Nullable
	private float[] project(double wx, double wy, double wz, int scaledW, int scaledH) {
		this.scratch.set((float) (wx - this.cameraPos.x), (float) (wy - this.cameraPos.y),
				(float) (wz - this.cameraPos.z), 1.0f);
		this.viewMatrix.transform(this.scratch);
		this.projMatrix.transform(this.scratch);

		if (this.scratch.w <= 0.0001f) {
			return null; // behind the camera
		}

		float ndcX = this.scratch.x / this.scratch.w;
		float ndcY = this.scratch.y / this.scratch.w;
		return new float[] {
				(ndcX * 0.5f + 0.5f) * scaledW,
				(1.0f - (ndcY * 0.5f + 0.5f)) * scaledH
		};
	}

	private boolean isOccluded(Minecraft mc, BlockPos pos) {
		BlockHitResult hit = mc.level.clip(new ClipContext(this.cameraPos, Vec3.atCenterOf(pos),
				ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));

		return hit.getType() == HitResult.Type.BLOCK
				&& !ContainerBlocks.blocks(mc.level, pos).contains(hit.getBlockPos());
	}

	private static void drawPanel(GuiContext ctx, Font font, float sx, float sy, Snapshot snapshot,
			float scale, String header, int headerColor) {
		List<ItemCount> items = snapshot.items();
		int shown = Math.min(MAX_ITEMS, items.size());
		int columns = (shown + ROWS_PER_COLUMN - 1) / ROWS_PER_COLUMN;
		int rowsTall = Math.min(shown, ROWS_PER_COLUMN);

		int maxNameW = 0;
		for (int i = 0; i < shown; i++) {
			maxNameW = Math.max(maxNameW, font.width(lineText(items.get(i))));
		}

		int headerHeight = font.lineHeight + 3;
		int colWidth = ICON_GAP + maxNameW + COL_GAP;
		int totalW = Math.max(columns * colWidth - COL_GAP, font.width(header)) + PAD * 2;
		int totalH = headerHeight + rowsTall * ROW_HEIGHT + PAD * 2;

		float panelLeft = sx - (totalW * scale) / 2.0f;
		float panelTop = sy - totalH * scale;

		int textColor = Configs.Colors.TEXT.getIntegerValue();

		ctx.pose().pushMatrix();
		ctx.pose().translate(panelLeft, panelTop);
		ctx.pose().scale(scale, scale);

		ctx.fill(0, 0, totalW, totalH, Configs.Colors.BACKGROUND.getIntegerValue());

		// Header: the schematic name in the schematic's own colour, so it is obvious what the container is for.
		ctx.drawString(font, header, PAD, PAD, headerColor, true);

		for (int i = 0; i < shown; i++) {
			int col = i / ROWS_PER_COLUMN;
			int row = i % ROWS_PER_COLUMN;
			int x = PAD + col * colWidth;
			int y = PAD + headerHeight + row * ROW_HEIGHT;

			ItemStack stack = items.get(i).stack();
			ctx.renderItem(stack, x, y);
			ctx.drawString(font, lineText(items.get(i)), x + ICON_GAP, y + (ROW_HEIGHT - font.lineHeight) / 2, textColor, true);
		}

		ctx.pose().popMatrix();
	}

	private static String lineText(ItemCount item) {
		return item.count() + "× " + item.stack().getHoverName().getString();
	}

	private static double distanceSq(BlockPos pos, Vec3 eye) {
		double dx = pos.getX() + 0.5 - eye.x;
		double dy = pos.getY() + 0.5 - eye.y;
		double dz = pos.getZ() + 0.5 - eye.z;

		return dx * dx + dy * dy + dz * dz;
	}
}
