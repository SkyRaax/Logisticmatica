package com.skyraax.logisticmatica.client.gui;

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

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.SubstitutionManager;

/**
 * Edits the material substitutions for one schematic. The list shows the schematic's original blocks;
 * click a row to pick (or change) its replacement from a searchable block list, or click a row's
 * reset button to clear just that material. "Reset all" clears every substitution. The swap is a
 * non-destructive overlay — the ghost render, verifier and material list follow it, the file does not
 * change.
 */
public class GuiSubstitutions extends GuiListBase<Block, WidgetSubstitutionEntry, WidgetListSubstitutions> {
	private final LitematicaSchematic schematic;
	private String filterText = "";

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

	/** Opens the block picker to choose a replacement for {@code original}. */
	public void openPicker(Block original) {
		GuiBlockPicker picker = new GuiBlockPicker(
				StringUtils.translate("logisticmatica.gui.title.block_picker", original.getName().getString()),
				picked -> SubstitutionManager.getInstance().setSubstitute(this.schematic, original, picked));
		picker.setParent(this);
		GuiBase.openGui(picker);
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
	protected int getListY() {
		return 66; // leave room for the navigation tab row
	}

	@Override
	protected WidgetListSubstitutions createListWidget(int listX, int listY) {
		return new WidgetListSubstitutions(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
	}

	@Override
	public void initGui() {
		super.initGui();

		NavBar.add(this, NavBar.Tab.SUBSTITUTIONS);

		final int y = 44;
		int x = 12;

		WidgetListSubstitutions listWidget = this.getListWidget();
		GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(x, y, 160, 16, this.font);
		searchField.setValueWrapper(this.filterText);

		if (listWidget != null) {
			listWidget.setFilterText(this.filterText);
		}

		this.addTextField(searchField, new SearchFieldListener(this), TextFieldType.STRING);
		x += 160 + 6;

		ButtonGeneric resetAll = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.button.substitution.reset_all"));
		this.addButton(resetAll, new ResetAllListener(this));
		x += resetAll.getWidth() + 8;

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
			this.gui.filterText = textField.getValueWrapper();

			WidgetListSubstitutions listWidget = this.gui.getListWidget();
			if (listWidget != null) {
				listWidget.setFilterText(this.gui.filterText);
				listWidget.refreshEntries();
				listWidget.resetScrollbarPosition();
			}

			return true;
		}
	}

	private record ResetAllListener(GuiSubstitutions gui) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			SubstitutionManager.getInstance().clearAll(this.gui.schematic);

			WidgetListSubstitutions listWidget = this.gui.getListWidget();
			if (listWidget != null) {
				listWidget.refreshEntries();
			}
		}
	}

	private record BackListener(GuiSubstitutions gui) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			GuiBase.openGui(this.gui.getParent());
		}
	}
}
