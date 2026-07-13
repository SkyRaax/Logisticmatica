package com.skyraax.logisticmatica.client.gui;

import javax.annotation.Nullable;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.SubstitutionManager;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * One original schematic block in {@link WidgetListSubstitutions}: its icon and name, plus — when it
 * is substituted — an arrow to the replacement and a reset button. Left-clicking the row opens the
 * block picker to choose (or change) the replacement; clicking the reset button clears just this
 * material. The row's substitution state is read live, so it reflects changes without a refresh.
 */
public class WidgetSubstitutionEntry extends WidgetListEntryBase<Block> {
	private static final String RESET_LABEL = "[x]";

	@Nullable private final Block block;
	private final GuiSubstitutions gui;
	private final boolean isOdd;

	public WidgetSubstitutionEntry(int x, int y, int width, int height, boolean isOdd,
			@Nullable Block block, GuiSubstitutions gui, int listIndex) {
		super(x, y, width, height, block, listIndex);

		this.block = block;
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

		if (this.block == null) {
			super.render(ctx, mouseX, mouseY, selected);
			return;
		}

		int iconX = this.x + 4;
		int iconY = this.y + 3;
		int textY = this.y + 7;

		RenderUtils.drawRect(ctx, iconX, iconY, 16, 16, 0x20FFFFFF);
		ctx.renderItem(new ItemStack(this.block), iconX, iconY);
		this.drawString(ctx, iconX + 20, textY, Configs.Colors.TEXT.getIntegerValue(),
				this.block.getName().getString());

		Block target = SubstitutionManager.getInstance().getSubstitute(this.gui.getSchematic(), this.block);

		if (target != null) {
			// Reset button on the far right, then the "-> replacement" arrow to its left.
			int resetX = this.resetRegionStart();
			this.drawString(ctx, resetX + 4, textY, Configs.Colors.MISSING.getIntegerValue(), RESET_LABEL);

			String arrow = StringUtils.translate("logisticmatica.gui.label.substitution.arrow",
					target.getName().getString());
			this.drawString(ctx, resetX - this.getStringWidth(arrow) - 8, textY,
					Configs.Colors.HAVE.getIntegerValue(), arrow);
		}

		super.render(ctx, mouseX, mouseY, selected);
	}

	/** X of the reset button's left edge; a click at or past it (when substituted) resets the row. */
	private int resetRegionStart() {
		return this.x + this.width - this.getStringWidth(RESET_LABEL) - 8;
	}

	@Override
	protected boolean onMouseClickedImpl(MouseButtonEvent click, boolean doubleClick) {
		if (this.block == null) {
			return false;
		}

		Block target = SubstitutionManager.getInstance().getSubstitute(this.gui.getSchematic(), this.block);

		if (target != null && click.x() >= this.resetRegionStart()) {
			SubstitutionManager.getInstance().setSubstitute(this.gui.getSchematic(), this.block, null);
		} else {
			this.gui.openPicker(this.block);
		}

		return true;
	}
}
