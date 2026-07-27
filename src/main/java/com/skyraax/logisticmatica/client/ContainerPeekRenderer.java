package com.skyraax.logisticmatica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.render.InventoryOverlay.InventoryProperties;
import fi.dy.masa.malilib.render.InventoryOverlayType;
import fi.dy.masa.malilib.util.GuiUtils;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.ContainerData;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * The "chest-tracker" peek: when the crosshair is on a focused project's marked container, draws
 * a small inventory
 * panel (chest background + item sprites + stack counts) at the top of the screen showing what the
 * container holds. The contents come from the cache, so it works even when the container is closed,
 * out of reach, or on a server where it cannot be read live.
 *
 * <p>Built on MaLiLib's {@link InventoryOverlay} — the same 2D overlay Litematica uses for its own
 * look-at inventory preview — fed from a cached {@link Snapshot} re-expanded into item stacks.
 */
public class ContainerPeekRenderer implements IRenderer {
	@Override
	public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
		if (!Configs.Hud.CONTAINER_VISUALS_ENABLED.getBooleanValue()
				|| !Configs.Hud.SHOW_CONTAINER_PEEK.getBooleanValue()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || FocusState.getSchematic() == null
				|| GuiUtils.getCurrentScreen() != null) {
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos canonical = ContainerBlocks.canonical(mc.level, blockHit.getBlockPos());
		if (!ContainerTracker.getInstance().isMarked(canonical)) {
			return;
		}

		Snapshot snapshot = ContainerData.snapshotOf(canonical);
		if (snapshot == null || !ContainerTracker.getInstance().isVisualsVisible(snapshot.schematicKey(), canonical)
				|| !FocusState.isFocusedSchematicKey(snapshot.schematicKey())
				|| snapshot.items().isEmpty()) {
			return;
		}

		boolean isDouble = ContainerBlocks.blocks(mc.level, canonical).size() > 1;
		int maxSlots = isDouble ? 54 : 27;
		InventoryOverlayType type = isDouble ? InventoryOverlayType.FIXED_54 : InventoryOverlayType.FIXED_27;

		NonNullList<ItemStack> items = expandToStacks(snapshot, maxSlots);
		InventoryProperties props = InventoryOverlay.getInventoryPropsTemp(type, items.size());

		Font font = mc.font;
		final int headerHeight = font.lineHeight + 2;
		final int panelW = props.width;
		final int panelH = headerHeight + props.height;

		// Position + scale are configurable so the panel can be moved clear of other mods' overlays
		// (e.g. Jade) and sized to taste.
		final double scale = Math.max(0.1, Configs.Hud.PEEK_SCALE.getDoubleValue());
		final HudAlignment alignment = (HudAlignment) Configs.Hud.PEEK_ALIGNMENT.getOptionListValue();
		final int offX = Configs.Hud.PEEK_OFFSET_X.getIntegerValue();
		final int offY = Configs.Hud.PEEK_OFFSET_Y.getIntegerValue();
		final int screenW = (int) (GuiUtils.getScaledWindowWidth() / scale);
		final int screenH = (int) (GuiUtils.getScaledWindowHeight() / scale);

		int x;
		int y;
		switch (alignment) {
			case TOP_RIGHT -> {
				x = screenW - panelW - offX;
				y = offY;
			}
			case BOTTOM_LEFT -> {
				x = offX;
				y = screenH - panelH - offY;
			}
			case BOTTOM_RIGHT -> {
				x = screenW - panelW - offX;
				y = screenH - panelH - offY;
			}
			case CENTER -> {
				x = (screenW - panelW) / 2;
				y = (screenH - panelH) / 2;
			}
			default -> {
				x = offX;
				y = offY;
			}
		}

		boolean scaled = scale != 1.0;
		if (scaled) {
			ctx.pose().pushMatrix();
			ctx.pose().scale((float) scale, (float) scale);
		}

		String header = mc.level.getBlockState(canonical).getBlock().getName().getString()
				+ "  " + snapshot.totalItems();
		ctx.drawString(font, header, x, y, Configs.Colors.HEADER.getIntegerValue(), true);

		int panelY = y + headerHeight;
		InventoryOverlay.renderInventoryBackground(ctx, type, x, panelY, props.slotsPerRow, props.totalSlots);
		InventoryOverlay.renderItemStacks(ctx, items, x + props.slotOffsetX, panelY + props.slotOffsetY,
				props.slotsPerRow, 0, props.totalSlots);

		if (scaled) {
			ctx.pose().popMatrix();
		}
	}

	/** Re-expands the aggregated cache counts into real stacks (respecting max stack size), capped to the grid. */
	private static NonNullList<ItemStack> expandToStacks(Snapshot snapshot, int maxSlots) {
		NonNullList<ItemStack> items = NonNullList.create();

		for (ItemCount item : snapshot.items()) {
			int remaining = item.count();
			int maxStack = Math.max(1, item.stack().getMaxStackSize());

			while (remaining > 0 && items.size() < maxSlots) {
				int n = Math.min(remaining, maxStack);
				ItemStack stack = item.stack().copy();
				stack.setCount(n);
				items.add(stack);
				remaining -= n;
			}

			if (items.size() >= maxSlots) {
				break;
			}
		}

		return items;
	}
}
