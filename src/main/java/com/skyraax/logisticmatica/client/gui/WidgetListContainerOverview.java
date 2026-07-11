package com.skyraax.logisticmatica.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * The scrollable list of marked containers on {@link GuiContainerOverview}. The filter matches both a
 * container's coordinates and the names of the items inside it, so typing an item name answers
 * "which of my containers hold X". Clicking a row opens that container's full contents.
 */
public class WidgetListContainerOverview extends WidgetListBase<Snapshot, WidgetContainerEntry> {
	private final GuiContainerOverview gui;
	private String filterText = "";

	public WidgetListContainerOverview(int x, int y, int width, int height, GuiContainerOverview parent) {
		super(x, y, width, height, null);

		this.gui = parent;
		this.browserEntryHeight = 30;
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
	protected Collection<Snapshot> getAllEntries() {
		return ContainerData.collectMarked();
	}

	@Override
	protected List<String> getEntryStringsForFilter(Snapshot entry) {
		List<String> strings = new ArrayList<>(entry.items().size() + 1);
		strings.add((entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ()));

		for (ItemCount item : entry.items()) {
			strings.add(item.stack().getHoverName().getString().toLowerCase(Locale.ROOT));
		}

		return strings;
	}

	@Override
	protected boolean onEntryClicked(@Nullable Snapshot entry, int index) {
		if (entry != null) {
			GuiContainerContents contents = new GuiContainerContents(entry);
			contents.setParent(this.gui);
			GuiBase.openGui(contents);
		}

		return true;
	}

	@Override
	protected WidgetContainerEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd,
			@Nullable Snapshot entry) {
		return new WidgetContainerEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
				isOdd, entry, listIndex);
	}
}
