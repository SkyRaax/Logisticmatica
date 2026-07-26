package com.skyraax.logisticmatica.share;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SharePermissionTest {
	@Test
	void rolePresetsOnlyContainTheirIntendedCapabilities() {
		assertTrue(SharePermission.VIEW.isIn(SharePermission.VIEWER));
		assertTrue(SharePermission.MANAGE_CONTAINERS.isIn(SharePermission.BUILDER));
		assertFalse(SharePermission.MOVE.isIn(SharePermission.BUILDER));
		assertTrue(SharePermission.MOVE.isIn(SharePermission.EDITOR));
		assertTrue(SharePermission.MANAGE_PERMISSIONS.isIn(SharePermission.MANAGER));
		assertFalse(SharePermission.DELETE.isIn(SharePermission.MANAGER));
	}
}
