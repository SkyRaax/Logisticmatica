package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.SubstitutionManager;
import com.skyraax.logisticmatica.client.Substitutions;

/**
 * The scrollable list of a schematic's original blocks on {@link GuiSubstitutions}. Clicking a block
 * substitutes it with the block currently in the player's main hand, or resets it when the hand is
 * empty. The filter matches both the block's name and its current substitute's name.
 */
public class WidgetListSubstitutions extends WidgetListBase<Block, WidgetSubstitutionEntry> {
	private final GuiSubstitutions gui;
	private String filterText = "";

	public WidgetListSubstitutions(int x, int y, int width, int height, GuiSubstitutions parent) {
		super(x, y, width, height, null);

		this.gui = parent;
		this.browserEntryHeight = 22;
	}

	public void setFilterText(String text) {
		this.filterText = text != null ? text : "";
	}

	public String getFilterTextRaw() {
		return this.filterText;
	}

	@Override
	protected boolean hasFilter() {
		return !this.filterText.isEmpty();
	}

	@Override
	protected String getFilterText() {
		return this.filterText.toLowerCase(Locale.ROOT);
	}

	@Override
	protected Collection<Block> getAllEntries() {
		return Substitutions.originalBlocks(this.gui.getSchematic());
	}

	@Override
	protected List<String> getEntryStringsForFilter(Block entry) {
		Block target = SubstitutionManager.getInstance().getSubstitute(this.gui.getSchematic(), entry);
		String name = entry.getName().getString().toLowerCase(Locale.ROOT);

		return target != null
				? ImmutableList.of(name, target.getName().getString().toLowerCase(Locale.ROOT))
				: ImmutableList.of(name);
	}

	@Override
	protected boolean onEntryClicked(@Nullable Block entry, int index) {
		if (entry == null) {
			return true;
		}

		Minecraft mc = Minecraft.getInstance();
		ItemStack held = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;
		Block heldBlock = held.isEmpty() ? Blocks.AIR : Block.byItem(held.getItem());

		// Held block substitutes; an empty hand (or the same block) resets the substitution.
		Block replacement = (heldBlock != Blocks.AIR && heldBlock != entry) ? heldBlock : null;
		SubstitutionManager.getInstance().setSubstitute(this.gui.getSchematic(), entry, replacement);

		this.refreshEntries();
		return true;
	}

	@Override
	protected WidgetSubstitutionEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd,
			@Nullable Block entry) {
		return new WidgetSubstitutionEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
				isOdd, entry, this.gui.getSchematic(), listIndex);
	}
}
