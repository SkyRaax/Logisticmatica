package com.skyraax.logisticmatica.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.skyraax.logisticmatica.client.config.MaterialAmountMode;

class MaterialViewTest {
	@Test
	void alreadyBuiltFilterExclusivelyControlsZeroRemainingTargets() {
		assertFalse(MaterialView.shouldHide(64, 0, 0, MaterialAmountMode.REMAINING,
				false, true));
		assertTrue(MaterialView.shouldHide(64, 0, 0, MaterialAmountMode.REMAINING,
				true, true));
	}

	@Test
	void fullySuppliedFilterUsesTheSelectedRequirementTarget() {
		assertTrue(MaterialView.shouldHide(64, 20, 20, MaterialAmountMode.REMAINING,
				false, true));
		assertFalse(MaterialView.shouldHide(64, 20, 19, MaterialAmountMode.REMAINING,
				false, true));
		assertFalse(MaterialView.shouldHide(64, 20, 20, MaterialAmountMode.TOTAL,
				false, true));
		assertTrue(MaterialView.shouldHide(64, 20, 64, MaterialAmountMode.TOTAL,
				false, true));
	}

	@Test
	void disabledFiltersDoNotHideAnyMaterial() {
		assertFalse(MaterialView.shouldHide(64, 0, 64, MaterialAmountMode.TOTAL,
				false, false));
		assertFalse(MaterialView.shouldHide(64, 20, 64, MaterialAmountMode.TOTAL,
				false, false));
	}
}
