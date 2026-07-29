package com.skyraax.logisticmatica.share;

/** Authoritative availability state of one tracked project container. */
public enum ContainerSyncStatus {
	PENDING,
	SYNCED,
	UNLOADED,
	MISSING,
	TOO_COMPLEX;

	public static ContainerSyncStatus byId(int id) {
		ContainerSyncStatus[] values = values();
		if (id < 0 || id >= values.length) {
			throw new IllegalArgumentException("Unknown container sync status " + id);
		}
		return values[id];
	}
}
