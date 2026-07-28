package com.skyraax.logisticmatica.client.gui;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

import fi.dy.masa.litematica.materials.MaterialListEntry;

import com.skyraax.logisticmatica.client.MaterialView;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * A single row in {@link WidgetListMaterialView}: the item icon, the item display name, and a
 * right-aligned "have / need" count. The count is coloured with the mod's configured HAVE colour
 * when the player already has enough, otherwise the MISSING colour. Row backgrounds alternate
 * (and lighten on hover/selection) to match Litematica's material-list rows.
 */
public class WidgetMaterialEntry extends WidgetListEntryBase<MaterialListEntry>
{
	@Nullable private final MaterialListEntry entry;
	private final boolean isOdd;

	public WidgetMaterialEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable MaterialListEntry entry, int listIndex)
	{
		super(x, y, width, height, entry, listIndex);

		this.entry = entry;
		this.isOdd = isOdd;
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
	{
		// Row background: lighter when hovered/selected, otherwise alternating dark shades.
		if (selected || this.isMouseOver(mouseX, mouseY))
		{
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0707070);
		}
		else if (this.isOdd)
		{
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0101010);
		}
		else
		{
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0303030);
		}

		if (this.entry != null)
		{
			ItemStack stack = this.entry.getStack();
			int textColor = Configs.Colors.TEXT.getIntegerValue();
			int iconX = this.x + 4;
			int iconY = this.y + 3;
			int textY = this.y + 7;

			int textX = iconX;
			if (Configs.Hud.SHOW_ITEM_ICONS.getBooleanValue()) {
				// Item icon, with a faint backing square like Litematica's rows.
				RenderUtils.drawRect(ctx, iconX, iconY, 16, 16, 0x20FFFFFF);
				ctx.renderItem(stack, iconX, iconY);
				textX += 20;
			}

			this.drawString(ctx, textX, textY, textColor, stack.getHoverName().getString());

			// Right-aligned "have / need" count, coloured by availability.
			int available = this.entry.getCountAvailable();
			int total = MaterialView.target(this.entry);
			String counts = available + " / " + total;
			int countColor = available >= total
					? Configs.Colors.HAVE.getIntegerValue()
					: Configs.Colors.MISSING.getIntegerValue();
			int countX = this.x + this.width - this.getStringWidth(counts) - 6;
			this.drawString(ctx, countX, textY, countColor, counts);
		}

		super.render(ctx, mouseX, mouseY, selected);
	}
}
