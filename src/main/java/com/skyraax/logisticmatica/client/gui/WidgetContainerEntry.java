package com.skyraax.logisticmatica.client.gui;

import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.SchematicColors;
import com.skyraax.logisticmatica.client.SchematicKey;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * A single container in {@link WidgetListContainerOverview}: two lines carrying the container's
 * coordinates and distance, a summary of its most-plentiful items, its representative icon, and a
 * right-aligned total item count. Clicking the row (handled by the list widget) opens the full
 * contents.
 */
public class WidgetContainerEntry extends WidgetListEntryBase<Snapshot> {
	/** How many item names to spell out in the summary line before collapsing the rest into "+N more". */
	private static final int SUMMARY_ITEMS = 3;

	@Nullable private final Snapshot snapshot;
	private final boolean isOdd;

	public WidgetContainerEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable Snapshot snapshot, int listIndex) {
		super(x, y, width, height, snapshot, listIndex);

		this.snapshot = snapshot;
		this.isOdd = isOdd;
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		if (selected || this.isMouseOver(mouseX, mouseY)) {
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0707070);
		} else if (this.isOdd) {
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0101010);
		} else {
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0303030);
		}

		if (this.snapshot == null) {
			super.render(ctx, mouseX, mouseY, selected);
			return;
		}

		int iconX = this.x + 4;
		int iconY = this.y + 11;

		if (!this.snapshot.items().isEmpty()) {
			RenderUtils.drawRect(ctx, iconX, iconY, 16, 16, 0x20FFFFFF);
			ctx.renderItem(this.snapshot.items().get(0).stack(), iconX, iconY);
		}

		int textX = iconX + 22;
		BlockPos pos = this.snapshot.pos();
		String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
		String total = StringUtils.translate("logisticmatica.gui.label.container.items", this.snapshot.totalItems());
		int totalX = this.x + this.width - this.getStringWidth(total) - 6;
		String schematicKey = this.snapshot.schematicKey();
		String schematicName = schematicKey != null ? SchematicKey.displayName(schematicKey)
				: StringUtils.translate("logisticmatica.gui.container.unassigned");
		int schematicColor = schematicKey != null ? SchematicColors.argb(schematicKey) : 0xFFAAAAAA;
		int coordsWidth = this.getStringWidth(coords);
		int schematicMaxWidth = Math.max(48, totalX - textX - coordsWidth - 22);
		String shownSchematic = this.ellipsize(schematicName, schematicMaxWidth);
		RenderUtils.drawRect(ctx, textX, this.y + 5, 4, 9, schematicColor);
		this.drawString(ctx, textX + 8, this.y + 4, schematicColor, shownSchematic);
		int coordsX = textX + 14 + this.getStringWidth(shownSchematic);
		this.drawString(ctx, coordsX, this.y + 4, Configs.Colors.TEXT.getIntegerValue(), coords);
		this.drawString(ctx, totalX, this.y + 4, Configs.Colors.HEADER.getIntegerValue(), total);

		// Bottom line: distance from the player, then a summary of the most-plentiful items.
		this.drawString(ctx, textX, this.y + 22, 0xFFAAAAAA, this.distanceAndSummary(pos));

		super.render(ctx, mouseX, mouseY, selected);
	}

	private String ellipsize(String value, int maximumWidth) {
		if (this.getStringWidth(value) <= maximumWidth) return value;
		String suffix = "...";
		int end = value.length();
		while (end > 1 && this.getStringWidth(value.substring(0, end) + suffix) > maximumWidth) end--;
		return value.substring(0, end) + suffix;
	}

	private String distanceAndSummary(BlockPos pos) {
		StringBuilder builder = new StringBuilder();

		Vec3 eye = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : null;
		if (eye != null) {
			long blocks = Math.round(Math.sqrt(
					Math.pow(pos.getX() + 0.5 - eye.x, 2)
							+ Math.pow(pos.getY() + 0.5 - eye.y, 2)
							+ Math.pow(pos.getZ() + 0.5 - eye.z, 2)));
			builder.append(StringUtils.translate("logisticmatica.gui.label.container.distance", blocks)).append("  -  ");
		}

		List<ItemCount> items = this.snapshot.items();
		int shown = Math.min(SUMMARY_ITEMS, items.size());

		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				builder.append(", ");
			}

			ItemCount item = items.get(i);
			builder.append(item.count()).append(' ').append(item.stack().getHoverName().getString());
		}

		int remaining = items.size() - shown;
		if (remaining > 0) {
			builder.append(' ').append(StringUtils.translate("logisticmatica.gui.label.container.more", remaining));
		}

		return builder.toString();
	}
}
