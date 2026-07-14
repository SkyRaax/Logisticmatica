package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.GuiUtils;

import com.skyraax.logisticmatica.client.gui.GuiHub;

/**
 * Hotkey callback that opens the Logisticmatica hub menu — the single entry point to every feature.
 */
public class OpenMenuCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		// If a chord that shares the menu key just fired (e.g. "T + C"), don't also open the menu.
		if (HotkeyActivity.recentlyActive()) {
			return true;
		}

		GuiHub hub = new GuiHub();
		hub.setParent(GuiUtils.getCurrentScreen());
		GuiBase.openGui(hub);
		return true;
	}
}
