package com.skyraax.logisticmatica.share;

import java.util.UUID;

/** Network-safe member/invitation view. */
public record SharedMemberView(UUID playerId, String playerName, int permissions, boolean accepted) {
	public SharedMemberView {
		permissions = SharePermission.sanitize(permissions);
	}
}
