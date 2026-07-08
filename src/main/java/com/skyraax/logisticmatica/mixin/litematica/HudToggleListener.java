package com.skyraax.logisticmatica.mixin.litematica;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;

import fi.dy.masa.litematica.gui.GuiMaterialList;

import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Action for the injected HUD-toggle button on Litematica's material-list screen: flips the HUD
 * on/off, persists the config, then re-inits the screen so the button's ON/OFF label refreshes
 * (mirroring how Litematica's own buttons re-init after an action).
 */
public class HudToggleListener implements IButtonActionListener {
	private final GuiMaterialList gui;

	public HudToggleListener(GuiMaterialList gui) {
		this.gui = gui;
	}

	@Override
	public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
		Configs.Hud.ENABLED.setBooleanValue(!Configs.Hud.ENABLED.getBooleanValue());
		Configs.saveToFile();
		this.gui.initGui();
	}
}
