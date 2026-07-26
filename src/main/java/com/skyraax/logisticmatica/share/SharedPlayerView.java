package com.skyraax.logisticmatica.share;

import java.util.UUID;

/** One currently-online server player exposed to the invitation picker. */
public record SharedPlayerView(UUID playerId, String playerName) {
}
