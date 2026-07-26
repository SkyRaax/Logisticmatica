package com.skyraax.logisticmatica.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Scrollable, filterable list backing {@link GuiSharing}. */
public class WidgetListSharedProjects extends WidgetListBase<SharedProjectView, WidgetSharedProjectEntry> {
	private final GuiSharing gui;
	private String filterText = "";

	public WidgetListSharedProjects(int x, int y, int width, int height, GuiSharing gui) {
		super(x, y, width, height, null);
		this.gui = gui;
		this.browserEntryHeight = 34;
	}

	public void setFilterText(String text) { this.filterText = text != null ? text : ""; }
	public String getFilterTextRaw() { return this.filterText; }
	@Override protected boolean hasFilter() { return !this.filterText.isEmpty(); }
	@Override protected String getFilterText() { return this.filterText.toLowerCase(Locale.ROOT); }
	@Override protected Collection<SharedProjectView> getAllEntries() {
		return ClientShareManager.getInstance().projects();
	}
	@Override protected List<String> getEntryStringsForFilter(SharedProjectView project) {
		return List.of(project.name(), project.ownerName(), project.dimension());
	}
	@Override protected boolean onEntryClicked(@Nullable SharedProjectView project, int index) {
		if (project != null) {
			GuiSharedProjectDetails details = new GuiSharedProjectDetails(project.id());
			details.setParent(this.gui);
			GuiBase.openGui(details);
		}
		return true;
	}
	@Override protected WidgetSharedProjectEntry createListEntryWidget(int x, int y, int listIndex,
			boolean isOdd, @Nullable SharedProjectView project) {
		return new WidgetSharedProjectEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
				isOdd, project, listIndex);
	}
}
