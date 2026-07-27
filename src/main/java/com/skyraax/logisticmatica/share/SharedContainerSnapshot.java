package com.skyraax.logisticmatica.share;

import java.util.List;
import java.util.UUID;

/** One bounded chunk of authoritative container state sent on subscribe or recovery. */
public record SharedContainerSnapshot(UUID projectId, long revision, boolean reset, boolean complete,
		List<SharedContainerView> containers) {
	public SharedContainerSnapshot(UUID projectId, long revision, List<SharedContainerView> containers) {
		this(projectId, revision, true, true, containers);
	}

	public SharedContainerSnapshot {
		containers = List.copyOf(containers);
	}
}
