package com.skyraax.logisticmatica.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.skyraax.logisticmatica.share.ShareAccess;
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

	@Test
	void directoryListingHidesProtectedProjectContents() {
		SharedProject project = project(UUID.randomUUID());
		project.replaceSubstitutions(Map.of("minecraft:stone", "minecraft:dirt"));
		project.putContainer(new SharedProject.ContainerKey("minecraft:overworld", 1, 2, 3),
				Map.of("minecraft:stone", 64));

		SharedProjectView outsider = project.viewFor(UUID.randomUUID(), false);
		assertFalse(outsider.member());
		assertFalse(outsider.can(SharePermission.VIEW));
		assertEquals("", outsider.schematicHash());
		assertEquals(0, outsider.schematicSize());
		assertTrue(outsider.substitutions().isEmpty());
		assertTrue(outsider.containers().isEmpty());
	}

	@Test
	void publicViewerReceivesProjectDataWithoutMembership() {
		SharedProject project = project(UUID.randomUUID());
		project.setPublicAccess(ShareAccess.PUBLIC_VIEWER);

		SharedProjectView outsider = project.viewFor(UUID.randomUUID(), false);
		assertFalse(outsider.member());
		assertTrue(outsider.can(SharePermission.VIEW));
		assertEquals("a".repeat(64), outsider.schematicHash());
	}

	@Test
	void accessRequestIsDistinctFromInvitationAndCanBeApproved() {
		UUID requester = UUID.randomUUID();
		SharedProject project = project(UUID.randomUUID());
		project.requestAccess(requester, "Builder", SharePermission.BUILDER);

		SharedProjectView pending = project.viewFor(requester, false);
		assertTrue(pending.accessRequested());
		assertFalse(pending.pendingInvite());
		assertTrue(project.respondToAccessRequest(requester, true, SharePermission.BUILDER));

		SharedProjectView accepted = project.viewFor(requester, false);
		assertTrue(accepted.member());
		assertFalse(accepted.accessRequested());
		assertTrue(accepted.can(SharePermission.MANAGE_CONTAINERS));
	}

	@Test
	void publicPolicyAndAccessRequestSurvivePersistence() {
		UUID requester = UUID.randomUUID();
		SharedProject project = project(UUID.randomUUID());
		project.setPublicAccess(ShareAccess.PUBLIC_SUPPLIER);
		project.requestAccess(requester, "Supplier", SharePermission.EDITOR);

		SharedProject restored = SharedProject.fromJson(project.toJson());
		assertTrue(restored != null);
		assertEquals(ShareAccess.PUBLIC_SUPPLIER, restored.publicAccess());
		assertTrue(restored.accessRequestedBy(requester));
		assertEquals(SharePermission.EDITOR, restored.members().get(requester).permissions());
	}

	private static SharedProject project(UUID owner) {
		return new SharedProject(UUID.randomUUID(), owner, "Owner", "Project", "minecraft:overworld",
				0, 64, 0, 0, 0, "a".repeat(64), 100);
	}
}
