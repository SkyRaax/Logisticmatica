package com.skyraax.logisticmatica.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ServerContainerAccessTest {
	@Test
	void onlyLoadedNonContainerBlocksRemoveAStaleMark() {
		ServerContainerAccess.ReadResult unloaded = new ServerContainerAccess.ReadResult(
				ServerContainerAccess.ReadStatus.UNLOADED, Map.of());
		ServerContainerAccess.ReadResult removed = new ServerContainerAccess.ReadResult(
				ServerContainerAccess.ReadStatus.NOT_CONTAINER, Map.of());
		ServerContainerAccess.ReadResult complex = new ServerContainerAccess.ReadResult(
				ServerContainerAccess.ReadStatus.TOO_COMPLEX, Map.of());

		assertFalse(unloaded.shouldRemoveMark());
		assertTrue(removed.shouldRemoveMark());
		assertFalse(complex.shouldRemoveMark());
	}
}
