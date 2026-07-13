package com.skyraax.logisticmatica.client.gui;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

import com.skyraax.logisticmatica.client.Substitutions;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * One block in {@link WidgetListBlockPicker}: its icon, display name and (dimmed) registry id.
 * Selecting it is handled by the list widget.
 */
public class WidgetBlockEntry extends WidgetListEntryBase<Block> {
	@Nullable private final Block block;
	private final boolean isOdd;

	public WidgetBlockEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable Block block, int listIndex) {
		super(x, y, width, height, block, listIndex);

		this.block = block;
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

		if (this.block != null) {
			int iconX = this.x + 4;
			int iconY = this.y + 3;
			int textY = this.y + 7;

			RenderUtils.drawRect(ctx, iconX, iconY, 16, 16, 0x20FFFFFF);
			ctx.renderItem(new ItemStack(this.block), iconX, iconY);

			this.drawString(ctx, iconX + 20, textY, Configs.Colors.TEXT.getIntegerValue(),
					this.block.getName().getString());

			String id = Substitutions.idOf(this.block);
			int idX = this.x + this.width - this.getStringWidth(id) - 6;
			this.drawString(ctx, idX, textY, 0xFF808080, id);
		}

		super.render(ctx, mouseX, mouseY, selected);
	}
}
