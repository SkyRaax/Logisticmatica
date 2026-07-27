package com.skyraax.logisticmatica.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Collections;
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
				"a".repeat(64), 1234, SharePermission.EDITOR, ShareAccess.PUBLIC_EDITOR,
				true, false, false,
				List.of(new SharedMemberView(ownerId, "SkyRaax", SharePermission.ALL, true)),
				Map.of("minecraft:spruce_planks", "minecraft:oak_planks"), 17L, 1);

		assertEquals(List.of(expected), ShareWire.decodeProjects(ShareWire.encodeProjects(List.of(expected))));
	}

	@Test
	void containerImportsRoundTripWithCachedContents() throws IOException {
		List<SharedContainerView> expected = List.of(
				new SharedContainerView("minecraft:overworld", 1, 64, -2,
						Map.of("minecraft:stone", 64, "minecraft:redstone", 12)));
		ShareWire.Reader reader = ShareWire.decode(ShareWire.encode(writer -> writer.writeContainers(expected)));

		assertEquals(expected, reader.readContainers());
		reader.requireFinished();
	}

	@Test
	void boundedContainerSnapshotRoundTrips() throws IOException {
		UUID projectId = UUID.randomUUID();
		SharedContainerSnapshot expected = new SharedContainerSnapshot(projectId, 8L, true, false,
				List.of(new SharedContainerView("minecraft:overworld", 1, 64, -2,
						Map.of("minecraft:stone", 64))));

		assertEquals(expected, ShareWire.decodeContainerSnapshot(
				ShareWire.encodeContainerSnapshot(expected)));
	}

	@Test
	void boundedContainerDeltaRoundTrips() throws IOException {
		UUID projectId = UUID.randomUUID();
		SharedContainerDelta expected = new SharedContainerDelta(projectId, 9L,
				List.of(new SharedContainerView("minecraft:overworld", 2, 64, -2,
						Map.of("minecraft:redstone", 128))),
				List.of(new SharedContainerKey("minecraft:overworld", 1, 64, -2)));

		assertEquals(expected, ShareWire.decodeContainerDelta(
				ShareWire.encodeContainerDelta(expected)));
	}

	@Test
	void rejectsOversizedContainerSnapshotChunk() {
		SharedContainerView container = new SharedContainerView("minecraft:overworld", 1, 64, -2, Map.of());
		SharedContainerSnapshot oversized = new SharedContainerSnapshot(UUID.randomUUID(), 1L,
				Collections.nCopies(ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET + 1, container));

		assertThrows(IllegalStateException.class, () -> ShareWire.encodeContainerSnapshot(oversized));
	}



	@Test
	void onlinePlayersRoundTripWithStableIds() throws IOException {
		List<SharedPlayerView> expected = List.of(
				new SharedPlayerView(UUID.randomUUID(), "SkyRaax"),
				new SharedPlayerView(UUID.randomUUID(), "Builder"));
		assertEquals(expected, ShareWire.decodePlayers(ShareWire.encodePlayers(expected)));
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
