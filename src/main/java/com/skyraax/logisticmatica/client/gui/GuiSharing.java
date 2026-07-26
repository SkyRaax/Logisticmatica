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

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Searchable overview and entry point for all server-authoritative shared projects. */
public class GuiSharing extends GuiListBase<SharedProjectView, WidgetSharedProjectEntry, WidgetListSharedProjects>
		implements SharingRefreshable {
	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private String filterText = "";

	public GuiSharing() {
		super(10, 68);
		this.useTitleHierarchy = false;
		this.updateTitle();
	}

	private void updateTitle() {
		String status = this.sharing.serverAvailable()
				? StringUtils.translate("logisticmatica.gui.share.connected", this.sharing.serverVersion())
				: StringUtils.translate("logisticmatica.gui.share.unavailable");
		this.title = StringUtils.translate("logisticmatica.gui.title.sharing", status);
	}

	@Override protected int getBrowserWidth() { return this.getScreenWidth() - 20; }
	@Override protected int getBrowserHeight() { return this.getScreenHeight() - 104; }

	@Override
	protected WidgetListSharedProjects createListWidget(int listX, int listY) {
		WidgetListSharedProjects list = new WidgetListSharedProjects(
				listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
		list.setFilterText(this.filterText);
		return list;
	}

	@Override
	public void initGui() {
		super.initGui();
		NavBar.add(this, NavBar.Tab.SHARING);
		int x = 12;
		int y = 46;
		WidgetListSharedProjects list = this.getListWidget();
		GuiTextFieldGeneric search = new GuiTextFieldGeneric(x, y, 180, 16, this.font);
		search.setValueWrapper(this.filterText);
		this.addTextField(search, new SearchListener(this), TextFieldType.STRING);
		x += 186;

		ButtonGeneric refresh = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.button.material_list.refresh"));
		refresh.setEnabled(this.sharing.serverAvailable());
		this.addButton(refresh, new ActionListener(Action.REFRESH, this));
		x += refresh.getWidth() + 4;

		ButtonGeneric share = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.share_selected"));
		share.setEnabled(this.sharing.serverAvailable());
		this.addButton(share, new ActionListener(Action.SHARE, this));

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back);
		this.addButton(backButton, new ActionListener(Action.BACK, this));
	}

	@Override
	public void refreshSharing() {
		this.updateTitle();
		this.initGui();
	}

	private record SearchListener(GuiSharing gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override public boolean onTextChange(GuiTextFieldGeneric field) {
			WidgetListSharedProjects list = this.gui.getListWidget();
			this.gui.filterText = field.getValueWrapper();
			if (list != null) {
				list.setFilterText(field.getValueWrapper());
				list.refreshEntries();
				list.resetScrollbarPosition();
			}
			return true;
		}
	}

	private enum Action { REFRESH, SHARE, BACK }
	private record ActionListener(Action action, GuiSharing gui) implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.action) {
				case REFRESH -> this.gui.sharing.refreshProjects();
				case SHARE -> this.gui.sharing.shareSelectedPlacement();
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
	}
}
