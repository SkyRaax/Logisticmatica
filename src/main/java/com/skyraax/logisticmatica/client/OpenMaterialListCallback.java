package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.GuiUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;

import com.skyraax.logisticmatica.client.gui.GuiFocusPicker;
import com.skyraax.logisticmatica.client.gui.GuiMaterialListView;

/**
 * Hotkey callback that opens Logisticmatica's material-list screen for the focused material list.
 * If nothing is focused yet, it opens the focus picker instead of doing nothing.
 */
public class OpenMaterialListCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		MaterialListBase materialList = DataManager.getMaterialList();

		if (materialList == null) {
			GuiFocusPicker picker = new GuiFocusPicker();
			picker.setParent(GuiUtils.getCurrentScreen());
			GuiBase.openGui(picker);
			return true;
		}

		GuiMaterialListView gui = new GuiMaterialListView(materialList);
		gui.setParent(GuiUtils.getCurrentScreen());
		GuiBase.openGui(gui);
		return true;
	}
}
