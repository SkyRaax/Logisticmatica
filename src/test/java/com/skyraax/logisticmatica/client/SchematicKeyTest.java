package com.skyraax.logisticmatica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SchematicKeyTest {
	@Test
	void extractsOnlyWellFormedSharedProjectKeys() {
		UUID projectId = UUID.fromString("7b90ef1a-41ce-41b2-8bdf-debaa44e0b1d");

		assertEquals(projectId, SchematicKey.projectId("project:" + projectId));
		assertNull(SchematicKey.projectId("name:project:" + projectId));
		assertNull(SchematicKey.projectId("project:not-a-uuid"));
		assertNull(SchematicKey.projectId(null));
	}
}
