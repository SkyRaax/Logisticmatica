package com.skyraax.logisticmatica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContainerTrackerTest {
	private final ContainerTracker tracker = ContainerTracker.getInstance();

	@BeforeEach
	void resetBefore() {
		this.tracker.clear();
	}

	@AfterEach
	void resetAfter() {
		this.tracker.clear();
	}

	@Test
	void promotedLocalMarkDoesNotReturnAfterUnfocus() {
		UUID projectId = UUID.randomUUID();
		BlockPos pos = new BlockPos(1, 64, 2);
		this.tracker.toggle("local.litematic", pos);

		assertTrue(this.tracker.setServerBinding(projectId, pos, Map.of(), true));
		assertEquals(ContainerTracker.projectKey(projectId), this.tracker.schematicKeyOf(pos));

		this.tracker.clearServerBindings(projectId);

		assertFalse(this.tracker.isMarked(pos));
		assertTrue(this.tracker.markedFor("local.litematic").isEmpty());
	}

	@Test
	void unrelatedLocalCollisionReturnsAfterUnfocus() {
		UUID projectId = UUID.randomUUID();
		BlockPos pos = new BlockPos(3, 65, 4);
		this.tracker.toggle("other-project.litematic", pos);

		assertFalse(this.tracker.setServerBinding(projectId, pos, Map.of(), false));
		this.tracker.clearServerBindings(projectId);

		assertEquals("other-project.litematic", this.tracker.schematicKeyOf(pos));
	}

	@Test
	void snapshotReplacementDoesNotTemporarilyResurrectLocalCollision() {
		UUID projectId = UUID.randomUUID();
		BlockPos pos = new BlockPos(5, 66, 6);
		this.tracker.toggle("other-project.litematic", pos);
		this.tracker.setServerBinding(projectId, pos, Map.of(), false);

		this.tracker.beginServerSnapshot(projectId);
		assertFalse(this.tracker.isMarked(pos));
		this.tracker.setServerBinding(projectId, pos, Map.of(), false);
		this.tracker.finishServerSnapshot(projectId);

		assertEquals(ContainerTracker.projectKey(projectId), this.tracker.schematicKeyOf(pos));
		this.tracker.clearServerBindings(projectId);
		assertEquals("other-project.litematic", this.tracker.schematicKeyOf(pos));
	}

	@Test
	void unfocusDuringChunkedSnapshotRestoresEveryLocalCollision() {
		UUID projectId = UUID.randomUUID();
		BlockPos receivedAgain = new BlockPos(8, 68, 9);
		BlockPos pendingChunk = new BlockPos(10, 68, 11);
		this.tracker.toggle("other-project.litematic", receivedAgain);
		this.tracker.toggle("other-project.litematic", pendingChunk);
		this.tracker.setServerBinding(projectId, receivedAgain, Map.of(), false);
		this.tracker.setServerBinding(projectId, pendingChunk, Map.of(), false);

		this.tracker.beginServerSnapshot(projectId);
		this.tracker.setServerBinding(projectId, receivedAgain, Map.of(), false);
		this.tracker.clearServerBindings(projectId);

		assertEquals("other-project.litematic", this.tracker.schematicKeyOf(receivedAgain));
		assertEquals("other-project.litematic", this.tracker.schematicKeyOf(pendingChunk));
	}

	@Test
	void forceRemovePurgesCurrentAndDisplacedBindings() {
		UUID projectId = UUID.randomUUID();
		BlockPos pos = new BlockPos(7, 67, 8);
		this.tracker.toggle("broken.litematic", pos);
		this.tracker.setServerBinding(projectId, pos, Map.of(), false);

		assertTrue(this.tracker.forceRemove(pos));
		this.tracker.clearServerBindings(projectId);

		assertFalse(this.tracker.isMarked(pos));
		assertTrue(this.tracker.markedFor("broken.litematic").isEmpty());
	}
}
