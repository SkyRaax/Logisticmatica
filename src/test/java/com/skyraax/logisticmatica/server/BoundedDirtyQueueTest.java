package com.skyraax.logisticmatica.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoundedDirtyQueueTest {
	@Test
	void coalescesDuplicatesAndKeepsFifoOrder() {
		BoundedDirtyQueue<String> queue = new BoundedDirtyQueue<>(3);

		assertTrue(queue.offer("a"));
		assertTrue(queue.offer("b"));
		assertTrue(queue.offer("a"));
		assertEquals("a", queue.poll());
		assertEquals("b", queue.poll());
		assertNull(queue.poll());

		BoundedDirtyQueue.Stats stats = queue.stats();
		assertEquals(3, stats.offered());
		assertEquals(1, stats.coalesced());
		assertEquals(0, stats.dropped());
		assertEquals(2, stats.processed());
	}

	@Test
	void rejectsNewKeysAtCapacityWithoutGrowing() {
		BoundedDirtyQueue<String> queue = new BoundedDirtyQueue<>(2);

		assertTrue(queue.offer("a"));
		assertTrue(queue.offer("b"));
		assertFalse(queue.offer("c"));
		assertEquals(2, queue.size());
		assertEquals(1, queue.stats().dropped());
		assertEquals("a", queue.poll());
		assertTrue(queue.offer("c"));
		assertEquals("b", queue.poll());
		assertEquals("c", queue.poll());
	}
}
