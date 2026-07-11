package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.GuiUtils;

import com.skyraax.logisticmatica.client.gui.GuiContainerOverview;

/**
 * Hotkey callback that opens the tracked-container overview — the "chest tracker" screen listing
 * every marked container and what it holds.
 */
public class OpenContainerOverviewCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		GuiContainerOverview gui = new GuiContainerOverview();
		gui.setParent(GuiUtils.getCurrentScreen());
		GuiBase.openGui(gui);
		return true;
	}
}
