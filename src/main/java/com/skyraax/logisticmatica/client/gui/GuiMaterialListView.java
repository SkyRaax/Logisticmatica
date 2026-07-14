package com.skyraax.logisticmatica.client.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.ButtonOnOff;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.FocusState;

/**
 * A lean material-list screen for Logisticmatica, built on MaLiLib's list-widget framework.
 * <p>
 * Mirrors the essential setup of Litematica's {@code GuiMaterialList} (same list geometry and
 * completion-listener wiring) but strips it down to: a search box, a "Hide complete" toggle,
 * a "Refresh" button and a back/close button. The item rows are drawn by {@link WidgetMaterialEntry}.
 */
public class GuiMaterialListView extends GuiListBase<MaterialListEntry, WidgetMaterialEntry, WidgetListMaterialView>
                                 implements ICompletionListener
{
	private final MaterialListBase materialList;

	public GuiMaterialListView(MaterialListBase materialList)
	{
		super(10, 66); // leave room for the navigation tab row + control row

		this.materialList = materialList;
		this.materialList.setCompletionListener(this);
		this.title = this.materialList.getTitle();
		this.useTitleHierarchy = false;

		// Refresh the "available" counts against the current player inventory.
		// (this.mc.player may be null on a title screen etc.; updateAvailableCounts() handles that.)
		MaterialListUtils.updateAvailableCounts(this.materialList.getMaterialsAll(), this.mc.player);
	}

	public MaterialListBase getMaterialList()
	{
		return this.materialList;
	}

	@Override
	protected int getBrowserWidth()
	{
		return this.getScreenWidth() - 20;
	}

	@Override
	protected int getBrowserHeight()
	{
		return this.getScreenHeight() - 102;
	}

	@Override
	protected WidgetListMaterialView createListWidget(int listX, int listY)
	{
		return new WidgetListMaterialView(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
	}

	@Override
	public void initGui()
	{
		// GuiListBase.initGui() clears all elements and (re)creates + refreshes the list widget.
		// The screen title (this.title == materialList.getTitle()) is drawn automatically by GuiBase.
		super.initGui();

		NavBar.add(this, NavBar.Tab.MATERIALS);

		final int y = 44;
		final int gap = 2;
		int x = 12;

		// Search text field: filters the list by item display name. Its text is restored from the
		// list widget so the active filter survives a re-init (e.g. from onTaskCompleted()).
		WidgetListMaterialView listWidget = this.getListWidget();
		GuiTextFieldGeneric searchField = new GuiTextFieldGeneric(x, y, 160, 16, this.font);

		if (listWidget != null)
		{
			searchField.setValueWrapper(listWidget.getFilterTextRaw());
		}

		this.addTextField(searchField, new SearchFieldListener(this), TextFieldType.STRING);
		x += 160 + 6;

		// "Hide complete" on/off toggle
		x += this.createButtonOnOff(x, y, this.materialList.getHideAvailable(), ButtonListener.Type.HIDE_COMPLETE) + gap;

		// "Refresh" button (re-creates the material list from the schematic/placement/area)
		x += this.createButton(x, y, ButtonListener.Type.REFRESH) + gap;

		// "Substitutions" button: edit the focused schematic's material swaps.
		x += this.createButton(x, y, ButtonListener.Type.SUBSTITUTIONS) + gap;

		// Back / close button, bottom-right
		String backLabel = ButtonListener.Type.BACK.getDisplayName();
		int backWidth = this.getStringWidth(backLabel) + 20;
		int backX = this.getScreenWidth() - backWidth - 10;
		int backY = this.getScreenHeight() - 36;
		ButtonGeneric backButton = new ButtonGeneric(backX, backY, backWidth, 20, backLabel);
		this.addButton(backButton, new ButtonListener(ButtonListener.Type.BACK, this));
	}

	private int createButton(int x, int y, ButtonListener.Type type)
	{
		// width == -1 -> ButtonBase auto-sizes to the label width
		ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, type.getDisplayName());
		this.addButton(button, new ButtonListener(type, this));
		return button.getWidth();
	}

	private int createButtonOnOff(int x, int y, boolean isCurrentlyOn, ButtonListener.Type type)
	{
		// width == -1 -> ButtonOnOff sizes the ON and OFF states to a common width
		ButtonOnOff button = new ButtonOnOff(x, y, -1, false, type.getTranslationKey(), isCurrentlyOn);
		this.addButton(button, new ButtonListener(type, this));
		return button.getWidth();
	}

	private void closeScreen()
	{
		// Returns to the parent screen, or to the game (null) when there is none.
		GuiBase.openGui(this.getParent());
	}

	/** Opens the substitution editor for the focused schematic, or warns if nothing is focused. */
	private void openSubstitutions()
	{
		LitematicaSchematic schematic = FocusState.getSchematic();

		if (schematic == null)
		{
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "logisticmatica.message.substitutions.no_focus");
			return;
		}

		GuiSubstitutions gui = new GuiSubstitutions(schematic);
		gui.setParent(this);
		GuiBase.openGui(gui);
	}

	@Override
	public void onTaskCompleted()
	{
		// Re-create the widgets when a material-list task finishes, but only if we're still the open screen.
		if (GuiUtils.getCurrentScreen() == this)
		{
			this.initGui();
		}
	}

	private record SearchFieldListener(GuiMaterialListView gui) implements ITextFieldListener<GuiTextFieldGeneric>
	{
		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField)
		{
			WidgetListMaterialView listWidget = this.gui.getListWidget();

			if (listWidget != null)
			{
				listWidget.setFilterText(textField.getValueWrapper());
				listWidget.refreshEntries();
				listWidget.resetScrollbarPosition();
			}

			return true;
		}
	}

	private record ButtonListener(Type type, GuiMaterialListView parent) implements IButtonActionListener
	{
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton)
		{
			MaterialListBase materialList = this.parent.materialList;

			switch (this.type)
			{
				case REFRESH:
					materialList.reCreateMaterialList();
					break;

				case SUBSTITUTIONS:
					this.parent.openSubstitutions();
					return; // openSubstitutions swaps the screen; don't re-init this one.

				case HIDE_COMPLETE:
					materialList.setHideAvailable(!materialList.getHideAvailable());
					// Restore the full pre-filtered set (minus ignored) so toggling the option off
					// brings back entries that recreateFilteredList() may have pruned while it was on.
					materialList.refreshPreFilteredList();

					WidgetListMaterialView listWidget = this.parent.getListWidget();
					if (listWidget != null)
					{
						listWidget.refreshEntries();
					}
					break;

				case BACK:
					this.parent.closeScreen();
					return; // The screen is closing; don't re-init it below.
			}

			this.parent.initGui(); // Re-create buttons/text fields to reflect the new state
		}

		private enum Type
		{
			REFRESH       ("logisticmatica.gui.button.material_list.refresh"),
			SUBSTITUTIONS ("logisticmatica.gui.button.substitutions"),
			HIDE_COMPLETE ("logisticmatica.gui.button.material_list.hide_complete"),
			BACK          ("logisticmatica.gui.button.back");

			private final String translationKey;

			Type(String translationKey)
			{
				this.translationKey = translationKey;
			}

			public String getTranslationKey()
			{
				return this.translationKey;
			}

			public String getDisplayName()
			{
				return StringUtils.translate(this.translationKey);
			}
		}
	}
}
