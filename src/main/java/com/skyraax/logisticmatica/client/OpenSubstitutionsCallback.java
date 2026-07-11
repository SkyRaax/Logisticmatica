package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.gui.GuiFocusPicker;
import com.skyraax.logisticmatica.client.gui.GuiSubstitutions;

/**
 * Hotkey callback that opens the substitution editor for the focused schematic. If nothing is
 * focused yet there is no schematic to edit, so it opens the focus picker (with a hint) instead.
 */
public class OpenSubstitutionsCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		LitematicaSchematic schematic = FocusState.getSchematic();

		if (schematic == null) {
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING,
					"logisticmatica.message.substitutions.no_focus");

			GuiFocusPicker picker = new GuiFocusPicker();
			picker.setParent(GuiUtils.getCurrentScreen());
			GuiBase.openGui(picker);
			return true;
		}

		GuiSubstitutions gui = new GuiSubstitutions(schematic);
		gui.setParent(GuiUtils.getCurrentScreen());
		GuiBase.openGui(gui);
		return true;
	}
}
