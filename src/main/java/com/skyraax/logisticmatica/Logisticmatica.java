package com.skyraax.logisticmatica;

import com.skyraax.logisticmatica.server.ShareServer;
import com.skyraax.logisticmatica.share.ShareNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for Logisticmatica.
 *
 * <p>This runs on both the physical client and a dedicated server. Code here must
 * stay side-agnostic and must <strong>not</strong> reference Litematica, MaLiLib or any
 * client-only class, so the mod loads cleanly on a Litematica-less dedicated server.
 * Client wiring lives in {@link com.skyraax.logisticmatica.client.LogisticmaticaClient};
 * server-authoritative systems (sharing, permissions, sync) are registered here.
 */
public class Logisticmatica implements ModInitializer {
	public static final String MOD_ID = "logisticmatica";
	public static final String MOD_NAME = "Logisticmatica";
	public static final String MOD_VERSION = FabricLoader.getInstance()
			.getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	@Override
	public void onInitialize() {
		ShareNetworking.registerPayloads();
		ShareServer.register();
		LOGGER.info("[{}] Common init complete; sharing protocol v{} registered.", MOD_NAME, com.skyraax.logisticmatica.share.ShareProtocol.VERSION);
	}
}
