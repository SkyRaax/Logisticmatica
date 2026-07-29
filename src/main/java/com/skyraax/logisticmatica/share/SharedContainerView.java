package com.skyraax.logisticmatica.share;

import java.util.Map;

/** Network-safe server snapshot of one schematic-bound container. */
public record SharedContainerView(String dimension, int x, int y, int z, Map<String, Integer> items,
		ContainerSyncStatus status, long lastUpdatedEpochMillis) {
	public SharedContainerView {
		items = Map.copyOf(items);
		status = status == null ? ContainerSyncStatus.PENDING : status;
		lastUpdatedEpochMillis = Math.max(0L, lastUpdatedEpochMillis);
	}

	public SharedContainerView(String dimension, int x, int y, int z, Map<String, Integer> items) {
		this(dimension, x, y, z, items, ContainerSyncStatus.SYNCED, 0L);
	}
}
