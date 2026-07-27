package com.skyraax.logisticmatica.share;

import java.util.List;
import java.util.UUID;

/** Ordered live changes for one subscribed project's authoritative containers. */
public record SharedContainerDelta(UUID projectId, long revision, List<SharedContainerView> upserts,
		List<SharedContainerKey> removals) {
	public SharedContainerDelta {
		upserts = List.copyOf(upserts);
		removals = List.copyOf(removals);
	}
}
