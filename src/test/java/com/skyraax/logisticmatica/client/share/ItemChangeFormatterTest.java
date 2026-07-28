package com.skyraax.logisticmatica.client.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ItemChangeFormatterTest {
	@Test
	void accumulatesNetAddedAndRemovedItemsAcrossContainers() {
		Map<String, Integer> changes = new LinkedHashMap<>();

		assertTrue(ItemChangeFormatter.accumulate(changes,
				Map.of("minecraft:stone", 16, "minecraft:dirt", 8),
				Map.of("minecraft:stone", 40, "minecraft:dirt", 3)));
		assertTrue(ItemChangeFormatter.accumulate(changes,
				Map.of("minecraft:stone", 10),
				Map.of("minecraft:stone", 4, "minecraft:oak_log", 12)));

		assertEquals(Map.of(
				"minecraft:stone", 18,
				"minecraft:dirt", -5,
				"minecraft:oak_log", 12), changes);
	}
}
