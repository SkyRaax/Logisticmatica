package com.skyraax.logisticmatica;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for Logisticmatica.
 *
 * <p>This runs on both the physical client and a dedicated server. Code here must
 * stay side-agnostic and must <strong>not</strong> reference Litematica, MaLiLib or any
 * client-only class, so the mod loads cleanly on a Litematica-less dedicated server.
 * Client wiring lives in {@link com.skyraax.logisticmatica.client.LogisticmaticaClient};
 * server-authoritative systems (sharing, permissions, sync) will be registered here.
 */
public class Logisticmatica implements ModInitializer {
	public static final String MOD_ID = "logisticmatica";
	public static final String MOD_NAME = "Logisticmatica";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	@Override
	public void onInitialize() {
		LOGGER.info("[{}] Common init complete. Server-side systems will be registered here.", MOD_NAME);
	}
}
