package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import com.skyraax.logisticmatica.client.Substitutions;

/**
 * The scrollable, searchable list of all placeable blocks on {@link GuiBlockPicker}. Selecting one
 * hands it to the picker's callback. The filter matches both the block's display name and its id.
 */
public class WidgetListBlockPicker extends WidgetListBase<Block, WidgetBlockEntry> {
	private final GuiBlockPicker gui;
	private String filterText = "";

	public WidgetListBlockPicker(int x, int y, int width, int height, GuiBlockPicker parent) {
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
		return Substitutions.substitutableBlocks();
	}

	@Override
	protected List<String> getEntryStringsForFilter(Block entry) {
		return ImmutableList.of(
				entry.getName().getString().toLowerCase(Locale.ROOT),
				Substitutions.idOf(entry).toLowerCase(Locale.ROOT));
	}

	@Override
	protected boolean onEntryClicked(@Nullable Block entry, int index) {
		if (entry != null) {
			this.gui.pick(entry);
			GuiBase.openGui(this.gui.getParent());
		}

		return true;
	}

	@Override
	protected WidgetBlockEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd,
			@Nullable Block entry) {
		return new WidgetBlockEntry(x, y, this.browserEntryWidth, this.browserEntryHeight, isOdd, entry, listIndex);
	}
}
