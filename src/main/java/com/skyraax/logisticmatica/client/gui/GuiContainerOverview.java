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
import com.skyraax.logisticmatica.client.FocusState;
import com.skyraax.logisticmatica.client.SchematicKey;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * The tracked-container overview: every marked container the mod has looked inside, searchable by
 * coordinates or by the items within. This is the "chest tracker" screen — see what your marked
 * containers hold from anywhere, and answer "where is item X" by typing its name. Selecting a row
 * opens that container's full contents.
 */
public class GuiContainerOverview extends GuiListBase<Snapshot, WidgetContainerEntry, WidgetListContainerOverview> {
	private String filterText = "";
	private boolean focusedOnly;

	public GuiContainerOverview() {
		super(10, 66); // leave room for the navigation tab row + control row

		this.useTitleHierarchy = false;
		this.updateTitle();
	}

	private void updateTitle() {
		ContainerTracker tracker = ContainerTracker.getInstance();
		if (this.focusedOnly && FocusState.getSchematicKey() != null) {
			String key = FocusState.getSchematicKey();
			this.title = StringUtils.translate("logisticmatica.gui.title.container_overview_focused",
					tracker.markedFor(key).size(), SchematicKey.displayName(key));
		} else {
			this.title = StringUtils.translate("logisticmatica.gui.title.container_overview",
					tracker.allMarked().size());
		}
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
		WidgetListContainerOverview list = new WidgetListContainerOverview(
				listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
		list.setFilterText(this.filterText);
		list.setFocusedOnly(this.focusedOnly);
		return list;
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
		x += refresh.getWidth() + 6;
		String scopeKey = this.focusedOnly ? "logisticmatica.gui.button.container.show_all"
				: "logisticmatica.gui.button.container.show_focused";
		ButtonGeneric scope = new ButtonGeneric(x, y, -1, 20, StringUtils.translate(scopeKey));
		scope.setEnabled(FocusState.getSchematicKey() != null);
		this.addButton(scope, new ButtonListener(ButtonListener.Type.SCOPE, this));
		x += scope.getWidth() + 6;
		String visualsKey = Configs.Hud.CONTAINER_VISUALS_ENABLED.getBooleanValue()
				? "logisticmatica.gui.button.container.hide_visuals"
				: "logisticmatica.gui.button.container.show_visuals";
		ButtonGeneric visuals = new ButtonGeneric(x, y, -1, 20, StringUtils.translate(visualsKey));
		this.addButton(visuals, new ButtonListener(ButtonListener.Type.VISUALS, this));

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

			this.gui.filterText = textField.getValueWrapper();
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
			SCOPE,
			VISUALS,
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
				case SCOPE -> {
					this.gui.focusedOnly = !this.gui.focusedOnly;
					this.gui.updateTitle();
					this.gui.initGui();
				}
				case VISUALS -> {
					Configs.Hud.CONTAINER_VISUALS_ENABLED.setBooleanValue(
							!Configs.Hud.CONTAINER_VISUALS_ENABLED.getBooleanValue());
					Configs.saveToFile();
					this.gui.initGui();
				}
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
		}
	}
