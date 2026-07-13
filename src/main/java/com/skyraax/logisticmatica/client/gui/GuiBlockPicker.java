package com.skyraax.logisticmatica.client.gui;

import java.util.function.Consumer;

import net.minecraft.world.level.block.Block;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * A searchable list of every placeable block, in the spirit of an item browser like REI. Selecting a
 * block hands it to the supplied callback and returns to the parent screen. Used to pick a
 * substitution's replacement block.
 */
public class GuiBlockPicker extends GuiListBase<Block, WidgetBlockEntry, WidgetListBlockPicker> {
	private final Consumer<Block> onPick;

	public GuiBlockPicker(String title, Consumer<Block> onPick) {
		super(10, 44);

		this.onPick = onPick;
		this.title = title;
		this.useTitleHierarchy = false;
	}

	/** Called by the list widget when a block is chosen. */
	public void pick(Block block) {
		this.onPick.accept(block);
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
	protected WidgetListBlockPicker createListWidget(int listX, int listY) {
		return new WidgetListBlockPicker(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
	}

	@Override
	public void initGui() {
		super.initGui();

		final int y = 24;
		int x = 12;

		WidgetListBlockPicker listWidget = this.getListWidget();
		GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(x, y, 240, 16, this.font);

		if (listWidget != null) {
			searchField.setValueWrapper(listWidget.getFilterTextRaw());
		}

		this.addTextField(searchField, new SearchFieldListener(this), TextFieldType.STRING);

		String backLabel = StringUtils.translate("logisticmatica.gui.button.back");
		int backWidth = this.getStringWidth(backLabel) + 20;
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - backWidth - 10,
				this.getScreenHeight() - 36, backWidth, 20, backLabel);
		this.addButton(backButton, new BackListener(this));
	}

	private record SearchFieldListener(GuiBlockPicker gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField) {
			WidgetListBlockPicker listWidget = this.gui.getListWidget();

			if (listWidget != null) {
				listWidget.setFilterText(textField.getValueWrapper());
				listWidget.refreshEntries();
				listWidget.resetScrollbarPosition();
			}

			return true;
		}
	}

	private record BackListener(GuiBlockPicker gui) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			GuiBase.openGui(this.gui.getParent());
		}
	}
}
