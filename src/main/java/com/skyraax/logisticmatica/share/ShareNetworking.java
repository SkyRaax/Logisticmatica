package com.skyraax.logisticmatica.share;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the shared payload types on both physical sides before play begins. */
public final class ShareNetworking {
	private ShareNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(
				ServerboundSharePayload.TYPE, ServerboundSharePayload.CODEC, ShareProtocol.MAX_ENVELOPE_BYTES);
		PayloadTypeRegistry.clientboundPlay().registerLarge(
				ClientboundSharePayload.TYPE, ClientboundSharePayload.CODEC, ShareProtocol.MAX_ENVELOPE_BYTES);
	}
}
