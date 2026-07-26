package com.skyraax.logisticmatica.share;

import java.util.Map;

/** Network-safe server snapshot of one schematic-bound container. */
public record SharedContainerView(String dimension, int x, int y, int z, Map<String, Integer> items) {
	public SharedContainerView {
		items = Map.copyOf(items);
	}
}
