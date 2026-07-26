package com.skyraax.logisticmatica.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedProjectView;

class SharedProjectTest {
	@Test
	void pendingInviteCannotSeeProjectContents() {
		UUID owner = UUID.randomUUID();
		UUID invitee = UUID.randomUUID();
		SharedProject project = project(owner);
		project.replaceSubstitutions(Map.of("minecraft:stone", "minecraft:dirt"));
		project.putContainer(new SharedProject.ContainerKey("minecraft:overworld", 1, 2, 3),
				Map.of("minecraft:stone", 64));
		project.invite(invitee, "Builder", SharePermission.EDITOR);

		SharedProjectView view = project.viewFor(invitee, false);
		assertTrue(view.pendingInvite());
		assertEquals(0, view.myPermissions());
		assertTrue(view.substitutions().isEmpty());
		assertTrue(view.containers().isEmpty());
		assertEquals(1, view.members().size());
	}

	@Test
	void inventoryRefreshDoesNotInvalidatePlacementRevision() {
		SharedProject project = project(UUID.randomUUID());
		SharedProject.ContainerKey key = new SharedProject.ContainerKey("minecraft:overworld", 1, 2, 3);
		project.putContainer(key, Map.of("minecraft:stone", 1));
		long revision = project.revision();

		assertTrue(project.refreshContainer(key, Map.of("minecraft:stone", 2)));
		assertFalse(project.refreshContainer(key, Map.of("minecraft:stone", 2)));
		assertEquals(revision, project.revision());
	}

	private static SharedProject project(UUID owner) {
		return new SharedProject(UUID.randomUUID(), owner, "Owner", "Project", "minecraft:overworld",
				0, 64, 0, 0, 0, "a".repeat(64), 100);
	}
}
