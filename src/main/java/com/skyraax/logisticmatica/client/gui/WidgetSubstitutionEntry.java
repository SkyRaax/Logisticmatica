package com.skyraax.logisticmatica.client.gui;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.SubstitutionManager;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * One original schematic block in {@link WidgetListSubstitutions}: its icon and name, and — when it
 * is substituted — an arrow to the replacement block. Clicking the row (handled by the list widget)
 * sets or clears the substitution using the item in hand.
 */
public class WidgetSubstitutionEntry extends WidgetListEntryBase<Block> {
	@Nullable private final Block block;
	private final LitematicaSchematic schematic;
	private final boolean isOdd;

	public WidgetSubstitutionEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable Block block, LitematicaSchematic schematic, int listIndex) {
		super(x, y, width, height, block, listIndex);

		this.block = block;
		this.schematic = schematic;
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

			Block target = SubstitutionManager.getInstance().getSubstitute(this.schematic, this.block);

			if (target != null) {
				String arrow = StringUtils.translate("logisticmatica.gui.label.substitution.arrow",
						target.getName().getString());
				int arrowX = this.x + this.width - this.getStringWidth(arrow) - 6;
				this.drawString(ctx, arrowX, textY, Configs.Colors.HAVE.getIntegerValue(), arrow);
			}
		}

		super.render(ctx, mouseX, mouseY, selected);
	}
}
