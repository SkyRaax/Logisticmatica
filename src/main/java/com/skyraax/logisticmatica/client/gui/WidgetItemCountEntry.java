package com.skyraax.logisticmatica.client.gui;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;

/**
 * A single row in a container's contents list: the item icon, its display name and a right-aligned
 * amount. Deliberately simple — a container's contents are a flat "how many of each item" list, so
 * unlike {@link WidgetMaterialEntry} there is no have/need comparison, just the count.
 */
public class WidgetItemCountEntry extends WidgetListEntryBase<ItemCount> {
	@Nullable private final ItemCount item;
	private final boolean isOdd;

	public WidgetItemCountEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable ItemCount item, int listIndex) {
		super(x, y, width, height, item, listIndex);

		this.item = item;
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

		if (this.item != null) {
			ItemStack stack = this.item.stack();
			int textColor = Configs.Colors.TEXT.getIntegerValue();
			int iconX = this.x + 4;
			int iconY = this.y + 3;
			int textY = this.y + 7;

			RenderUtils.drawRect(ctx, iconX, iconY, 16, 16, 0x20FFFFFF);
			ctx.renderItem(stack, iconX, iconY);

			this.drawString(ctx, iconX + 20, textY, textColor, stack.getHoverName().getString());

			String count = String.valueOf(this.item.count());
			int countX = this.x + this.width - this.getStringWidth(count) - 6;
			this.drawString(ctx, countX, textY, textColor, count);
		}

		super.render(ctx, mouseX, mouseY, selected);
	}
}
