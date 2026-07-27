package com.skyraax.logisticmatica.share;

/** Stable world position identifying one canonical shared container. */
public record SharedContainerKey(String dimension, int x, int y, int z) {
}
