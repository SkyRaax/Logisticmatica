package com.skyraax.logisticmatica.client;

import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.GuiUtils;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.config.ContainerLabelLayout;
import com.skyraax.logisticmatica.client.gui.ContainerData;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;
import com.skyraax.logisticmatica.mixin.AccessorLevelRenderer;

/**
 * Draws a compact panel above each nearby marked container of the focused schematic. The panel is a
 * crisp 2D overlay positioned by projecting the container's world position to the screen — much more
 * readable than in-world text, and it lets us lay out icons next to text and wrap long lists into
 * columns. The current-frame camera matrices are read in the GUI overlay
 * pass to do the projection.
 */
public class ContainerLabelRenderer implements IRenderer {
	private static final double LABEL_DISTANCE = 48.0;
	private static final double LABEL_DISTANCE_SQ = LABEL_DISTANCE * LABEL_DISTANCE;
	private static final int MAX_LABELS = 12;
	private static final int COLUMN_ROWS = 10;

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
	private record Projection(float x, float y, float clipW) {}

	// --- GUI pass: read the current camera state, project each container and draw its panel. ---
	@Override
	public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
		if (!Configs.Hud.CONTAINER_VISUALS_ENABLED.getBooleanValue()
				|| !Configs.Hud.SHOW_CONTAINER_LABELS.getBooleanValue()) {
			return;
		}

		if (ContainerTracker.getInstance().isEmpty() || FocusState.getSchematic() == null
				|| GuiUtils.getCurrentScreen() != null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}

		CameraRenderState cameraState = ((AccessorLevelRenderer) (Object) mc.levelRenderer)
				.logisticmatica$getLevelRenderState().cameraRenderState;
		this.viewMatrix.set(cameraState.viewRotationMatrix);
		this.projMatrix.set(cameraState.projectionMatrix);
		this.cameraPos = cameraState.pos;

		Font font = mc.font;
		boolean seeThrough = Configs.Hud.LABEL_SEE_THROUGH.getBooleanValue();
		int scaledW = GuiUtils.getScaledWindowWidth();
		int scaledH = GuiUtils.getScaledWindowHeight();
		Vec3 eye = this.cameraPos;

		int shown = 0;
		for (Snapshot snapshot : ContainerData.collectMarked()) {
			if (!FocusState.isFocusedSchematicKey(snapshot.schematicKey())
					|| !ContainerTracker.getInstance().isVisualsVisible(snapshot.schematicKey(), snapshot.pos())) continue;

			BlockPos pos = snapshot.pos();
			if (distanceSq(pos, eye) > LABEL_DISTANCE_SQ) {
				continue;
			}

			double wy = pos.getY() + ANCHOR_HEIGHT;
			Projection screen = this.project(pos.getX() + 0.5, wy, pos.getZ() + 0.5, scaledW, scaledH);
			if (screen == null) {
				continue;
			}

			if (!seeThrough && this.isOccluded(mc, pos)) {
				continue;
			}

			// Perspective size depends on camera-space depth (clip W), not Euclidean distance.
			// This keeps a billboard's apparent geometry stable near the viewport edges and under zoom.
			float unitPixels = Math.abs(this.projMatrix.m11()) * scaledH * 0.5f
					/ Math.max(0.25f, screen.clipW());
			float scale = Math.min(1.0f, Math.max(0.02f, unitPixels * PANEL_WORLD_SCALE));
			String header = SchematicKey.displayName(snapshot.schematicKey());

			drawPanel(ctx, font, screen.x(), screen.y(), snapshot, scale,
					header, SchematicColors.argb(snapshot.schematicKey()));

			if (++shown >= MAX_LABELS) {
				break;
			}
		}
	}

	/** Projects a world position and retains clip W, the perspective-correct camera depth. */
	@Nullable
	private Projection project(double wx, double wy, double wz, int scaledW, int scaledH) {
		this.scratch.set((float) (wx - this.cameraPos.x), (float) (wy - this.cameraPos.y),
				(float) (wz - this.cameraPos.z), 1.0f);
		this.viewMatrix.transform(this.scratch);
		this.projMatrix.transform(this.scratch);

		if (this.scratch.w <= 0.0001f) {
			return null; // behind the camera
		}

		float ndcX = this.scratch.x / this.scratch.w;
		float ndcY = this.scratch.y / this.scratch.w;
		return new Projection(
				(ndcX * 0.5f + 0.5f) * scaledW,
				(1.0f - (ndcY * 0.5f + 0.5f)) * scaledH,
				this.scratch.w);
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
		int shown = Math.min(Configs.Hud.LABEL_MAX_ITEMS.getIntegerValue(), items.size());
		boolean vertical = Configs.Hud.LABEL_LAYOUT.getOptionListValue() == ContainerLabelLayout.VERTICAL;
		int rowsPerColumn = vertical ? Math.max(1, shown) : COLUMN_ROWS;
		int columns = Math.max(1, (shown + rowsPerColumn - 1) / rowsPerColumn);
		int rowsTall = Math.min(shown, rowsPerColumn);
		String displayHeader = items.size() > shown ? header + " (+" + (items.size() - shown) + ")" : header;

		int maxNameW = 0;
		for (int i = 0; i < shown; i++) {
			maxNameW = Math.max(maxNameW, font.width(lineText(items.get(i))));
		}

		int headerHeight = font.lineHeight + 3;
		int colWidth = ICON_GAP + maxNameW + COL_GAP;
		int totalW = Math.max(columns * colWidth - COL_GAP, font.width(displayHeader)) + PAD * 2;
		int totalH = headerHeight + rowsTall * ROW_HEIGHT + PAD * 2;

		float scaledPanelWidth = totalW * scale;
		float scaledPanelHeight = totalH * scale;
		// Do not clamp to the viewport: a real billboard naturally moves partially off-screen.
		float panelLeft = sx - scaledPanelWidth / 2.0f;
		float panelTop = sy - scaledPanelHeight;

		int textColor = Configs.Colors.TEXT.getIntegerValue();

		ctx.pose().pushMatrix();
		ctx.pose().translate(panelLeft, panelTop);
		ctx.pose().scale(scale, scale);

		ctx.fill(0, 0, totalW, totalH, Configs.Colors.BACKGROUND.getIntegerValue());

		// Header: the schematic name in the schematic's own colour, so it is obvious what the container is for.
		ctx.drawString(font, displayHeader, PAD, PAD, headerColor, true);

		for (int i = 0; i < shown; i++) {
			int col = i / rowsPerColumn;
			int row = i % rowsPerColumn;
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
