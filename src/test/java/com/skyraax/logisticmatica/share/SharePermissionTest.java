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
		assertTrue(SharePermission.UPDATE_SCHEMATIC.isIn(SharePermission.EDITOR));
		assertTrue(SharePermission.MANAGE_PERMISSIONS.isIn(SharePermission.MANAGER));
		assertFalse(SharePermission.DELETE.isIn(SharePermission.MANAGER));
	}

	@Test
	void publicAccessNeverGrantsProjectAdministration() {
		assertTrue(SharePermission.MANAGE_CONTAINERS.isIn(ShareAccess.PUBLIC_SUPPLIER.permissions()));
		assertFalse(SharePermission.UPDATE_SCHEMATIC.isIn(ShareAccess.PUBLIC_SUPPLIER.permissions()));
		assertTrue(SharePermission.UPDATE_SCHEMATIC.isIn(ShareAccess.PUBLIC_EDITOR.permissions()));
		assertFalse(SharePermission.MANAGE_PERMISSIONS.isIn(ShareAccess.PUBLIC_EDITOR.permissions()));
		assertFalse(SharePermission.DELETE.isIn(ShareAccess.PUBLIC_EDITOR.permissions()));
	}
}
