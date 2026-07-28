package com.skyraax.logisticmatica.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.skyraax.logisticmatica.share.ShareProtocol;
import com.skyraax.logisticmatica.share.SharedContainerKey;
import com.skyraax.logisticmatica.share.SharedContainerView;

class ShareServerBackpressureTest {
	@Test
	void actionWindowBoundsEveryPlayerPerTick() {
		ShareServer.ActionWindow window = new ShareServer.ActionWindow();

		for (int action = 0; action < 8; action++) {
			assertTrue(window.allow(100L));
		}
		assertFalse(window.allow(100L));
		assertTrue(window.allow(101L));
	}

	@Test
	void pendingDeltaKeepsOnlyLatestStateForEveryContainer() {
		UUID projectId = UUID.randomUUID();
		ShareServer.PendingDelta delta = new ShareServer.PendingDelta(projectId);
		SharedContainerKey key = new SharedContainerKey("minecraft:overworld", 1, 2, 3);
		SharedContainerView first = view(1, Map.of("minecraft:stone", 1));
		SharedContainerView latest = view(1, Map.of("minecraft:stone", 64));

		assertTrue(delta.merge(2L, List.of(first), List.of()));
		assertTrue(delta.merge(3L, List.of(latest), List.of()));
		assertEquals(3L, delta.revision());
		assertEquals(List.of(latest), delta.upserts());
		assertTrue(delta.removals().isEmpty());

		assertTrue(delta.merge(4L, List.of(), List.of(key)));
		assertTrue(delta.upserts().isEmpty());
		assertEquals(List.of(key), delta.removals());

		assertTrue(delta.merge(5L, List.of(first), List.of()));
		assertEquals(List.of(first), delta.upserts());
		assertTrue(delta.removals().isEmpty());
	}

	@Test
	void pendingDeltaFallsBackBeforePacketBoundCanBeExceeded() {
		ShareServer.PendingDelta delta = new ShareServer.PendingDelta(UUID.randomUUID());
		List<SharedContainerView> containers = new ArrayList<>();
		for (int index = 0; index <= ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET; index++) {
			containers.add(view(index, Map.of("minecraft:dirt", index + 1)));
		}

		assertFalse(delta.merge(2L, containers, List.of()));
	}

	private static SharedContainerView view(int x, Map<String, Integer> items) {
		return new SharedContainerView("minecraft:overworld", x, 2, 3, items);
	}
}
