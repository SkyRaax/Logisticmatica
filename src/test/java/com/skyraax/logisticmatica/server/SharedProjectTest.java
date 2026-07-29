package com.skyraax.logisticmatica.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.skyraax.logisticmatica.share.ShareAccess;
import com.skyraax.logisticmatica.share.ProjectStatus;
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
		assertEquals(0, view.containerCount());
		assertEquals(0L, view.containerRevision());
		assertEquals(1, view.members().size());
	}

	@Test
	void inventoryRefreshDoesNotInvalidatePlacementRevision() {
		SharedProject project = project(UUID.randomUUID());
		SharedProject.ContainerKey key = new SharedProject.ContainerKey("minecraft:overworld", 1, 2, 3);
		project.putContainer(key, Map.of("minecraft:stone", 1));
		long revision = project.revision();
		long containerRevision = project.containerRevision();

		assertTrue(project.refreshContainer(key, Map.of("minecraft:stone", 2)));
		assertFalse(project.refreshContainer(key, Map.of("minecraft:stone", 2)));
		assertEquals(revision, project.revision());
		assertEquals(containerRevision, project.containerRevision());
		project.commitContainerRefresh();
		assertEquals(containerRevision + 1, project.containerRevision());
	}

	@Test
	void unloadedContainerMarkReceivesAuthoritativeSnapshotWhenAvailable() {
		UUID owner = UUID.randomUUID();
		SharedProject project = project(owner);
		SharedProject.ContainerKey key = new SharedProject.ContainerKey("minecraft:overworld", 80, 64, -120);
		project.putContainer(key, Map.of());

		assertEquals(1, project.viewFor(owner, false).containerCount());
		assertTrue(project.containerSnapshot().getFirst().items().isEmpty());
		assertTrue(project.refreshContainer(key, Map.of("minecraft:redstone", 64)));
		assertEquals(Map.of("minecraft:redstone", 64),
				project.containerSnapshot().getFirst().items());
	}

	@Test
	void destroyedContainerRemovalInvalidatesBothProjectViewsAndContainerSnapshots() {
		UUID owner = UUID.randomUUID();
		SharedProject project = project(owner);
		SharedProject.ContainerKey key = new SharedProject.ContainerKey("minecraft:overworld", 8, 70, 12);
		project.putContainer(key, Map.of("minecraft:stone", 64));
		long revision = project.revision();
		long containerRevision = project.containerRevision();

		assertTrue(project.removeContainer(key));

		assertEquals(revision + 1, project.revision());
		assertEquals(containerRevision + 1, project.containerRevision());
		assertEquals(0, project.viewFor(owner, false).containerCount());
		assertTrue(project.containerSnapshot().isEmpty());
		assertFalse(project.removeContainer(key));
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
		assertEquals(0, outsider.containerCount());
		assertEquals(0L, outsider.containerRevision());
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
		project.setStatus(ProjectStatus.BLOCKED);
		project.setPublicAccess(ShareAccess.PUBLIC_SUPPLIER);
		project.requestAccess(requester, "Supplier", SharePermission.EDITOR);

		SharedProject restored = SharedProject.fromJson(project.toJson());
		assertTrue(restored != null);
		assertEquals(ProjectStatus.BLOCKED, restored.status());
		assertEquals(ShareAccess.PUBLIC_SUPPLIER, restored.publicAccess());
		assertTrue(restored.accessRequestedBy(requester));
		assertEquals(SharePermission.EDITOR, restored.members().get(requester).permissions());
	}

	private static SharedProject project(UUID owner) {
		return new SharedProject(UUID.randomUUID(), owner, "Owner", "Project", "minecraft:overworld",
				0, 64, 0, 0, 0, "a".repeat(64), 100);
	}
}
