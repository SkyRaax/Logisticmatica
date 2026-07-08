package com.skyraax.logisticmatica.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.skyraax.logisticmatica.client.gui.GuiConfigs;

/**
 * Mod Menu integration: makes Logisticmatica show up in Mod Menu with a config button that
 * opens our config screen. MaLiLib only registers itself with Mod Menu, so each add-on has to
 * provide its own entrypoint. This class is only loaded when Mod Menu is actually installed
 * (the {@code modmenu} entrypoint is requested lazily), so the compile-only Mod Menu dependency
 * never causes a class-load failure at runtime when Mod Menu is absent.
 */
public class LogisticmaticaModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return screen -> {
			GuiConfigs gui = new GuiConfigs();
			gui.setParent(screen);
			return gui;
		};
	}
}
