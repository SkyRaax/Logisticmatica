package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.GuiUtils;

import com.skyraax.logisticmatica.client.gui.GuiFocusPicker;

/** Hotkey callback: opens the picker for choosing which schematic placement is focused. */
public class OpenFocusPickerCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		GuiFocusPicker gui = new GuiFocusPicker();
		gui.setParent(GuiUtils.getCurrentScreen());
		GuiBase.openGui(gui);
		return true;
	}
}
