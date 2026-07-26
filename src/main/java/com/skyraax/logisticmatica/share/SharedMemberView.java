package com.skyraax.logisticmatica.share;

import java.util.UUID;

/** Network-safe member, invitation or access-request view. */
public record SharedMemberView(UUID playerId, String playerName, int permissions,
		boolean accepted, boolean accessRequested) {
	public SharedMemberView {
		permissions = SharePermission.sanitize(permissions);
	}

	public SharedMemberView(UUID playerId, String playerName, int permissions, boolean accepted) {
		this(playerId, playerName, permissions, accepted, false);
	}
}
