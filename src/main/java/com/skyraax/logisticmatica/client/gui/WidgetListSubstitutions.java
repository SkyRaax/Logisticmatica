package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import com.skyraax.logisticmatica.client.SubstitutionManager;
import com.skyraax.logisticmatica.client.Substitutions;

/**
 * The scrollable list of a schematic's original blocks on {@link GuiSubstitutions}. Row clicks (open
 * the picker / reset) are handled by {@link WidgetSubstitutionEntry} itself, since they depend on
 * where in the row the click lands. The filter matches both a block's name and its substitute's name.
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
	protected WidgetSubstitutionEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd,
			@Nullable Block entry) {
		return new WidgetSubstitutionEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
				isOdd, entry, this.gui, listIndex);
	}
}
