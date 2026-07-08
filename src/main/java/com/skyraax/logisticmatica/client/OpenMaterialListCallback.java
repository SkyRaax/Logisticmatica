package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.GuiUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.gui.GuiMaterialListView;

/**
 * Hotkey callback that opens Logisticmatica's material-list screen for the currently active
 * Litematica material list. If none is active yet (the player hasn't opened a material list),
 * it logs a hint instead of crashing — a later step can auto-create one from the selected placement.
 */
public class OpenMaterialListCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		MaterialListBase materialList = DataManager.getMaterialList();

		if (materialList == null) {
			Logisticmatica.LOGGER.info(
					"[{}] Open material list: no active material list yet — open a schematic's material list once first.",
					Logisticmatica.MOD_NAME);
			return true;
		}

		GuiMaterialListView gui = new GuiMaterialListView(materialList);
		gui.setParent(GuiUtils.getCurrentScreen());
		GuiBase.openGui(gui);
		return true;
	}
}
