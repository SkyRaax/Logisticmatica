package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;

/**
 * The scrollable list of one container's items, filtered by the search box on
 * {@link GuiContainerContents}. Items come from the owning screen, so a "Refresh" there re-reads the
 * cache and this list follows.
 */
public class WidgetListContainerContents extends WidgetListBase<ItemCount, WidgetItemCountEntry> {
	private final GuiContainerContents gui;
	private String filterText = "";

	public WidgetListContainerContents(int x, int y, int width, int height, GuiContainerContents parent) {
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
	protected Collection<ItemCount> getAllEntries() {
		return this.gui.getItems();
	}

	@Override
	protected List<String> getEntryStringsForFilter(ItemCount entry) {
		return ImmutableList.of(entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT));
	}

	@Override
	protected WidgetItemCountEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd,
			@Nullable ItemCount entry) {
		return new WidgetItemCountEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
				isOdd, entry, listIndex);
	}
}
