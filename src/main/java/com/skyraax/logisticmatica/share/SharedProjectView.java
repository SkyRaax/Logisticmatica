package com.skyraax.logisticmatica.share;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable project state sent by the authoritative server to one client. */
public record SharedProjectView(
		UUID id,
		long revision,
		String name,
		UUID ownerId,
		String ownerName,
		String dimension,
		int x,
		int y,
		int z,
		int rotation,
		int mirror,
		String schematicHash,
		int schematicSize,
		int myPermissions,
		ShareAccess publicAccess,
		boolean member,
		boolean pendingInvite,
		boolean accessRequested,
		List<SharedMemberView> members,
		Map<String, String> substitutions,
		List<SharedContainerView> containers) {
	public SharedProjectView {
		myPermissions = SharePermission.sanitize(myPermissions);
		publicAccess = publicAccess != null ? publicAccess : ShareAccess.REQUEST_ONLY;
		members = List.copyOf(members);
		substitutions = Map.copyOf(substitutions);
		containers = List.copyOf(containers);
	}

	public boolean can(SharePermission permission) {
		return permission.isIn(this.myPermissions);
	}
}
