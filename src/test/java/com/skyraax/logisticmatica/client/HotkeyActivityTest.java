package com.skyraax.logisticmatica.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HotkeyActivityTest {
	@Test
	void startsWithoutSuppressingTheMenuHotkey() {
		assertFalse(HotkeyActivity.recentlyActive());
		HotkeyActivity.mark();
		assertTrue(HotkeyActivity.recentlyActive());
	}
}
