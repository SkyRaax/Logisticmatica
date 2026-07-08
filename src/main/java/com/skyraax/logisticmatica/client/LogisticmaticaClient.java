package com.skyraax.logisticmatica.client;

import com.skyraax.logisticmatica.Logisticmatica;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import fi.dy.masa.malilib.event.InitializationHandler;

/**
 * Client entrypoint. Only invoked on the physical client.
 *
 * <p>Client features (material list, HUD, container tracking, rendering) build on
 * Litematica + MaLiLib, so we verify Litematica is present before touching any MaLiLib
 * class. If it is missing we degrade gracefully and log a clear message instead of
 * crashing — this keeps the shared jar safe to drop onto any client.
 */
@Environment(EnvType.CLIENT)
public class LogisticmaticaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (!FabricLoader.getInstance().isModLoaded("litematica")) {
			Logisticmatica.LOGGER.warn(
					"[{}] Litematica not found — client features are disabled. "
							+ "Install Litematica + MaLiLib to use the material list, HUD and container tracking.",
					Logisticmatica.MOD_NAME);
			return;
		}

		// Register with MaLiLib's initialization pipeline. registerModHandlers() then wires
		// our config, hotkeys, HUD renderer and material-list access on top of Litematica.
		InitializationHandler.getInstance().registerInitializationHandler(new LogisticmaticaInitHandler());
		Logisticmatica.LOGGER.info("[{}] Litematica detected — registered MaLiLib init handler.", Logisticmatica.MOD_NAME);
	}
}
