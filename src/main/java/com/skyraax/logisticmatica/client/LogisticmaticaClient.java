package com.skyraax.logisticmatica.client;

import com.skyraax.logisticmatica.Logisticmatica;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client entrypoint. Only invoked on the physical client.
 *
 * <p>Client features (material list, HUD, container tracking, rendering) build on
 * Litematica, so we verify it is present before wiring anything up. If it is missing we
 * degrade gracefully and log a clear message instead of crashing — this keeps the shared
 * jar safe to drop onto any client.
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
		Logisticmatica.LOGGER.info(
				"[{}] Client init complete. Litematica detected — feature wiring will go here.",
				Logisticmatica.MOD_NAME);
	}
}
