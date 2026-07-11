package com.skyraax.logisticmatica.client.gui;

import java.util.List;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * Shows the contents of a single tracked container — the "look inside a marked chest from anywhere"
 * view. The item list is a snapshot taken when the screen opens; "Refresh" re-reads the cache so an
 * open screen can be brought up to date after the container is re-scanned.
 */
public class GuiContainerContents extends GuiListBase<ItemCount, WidgetItemCountEntry, WidgetListContainerContents> {
	private final BlockPos pos;
	private List<ItemCount> items;
	private int totalItems;

	public GuiContainerContents(Snapshot snapshot) {
		super(10, 44);

		this.pos = snapshot.pos();
		this.items = snapshot.items();
		this.totalItems = snapshot.totalItems();
		this.useTitleHierarchy = false;
		this.updateTitle();
	}

	/** The items backing the list; read by {@link WidgetListContainerContents}. */
	public List<ItemCount> getItems() {
		return this.items;
	}

	private void updateTitle() {
		String coords = this.pos.getX() + ", " + this.pos.getY() + ", " + this.pos.getZ();
		this.title = StringUtils.translate("logisticmatica.gui.title.container_contents", coords, this.totalItems);
	}

	@Override
	protected int getBrowserWidth() {
		return this.getScreenWidth() - 20;
	}

	@Override
	protected int getBrowserHeight() {
		return this.getScreenHeight() - 80;
	}

	@Override
	protected WidgetListContainerContents createListWidget(int listX, int listY) {
		return new WidgetListContainerContents(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
	}

	@Override
	public void initGui() {
		super.initGui();

		final int y = 24;
		int x = 12;

		WidgetListContainerContents listWidget = this.getListWidget();
		GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(x, y, 160, 16, this.font);

		if (listWidget != null) {
			searchField.setValueWrapper(listWidget.getFilterTextRaw());
		}

		this.addTextField(searchField, new SearchFieldListener(this), TextFieldType.STRING);
		x += 160 + 6;

		ButtonGeneric refresh = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.button.material_list.refresh"));
		this.addButton(refresh, new ButtonListener(ButtonListener.Type.REFRESH, this));

		String backLabel = StringUtils.translate("logisticmatica.gui.button.back");
		int backWidth = this.getStringWidth(backLabel) + 20;
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - backWidth - 10,
				this.getScreenHeight() - 36, backWidth, 20, backLabel);
		this.addButton(backButton, new ButtonListener(ButtonListener.Type.BACK, this));
	}

	private void refresh() {
		Snapshot snapshot = ContainerData.snapshotOf(this.pos);

		this.items = snapshot != null ? snapshot.items() : List.of();
		this.totalItems = snapshot != null ? snapshot.totalItems() : 0;
		this.updateTitle();
		this.initGui();
	}

	private record SearchFieldListener(GuiContainerContents gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField) {
			WidgetListContainerContents listWidget = this.gui.getListWidget();

			if (listWidget != null) {
				listWidget.setFilterText(textField.getValueWrapper());
				listWidget.refreshEntries();
				listWidget.resetScrollbarPosition();
			}

			return true;
		}
	}

	private record ButtonListener(Type type, GuiContainerContents gui) implements IButtonActionListener {
		private enum Type {
			REFRESH,
			BACK
		}

		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.type) {
				case REFRESH -> this.gui.refresh();
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
	}
}
