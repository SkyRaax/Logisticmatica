package com.skyraax.logisticmatica.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.ContainerLocator;
import com.skyraax.logisticmatica.client.ContainerTracker;
import com.skyraax.logisticmatica.client.SchematicColors;
import com.skyraax.logisticmatica.client.SchematicKey;
import com.skyraax.logisticmatica.client.config.Configs;
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
	private final GuiContainerOverview gui;
	private final boolean isOdd;

	public WidgetContainerEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable Snapshot snapshot, GuiContainerOverview gui, int listIndex) {
		super(x, y, width, height, snapshot, listIndex);

		this.snapshot = snapshot;
		this.gui = gui;
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
		String schematicKey = this.snapshot.schematicKey();
		boolean visualsVisible = ContainerTracker.getInstance().isVisualsVisible(schematicKey, pos);
		boolean locating = schematicKey != null && ContainerLocator.matches(schematicKey, pos);
		String visualStatus = StringUtils.translate(visualsVisible
				? "logisticmatica.gui.container.visuals.visible"
				: "logisticmatica.gui.container.visuals.hidden");
		String findStatus = StringUtils.translate(locating
				? "logisticmatica.gui.container.finding"
				: "logisticmatica.gui.container.find");
		int visualX = this.visibilityRegionStart(visualStatus);
		int findX = this.findRegionStart(visualStatus, findStatus);
		int totalX = findX - this.getStringWidth(total) - 12;
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
		this.drawString(ctx, findX, this.y + 4, locating ? 0xFFFFFF55 : 0xFF55FFFF, findStatus);
		this.drawString(ctx, visualX, this.y + 4, visualsVisible ? 0xFF55FF55 : 0xFFAAAAAA, visualStatus);

		// Bottom line: authoritative sync health, age, distance, then the item summary.
		String status = this.statusLabel();
		String details = status + "  -  " + this.updatedAgo() + "  -  " + this.distanceAndSummary(pos);
		this.drawString(ctx, textX, this.y + 22, this.statusColor(), details);

		super.render(ctx, mouseX, mouseY, selected);
	}

	private int visibilityRegionStart(String label) {
		return this.x + this.width - this.getStringWidth(label) - 8;
	}

	private int findRegionStart(String visualLabel, String findLabel) {
		return this.visibilityRegionStart(visualLabel) - this.getStringWidth(findLabel) - 12;
	}

	@Override
	protected boolean onMouseClickedImpl(MouseButtonEvent click, boolean doubleClick) {
		if (this.snapshot == null || this.snapshot.schematicKey() == null) return false;
		String schematicKey = this.snapshot.schematicKey();
		BlockPos pos = this.snapshot.pos();
		boolean visible = ContainerTracker.getInstance().isVisualsVisible(
				schematicKey, pos);
		String visualLabel = StringUtils.translate(visible
				? "logisticmatica.gui.container.visuals.visible"
				: "logisticmatica.gui.container.visuals.hidden");
		boolean locating = ContainerLocator.matches(schematicKey, pos);
		String findLabel = StringUtils.translate(locating
				? "logisticmatica.gui.container.finding"
				: "logisticmatica.gui.container.find");
		int visualX = this.visibilityRegionStart(visualLabel);
		int findX = this.findRegionStart(visualLabel, findLabel);
		if (click.x() >= visualX) {
			ContainerTracker.getInstance().toggleVisuals(schematicKey, pos);
		} else if (click.x() >= findX) {
			ContainerLocator.highlight(schematicKey, pos);
			GuiBase.openGui(null);
			InfoUtils.showGuiOrInGameMessage(MessageType.INFO,
					"logisticmatica.message.container.finding",
					SchematicKey.displayName(schematicKey), pos.getX(), pos.getY(), pos.getZ());
		} else {
			GuiContainerContents contents = new GuiContainerContents(this.snapshot);
			contents.setParent(this.gui);
			GuiBase.openGui(contents);
		}
		return true;
	}

	@Override
	public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		super.postRenderHovered(ctx, mouseX, mouseY, selected);
		if (this.snapshot == null || !this.isMouseOver(mouseX, mouseY)) return;
		List<String> lines = new ArrayList<>();
		lines.add(this.statusLabel());
		if (this.snapshot.lastUpdatedEpochMillis() > 0L) {
			String exact = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
					.withZone(ZoneId.systemDefault())
					.format(Instant.ofEpochMilli(this.snapshot.lastUpdatedEpochMillis()));
			lines.add(StringUtils.translate("logisticmatica.gui.container.last_updated", exact));
		} else {
			lines.add(StringUtils.translate("logisticmatica.gui.container.updated.never"));
		}
		UUID projectId = SchematicKey.projectId(this.snapshot.schematicKey());
		if (projectId != null) {
			lines.add(StringUtils.translate("logisticmatica.gui.container.project_id", projectId));
		}
		RenderUtils.drawHoverText(ctx, mouseX, mouseY, lines);
	}

	private String ellipsize(String value, int maximumWidth) {
		if (this.getStringWidth(value) <= maximumWidth) return value;
		String suffix = "...";
		int end = value.length();
		while (end > 1 && this.getStringWidth(value.substring(0, end) + suffix) > maximumWidth) end--;
		return value.substring(0, end) + suffix;
	}

	private String statusLabel() {
		return StringUtils.translate("logisticmatica.gui.container.status."
				+ this.snapshot.syncStatus().name().toLowerCase(Locale.ROOT));
	}

	private int statusColor() {
		return switch (this.snapshot.syncStatus()) {
			case SYNCED -> 0xFF55FF55;
			case PENDING, UNLOADED -> 0xFFFFAA00;
			case MISSING -> 0xFFFF5555;
			case TOO_COMPLEX -> 0xFFFF55FF;
		};
	}

	private String updatedAgo() {
		long updated = this.snapshot.lastUpdatedEpochMillis();
		if (updated <= 0L) {
			return StringUtils.translate("logisticmatica.gui.container.updated.never");
		}
		long seconds = Math.max(0L, (System.currentTimeMillis() - updated) / 1_000L);
		if (seconds < 5L) {
			return StringUtils.translate("logisticmatica.gui.container.updated.now");
		}
		if (seconds < 60L) {
			return StringUtils.translate("logisticmatica.gui.container.updated.seconds", seconds);
		}
		long minutes = seconds / 60L;
		if (minutes < 60L) {
			return StringUtils.translate("logisticmatica.gui.container.updated.minutes", minutes);
		}
		long hours = minutes / 60L;
		if (hours < 24L) {
			return StringUtils.translate("logisticmatica.gui.container.updated.hours", hours);
		}
		return StringUtils.translate("logisticmatica.gui.container.updated.days", hours / 24L);
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
