package com.skyraax.logisticmatica.client.gui;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.gui.GuiConfigsBase;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * The in-game config screen for Logisticmatica. For now a single flat list of all options;
 * it can grow tabs later (Litematica-style) as the option set expands.
 */
public class GuiConfigs extends GuiConfigsBase {
	public GuiConfigs() {
		super(10, 50, Logisticmatica.MOD_ID, null, "logisticmatica.gui.title.configs", Logisticmatica.MOD_VERSION);
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		List<ConfigOptionWrapper> wrappers = new ArrayList<>();
		wrappers.addAll(ConfigOptionWrapper.createFor(Configs.Hud.OPTIONS));
		wrappers.addAll(ConfigOptionWrapper.createFor(Configs.Colors.OPTIONS));
		wrappers.addAll(ConfigOptionWrapper.createFor(Configs.Hotkeys.HOTKEY_LIST));
		return wrappers;
	}
}
