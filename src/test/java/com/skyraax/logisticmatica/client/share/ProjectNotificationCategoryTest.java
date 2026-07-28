package com.skyraax.logisticmatica.client.share;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectNotificationCategoryTest {
	@Test
	void liveContainerContentsAreQuietByDefault() {
		assertFalse(ProjectNotificationCategory.CONTAINER_CONTENTS.defaultEnabled());
		assertTrue(ProjectNotificationCategory.CONTAINER_MARKS.defaultEnabled());
		assertTrue(ProjectNotificationCategory.STATUS.defaultEnabled());
	}
}
