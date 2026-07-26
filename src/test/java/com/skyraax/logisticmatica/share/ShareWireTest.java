package com.skyraax.logisticmatica.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ShareWireTest {
	@Test
	void projectRoundTripsWithoutLosingAuthorityState() throws IOException {
		UUID projectId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		SharedProjectView expected = new SharedProjectView(projectId, 42, "Spawn perimeter",
				ownerId, "SkyRaax", "minecraft:overworld", 12, 64, -8, 1, 2,
				"a".repeat(64), 1234, SharePermission.EDITOR, false,
				List.of(new SharedMemberView(ownerId, "SkyRaax", SharePermission.ALL, true)),
				Map.of("minecraft:spruce_planks", "minecraft:oak_planks"),
				List.of(new SharedContainerView("minecraft:overworld", 10, 65, -9,
						Map.of("minecraft:redstone", 128))));

		assertEquals(List.of(expected), ShareWire.decodeProjects(ShareWire.encodeProjects(List.of(expected))));
	}

	@Test
	void rejectsCollectionCountsOutsideProtocolBounds() {
		byte[] negative = ByteBuffer.allocate(4).putInt(-1).array();
		byte[] excessive = ByteBuffer.allocate(4)
				.putInt(ShareProtocol.MAX_PROJECTS_PER_PLAYER + 1).array();
		assertThrows(IOException.class, () -> ShareWire.decodeProjects(negative));
		assertThrows(IOException.class, () -> ShareWire.decodeProjects(excessive));
	}
}
