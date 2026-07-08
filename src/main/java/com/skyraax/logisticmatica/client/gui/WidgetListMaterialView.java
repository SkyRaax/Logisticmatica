package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import fi.dy.masa.litematica.materials.MaterialListEntry;

/**
 * The scrollable material list. Entries come straight from the owning screen's
 * {@link fi.dy.masa.litematica.materials.MaterialListBase}, and filtering is driven by the
 * search text field on {@link GuiMaterialListView} (which feeds {@link #setFilterText(String)}).
 * <p>
 * Filtering reuses MaLiLib's built-in machinery in {@code WidgetListBase.refreshBrowserEntries()}:
 * we override {@link #hasFilter()} / {@link #getFilterText()} to expose our own filter string and
 * {@link #getEntryStringsForFilter(MaterialListEntry)} to match against the item display name.
 */
public class WidgetListMaterialView extends WidgetListBase<MaterialListEntry, WidgetMaterialEntry>
{
	private final GuiMaterialListView gui;
	private String filterText = "";

	public WidgetListMaterialView(int x, int y, int width, int height, GuiMaterialListView parent)
	{
		super(x, y, width, height, null);

		this.gui = parent;
		this.browserEntryHeight = 22;
	}

	/** Sets the raw (as-typed) filter string. Matching is case-insensitive (see {@link #getFilterText()}). */
	public void setFilterText(String text)
	{
		this.filterText = text != null ? text : "";
	}

	/** The raw filter text as typed, used to restore the search field across re-inits. */
	public String getFilterTextRaw()
	{
		return this.filterText;
	}

	@Override
	protected boolean hasFilter()
	{
		return this.filterText.isEmpty() == false;
	}

	@Override
	protected String getFilterText()
	{
		// Entry strings (see below) are lower-cased, so lower-case the filter for case-insensitive matching.
		return this.filterText.toLowerCase(Locale.ROOT);
	}

	@Override
	protected Collection<MaterialListEntry> getAllEntries()
	{
		// true -> let MaterialListBase refresh its filtered ("hide available") sub-list as needed.
		return this.gui.getMaterialList().getMaterialsFiltered(true);
	}

	@Override
	protected List<String> getEntryStringsForFilter(MaterialListEntry entry)
	{
		ItemStack stack = entry.getStack();
		return ImmutableList.of(stack.getHoverName().getString().toLowerCase(Locale.ROOT));
	}

	@Override
	protected WidgetMaterialEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, @Nullable MaterialListEntry entry)
	{
		return new WidgetMaterialEntry(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(entry),
				isOdd, entry, listIndex);
	}
}
