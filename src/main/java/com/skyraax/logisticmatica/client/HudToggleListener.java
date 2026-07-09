package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;

import fi.dy.masa.litematica.gui.GuiMaterialList;

import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Action for the HUD-toggle button injected into Litematica's material-list screen: flips the HUD
 * on/off, persists the config, then re-inits that screen so the ON/OFF label refreshes.
 *
 * <p>Lives in a regular (non-mixin) package on purpose: Mixin forbids referencing classes that
 * live inside a mixin package directly, so the listener the mixin instantiates must live here.
 * It is only ever loaded from the (Litematica-only) mixin, so it stays safe on Litematica-less clients.
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
