package com.skyraax.logisticmatica.client;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.GuiConfigs;
import com.skyraax.logisticmatica.client.hud.MaterialHudRenderer;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

/**
 * MaLiLib initialization handler — the single place where all client-side systems are
 * registered once the game is ready and MaLiLib can accept configs, keybinds and
 * renderers without class-loading issues.
 */
public class LogisticmaticaInitHandler implements IInitializationHandler {
	@Override
	public void registerModHandlers() {
		ConfigManager.getInstance().registerConfigHandler(Logisticmatica.MOD_ID, new Configs());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(
				new ModInfo(Logisticmatica.MOD_ID, Logisticmatica.MOD_NAME, GuiConfigs::new));

		RenderEventHandler.getInstance().registerInGameGuiRenderer(new MaterialHudRenderer());

		// Hotkeys, tick handlers and per-world persistence are registered in the
		// following implementation steps.

		Logisticmatica.LOGGER.info("[{}] Config, config screen and material HUD registered.", Logisticmatica.MOD_NAME);
	}
}
