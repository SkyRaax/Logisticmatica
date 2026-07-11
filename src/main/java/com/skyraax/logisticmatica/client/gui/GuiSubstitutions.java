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

import net.minecraft.world.level.block.Block;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/**
 * Edits the material substitutions for one schematic. The list shows the schematic's original blocks;
 * hold the replacement block and click a row to substitute it (empty hand resets). The swap is a
 * non-destructive overlay — the ghost render, verifier and material list follow it, the file does not
 * change.
 */
public class GuiSubstitutions extends GuiListBase<Block, WidgetSubstitutionEntry, WidgetListSubstitutions> {
	private final LitematicaSchematic schematic;

	public GuiSubstitutions(LitematicaSchematic schematic) {
		super(10, 44);

		this.schematic = schematic;
		this.useTitleHierarchy = false;
		this.title = StringUtils.translate("logisticmatica.gui.title.substitutions",
				schematic.getMetadata().getName());
	}

	public LitematicaSchematic getSchematic() {
		return this.schematic;
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
	protected WidgetListSubstitutions createListWidget(int listX, int listY) {
		return new WidgetListSubstitutions(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
	}

	@Override
	public void initGui() {
		super.initGui();

		final int y = 24;
		int x = 12;

		WidgetListSubstitutions listWidget = this.getListWidget();
		GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(x, y, 160, 16, this.font);

		if (listWidget != null) {
			searchField.setValueWrapper(listWidget.getFilterTextRaw());
		}

		this.addTextField(searchField, new SearchFieldListener(this), TextFieldType.STRING);
		x += 160 + 8;

		// Usage hint, so it is obvious the held item is what does the substituting.
		String hint = StringUtils.translate("logisticmatica.gui.label.substitution.hint");
		this.addLabel(x, y + 4, this.getStringWidth(hint), 12, 0xFFAAAAAA, hint);

		String backLabel = StringUtils.translate("logisticmatica.gui.button.back");
		int backWidth = this.getStringWidth(backLabel) + 20;
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - backWidth - 10,
				this.getScreenHeight() - 36, backWidth, 20, backLabel);
		this.addButton(backButton, new BackListener(this));
	}

	private record SearchFieldListener(GuiSubstitutions gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField) {
			WidgetListSubstitutions listWidget = this.gui.getListWidget();

			if (listWidget != null) {
				listWidget.setFilterText(textField.getValueWrapper());
				listWidget.refreshEntries();
				listWidget.resetScrollbarPosition();
			}

			return true;
		}
	}

	private record BackListener(GuiSubstitutions gui) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			GuiBase.openGui(this.gui.getParent());
		}
	}
}
