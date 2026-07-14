package com.skyraax.logisticmatica.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.ContainerTracker;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * The tracked-container overview: every marked container the mod has looked inside, searchable by
 * coordinates or by the items within. This is the "chest tracker" screen — see what your marked
 * containers hold from anywhere, and answer "where is item X" by typing its name. Selecting a row
 * opens that container's full contents.
 */
public class GuiContainerOverview extends GuiListBase<Snapshot, WidgetContainerEntry, WidgetListContainerOverview> {
	public GuiContainerOverview() {
		super(10, 66); // leave room for the navigation tab row + control row

		this.useTitleHierarchy = false;
		this.updateTitle();
	}

	private void updateTitle() {
		int marked = ContainerTracker.getInstance().getMarked().size();
		this.title = StringUtils.translate("logisticmatica.gui.title.container_overview", marked);
	}

	@Override
	protected int getBrowserWidth() {
		return this.getScreenWidth() - 20;
	}

	@Override
	protected int getBrowserHeight() {
		return this.getScreenHeight() - 102;
	}

	@Override
	protected WidgetListContainerOverview createListWidget(int listX, int listY) {
		return new WidgetListContainerOverview(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
	}

	@Override
	public void initGui() {
		super.initGui();

		NavBar.add(this, NavBar.Tab.CONTAINERS);

		final int y = 44;
		int x = 12;

		WidgetListContainerOverview listWidget = this.getListWidget();
		GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(x, y, 200, 16, this.font);

		if (listWidget != null) {
			searchField.setValueWrapper(listWidget.getFilterTextRaw());
		}

		this.addTextField(searchField, new SearchFieldListener(this), TextFieldType.STRING);
		x += 200 + 6;

		ButtonGeneric refresh = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.button.material_list.refresh"));
		this.addButton(refresh, new ButtonListener(ButtonListener.Type.REFRESH, this));

		String backLabel = StringUtils.translate("logisticmatica.gui.button.back");
		int backWidth = this.getStringWidth(backLabel) + 20;
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - backWidth - 10,
				this.getScreenHeight() - 36, backWidth, 20, backLabel);
		this.addButton(backButton, new ButtonListener(ButtonListener.Type.BACK, this));
	}

	private record SearchFieldListener(GuiContainerOverview gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField) {
			WidgetListContainerOverview listWidget = this.gui.getListWidget();

			if (listWidget != null) {
				listWidget.setFilterText(textField.getValueWrapper());
				listWidget.refreshEntries();
				listWidget.resetScrollbarPosition();
			}

			return true;
		}
	}

	private record ButtonListener(Type type, GuiContainerOverview gui) implements IButtonActionListener {
		private enum Type {
			REFRESH,
			BACK
		}

		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.type) {
				case REFRESH -> {
					this.gui.updateTitle();

					WidgetListContainerOverview listWidget = this.gui.getListWidget();
					if (listWidget != null) {
						listWidget.refreshEntries();
					}
				}
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
	}
}
