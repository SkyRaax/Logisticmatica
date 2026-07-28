package com.skyraax.logisticmatica.server;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import me.lucko.fabric.api.permissions.v0.Permissions;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.share.ClientboundSharePayload;
import com.skyraax.logisticmatica.share.ServerboundSharePayload;
import com.skyraax.logisticmatica.share.ProjectStatus;
import com.skyraax.logisticmatica.share.ShareAccess;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.ShareProtocol;
import com.skyraax.logisticmatica.share.ShareWire;
import com.skyraax.logisticmatica.share.SharedContainerDelta;
import com.skyraax.logisticmatica.share.SharedContainerKey;
import com.skyraax.logisticmatica.share.SharedContainerSnapshot;
import com.skyraax.logisticmatica.share.SharedContainerView;
import com.skyraax.logisticmatica.share.SharedPlayerView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Server-authoritative sharing service: validates every mutation, persists it and broadcasts views. */
public final class ShareServer {
	private static final ShareServer INSTANCE = new ShareServer();
	private static final String ADMIN_PERMISSION = Logisticmatica.MOD_ID + ".admin";
	private static final int CONTAINER_SCANS_PER_PASS = 4;
	private static final int CONTAINER_SCAN_INTERVAL_TICKS = 4;
	private static final long CONTAINER_SCAN_BUDGET_NANOS = 750_000L;
	private static final int SNAPSHOT_CONTAINERS_PER_PACKET = 16;
	private static final int SYNC_PACKETS_PER_TICK = 2;
	private static final long SYNC_WORK_BUDGET_NANOS = 2_000_000L;
	private static final int PROJECT_PACKETS_PER_TICK = 4;
	private static final int MAX_PENDING_CLIENT_ACTIONS = 32;
	private static final int MAX_CLIENT_ACTIONS_PER_TICK = 8;
	private static final double CONTAINER_BIND_DISTANCE_SQ = 64.0;

	private final ShareStore store = new ShareStore();
	private record ContainerScanTarget(SharedProject project, SharedProject.ContainerKey key) {}
	private static final class PendingSnapshot {
		private final UUID projectId;
		private final UUID requestId;
		private final long revision;
		private final List<SharedProject.ContainerKey> keys;
		private int cursor;

		private PendingSnapshot(SharedProject project, UUID requestId) {
			this.projectId = project.id();
			this.requestId = requestId;
			this.revision = project.containerRevision();
			this.keys = List.copyOf(project.containers().keySet());
		}
	}
	static final class PendingDelta {
		private final UUID projectId;
		private long revision;
		private final Map<SharedContainerKey, SharedContainerView> upserts = new LinkedHashMap<>();
		private final Set<SharedContainerKey> removals = new LinkedHashSet<>();

		PendingDelta(UUID projectId) {
			this.projectId = projectId;
		}

		boolean merge(long revision, List<SharedContainerView> changed,
				List<SharedContainerKey> removed) {
			this.revision = Math.max(this.revision, revision);
			for (SharedContainerKey key : removed) {
				this.upserts.remove(key);
				this.removals.add(key);
			}
			for (SharedContainerView container : changed) {
				SharedContainerKey key = wireKey(container);
				this.removals.remove(key);
				this.upserts.put(key, container);
			}
			return this.upserts.size() + this.removals.size()
					<= ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET;
		}

		long revision() { return this.revision; }
		List<SharedContainerView> upserts() { return List.copyOf(this.upserts.values()); }
		List<SharedContainerKey> removals() { return List.copyOf(this.removals); }
	}

	static final class ActionWindow {
		private long tick = Long.MIN_VALUE;
		private int actions;

		boolean allow(long currentTick) {
			if (this.tick != currentTick) {
				this.tick = currentTick;
				this.actions = 0;
			}
			return ++this.actions <= MAX_CLIENT_ACTIONS_PER_TICK;
		}
	}

	private List<ContainerScanTarget> containerScanOrder = List.of();
	private boolean containerScanDirty = true;
	private int containerScanCursor;
	private long nextContainerScanTick;
	private final Map<UUID, UUID> subscriptions = new HashMap<>();
	private final Map<UUID, PendingSnapshot> pendingSnapshots = new HashMap<>();
	private final Map<UUID, PendingDelta> pendingDeltas = new HashMap<>();
	private final Deque<UUID> outboundPlayers = new ArrayDeque<>();
	private final Set<UUID> outboundQueued = new HashSet<>();
	private final Map<UUID, Set<UUID>> pendingProjectRecipients = new LinkedHashMap<>();
	private final Map<UUID, AtomicInteger> pendingInbound = new ConcurrentHashMap<>();
	private final Map<UUID, Long> staleResyncTicks = new HashMap<>();
	private final Map<UUID, ActionWindow> actionWindows = new HashMap<>();
	@Nullable private MinecraftServer server;

	private ShareServer() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ServerboundSharePayload.TYPE, (payload, context) -> {
			UUID playerId = context.player().getUUID();
			AtomicInteger pending = INSTANCE.pendingInbound.computeIfAbsent(playerId,
					ignored -> new AtomicInteger());
			if (pending.incrementAndGet() > MAX_PENDING_CLIENT_ACTIONS) {
				pending.decrementAndGet();
				return;
			}
			context.server().execute(() -> {
				try {
					INSTANCE.handle(payload, context.player());
				} finally {
					if (pending.decrementAndGet() == 0) {
						INSTANCE.pendingInbound.remove(playerId, pending);
					}
				}
			});
		});
		ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::onServerStarted);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				INSTANCE.disconnect(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(INSTANCE::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onServerTick);
	}

	private void onServerStarted(MinecraftServer server) {
		this.server = server;
		this.store.start(server);
		this.containerScanCursor = 0;
		this.nextContainerScanTick = 0L;
		this.subscriptions.clear();
		this.pendingSnapshots.clear();
		this.pendingDeltas.clear();
		this.outboundPlayers.clear();
		this.outboundQueued.clear();
		this.pendingProjectRecipients.clear();
		this.pendingInbound.clear();
		this.staleResyncTicks.clear();
		this.actionWindows.clear();
		this.containerScanDirty = true;
	}

	private void onServerStopping(MinecraftServer server) {
		this.store.stop();
		this.server = null;
		this.containerScanOrder = List.of();
		this.containerScanDirty = true;
		this.subscriptions.clear();
		this.pendingSnapshots.clear();
		this.pendingDeltas.clear();
		this.outboundPlayers.clear();
		this.outboundQueued.clear();
		this.pendingProjectRecipients.clear();
		this.pendingInbound.clear();
		this.staleResyncTicks.clear();
		this.actionWindows.clear();
	}

	private void handle(ServerboundSharePayload payload, ServerPlayer player) {
		if (payload.protocolVersion() != ShareProtocol.VERSION) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.protocol");
			return;
		}
		MinecraftServer server = this.server;
		if (server == null || !this.actionWindows.computeIfAbsent(player.getUUID(),
				ignored -> new ActionWindow()).allow(server.getTickCount())) {
			return;
		}

		try {
			switch (payload.action()) {
				case HELLO -> this.handleHello(player, payload);
				case LIST_PROJECTS -> this.sendProjects(player, payload.requestId());
				case CREATE_PROJECT -> this.handleCreate(player, payload);
				case DOWNLOAD_PROJECT -> this.handleDownload(player, payload);
				case UPDATE_TRANSFORM -> this.handleTransform(player, payload);
				case UPDATE_SCHEMATIC -> this.handleSchematicUpdate(player, payload);
				case UPDATE_SUBSTITUTIONS -> this.handleSubstitutions(player, payload);
				case INVITE -> this.handleInvite(player, payload);
				case RESPOND_INVITE -> this.handleInviteResponse(player, payload);
				case SET_PERMISSIONS -> this.handlePermissions(player, payload);
				case REMOVE_MEMBER -> this.handleRemoveMember(player, payload);
				case DELETE_PROJECT -> this.handleDelete(player, payload);
				case LEAVE_PROJECT -> this.handleLeave(player, payload);
				case TOGGLE_CONTAINER -> this.handleContainerToggle(player, payload);
				case REFRESH_CONTAINER -> this.handleContainerRefresh(player, payload);
				case LIST_PLAYERS -> this.sendPlayers(player, payload.requestId());
				case SET_PUBLIC_ACCESS -> this.handlePublicAccess(player, payload);
				case REQUEST_ACCESS -> this.handleAccessRequest(player, payload);
				case RESPOND_ACCESS -> this.handleAccessResponse(player, payload);
				case SUBSCRIBE_PROJECT -> this.handleSubscribe(player, payload);
				case UNSUBSCRIBE_PROJECT -> this.handleUnsubscribe(player, payload);
				case SET_PROJECT_STATUS -> this.handleProjectStatus(player, payload);
			}
		} catch (IOException | IllegalArgumentException e) {
			Logisticmatica.LOGGER.warn("[{}] Rejected sharing action {} from {}: {}",
					Logisticmatica.MOD_NAME, payload.action(), player.getName().getString(), e.getMessage());
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.invalid_request");
		}
	}

	private void handleHello(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		String clientVersion = reader.readString();
		reader.requireFinished();

		byte[] body = ShareWire.encode(writer -> {
			writer.writeInt(ShareProtocol.VERSION);
			writer.writeInt(ShareProtocol.FEATURES);
			writer.writeInt(ShareProtocol.MAX_SCHEMATIC_BYTES);
			writer.writeString(Logisticmatica.MOD_VERSION);
			writer.writeUuid(this.store.serverId());
		});
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.HELLO, payload.requestId(), body));
		Logisticmatica.LOGGER.debug("[{}] Sharing handshake with {} (client {}).",
				Logisticmatica.MOD_NAME, player.getName().getString(), clientVersion);
	}

	private void handleCreate(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		if (this.store.projects().size() >= ShareProtocol.MAX_PROJECTS_PER_PLAYER) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.server_project_limit");
			return;
		}
		long owned = this.store.projects().stream().filter(project -> project.ownerId().equals(player.getUUID())).count();
		if (owned >= ShareProtocol.MAX_PROJECTS_PER_PLAYER && !this.isAdministrator(player)) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.project_limit");
			return;
		}

		ShareWire.Reader reader = ShareWire.decode(payload.body());
		String name = cleanName(reader.readString());
		String dimension = reader.readString();
		int x = reader.readInt();
		int y = reader.readInt();
		int z = reader.readInt();
		int rotation = checkedOrdinal(reader.readInt(), Rotation.values().length, "rotation");
		int mirror = checkedOrdinal(reader.readInt(), Mirror.values().length, "mirror");
		byte[] schematic = reader.readBytes(ShareProtocol.MAX_SCHEMATIC_BYTES);
		List<SharedContainerView> localContainers = reader.readContainers();
		reader.requireFinished();
		ServerLevel level = this.requireDimension(player.level().getServer(), dimension);

		if (localContainers.size() > ShareProtocol.MAX_CONTAINERS_PER_PROJECT
				|| this.totalContainerCount() + localContainers.size() > ShareProtocol.MAX_CONTAINERS_GLOBAL) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.container_limit");
			return;
		}

		String hash = this.store.storeSchematic(schematic);
		SharedProject project = new SharedProject(UUID.randomUUID(), player.getUUID(), player.getName().getString(),
				name, dimension, x, y, z, rotation, mirror, hash, schematic.length);
		this.importContainers(project, level, player, localContainers);
		this.store.put(project);
		this.containerScanDirty = true;
		this.store.save();
		this.sendProject(player, project, payload.requestId());
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.created");
	}

	/**
	 * Promotes every eligible local mark during project creation. Loaded positions are validated and
	 * snapshotted immediately; unloaded positions remain empty until the server can read their chunk.
	 */
	private void importContainers(SharedProject project, ServerLevel level, ServerPlayer player,
			List<SharedContainerView> containers) {
		int existingContainers = this.totalContainerCount();
		for (SharedContainerView imported : containers) {
			if (!project.dimension().equals(imported.dimension())
					|| player.level() != level
					|| project.containers().size() >= ShareProtocol.MAX_CONTAINERS_PER_PROJECT
					|| existingContainers + project.containers().size()
							>= ShareProtocol.MAX_CONTAINERS_GLOBAL) {
				continue;
			}

			BlockPos requested = new BlockPos(imported.x(), imported.y(), imported.z());
			boolean loaded = level.hasChunkAt(requested);
			BlockPos pos = loaded ? ServerContainerAccess.canonical(level, requested) : requested.immutable();
			SharedProject.ContainerKey key = new SharedProject.ContainerKey(project.dimension(),
					pos.getX(), pos.getY(), pos.getZ());
			if (project.containers().containsKey(key)) continue;

			boolean claimed = this.store.projects().stream()
					.anyMatch(existing -> existing.containers().containsKey(key));
			if (claimed) continue;

			Map<String, Integer> snapshot = loaded ? ServerContainerAccess.snapshot(level, pos) : Map.of();
			if (snapshot == null || snapshot.size() > ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER) {
				continue;
			}
			project.putContainer(key, snapshot);
		}
	}


	private void handleDownload(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		reader.requireFinished();
		SharedProject project = this.requireProject(player, projectId, SharePermission.VIEW, payload.requestId());
		if (project == null) {
			return;
		}

		byte[] schematic = this.store.readSchematic(project.schematicHash());
		byte[] body = ShareWire.encode(writer -> {
			writer.writeUuid(project.id());
			writer.writeLong(project.revision());
			writer.writeString(project.schematicHash());
			writer.writeString(project.name());
			writer.writeBytes(schematic, ShareProtocol.MAX_SCHEMATIC_BYTES);
		});
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.PROJECT_DATA,
				payload.requestId(), body));
	}

	private void handleSubscribe(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		UUID projectId = readProjectId(payload.body());
		SharedProject project = this.requireProject(player, projectId, SharePermission.VIEW, payload.requestId());
		if (project == null) {
			return;
		}
		this.subscriptions.put(player.getUUID(), projectId);
		this.queueSnapshot(player, project, payload.requestId());
		this.containerScanDirty = true;
	}

	private void handleUnsubscribe(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		UUID projectId = readProjectId(payload.body());
		if (projectId.equals(this.subscriptions.get(player.getUUID()))) {
			this.subscriptions.remove(player.getUUID());
			this.cancelPlayerSync(player.getUUID());
			this.containerScanDirty = true;
		}
	}

	private void handleTransform(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		long expectedRevision = reader.readLong();
		String dimension = reader.readString();
		int x = reader.readInt();
		int y = reader.readInt();
		int z = reader.readInt();
		int rotation = checkedOrdinal(reader.readInt(), Rotation.values().length, "rotation");
		int mirror = checkedOrdinal(reader.readInt(), Mirror.values().length, "mirror");
		reader.requireFinished();
		this.requireDimension(player.level().getServer(), dimension);

		SharedProject project = this.requireProject(player, projectId, SharePermission.MOVE, payload.requestId());
		if (!this.checkRevision(player, project, expectedRevision, payload.requestId())) {
			return;
		}
		project.updateTransform(dimension, x, y, z, rotation, mirror);
		this.changed(project, player);
	}

	private void handleSchematicUpdate(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		long expectedRevision = reader.readLong();
		byte[] schematic = reader.readBytes(ShareProtocol.MAX_SCHEMATIC_BYTES);
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId, SharePermission.UPDATE_SCHEMATIC, payload.requestId());
		if (!this.checkRevision(player, project, expectedRevision, payload.requestId())) {
			return;
		}
		String hash = this.store.storeSchematic(schematic);
		project.updateSchematic(hash, schematic.length);
		this.changed(project, player);
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.schematic_updated");
	}

	private void handleSubstitutions(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		long expectedRevision = reader.readLong();
		Map<String, String> substitutions = reader.readStringMap(ShareProtocol.MAX_SUBSTITUTIONS_PER_PROJECT);
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId, SharePermission.SUBSTITUTE, payload.requestId());
		if (!this.checkRevision(player, project, expectedRevision, payload.requestId())) {
			return;
		}
		for (Map.Entry<String, String> entry : substitutions.entrySet()) {
			if (entry.getKey().length() > 256 || entry.getValue().length() > 256) {
				throw new IOException("Block id is too long");
			}
		}
		project.replaceSubstitutions(substitutions);
		this.changed(project, player);
	}

	private void handleInvite(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		UUID targetId = reader.readUuid();
		reader.readString(); // client-side display name; server identity remains authoritative
		int requestedPermissions = SharePermission.sanitize(reader.readInt()) | SharePermission.VIEW.mask();
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId, SharePermission.INVITE, payload.requestId());
		if (project == null) {
			return;
		}
		ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(targetId);
		if (target == null) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.player_offline");
			return;
		}
		if (target.getUUID().equals(project.ownerId())) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.owner_member");
			return;
		}

		if (!project.members().containsKey(target.getUUID())
				&& project.members().size() >= ShareProtocol.MAX_MEMBERS_PER_PROJECT) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.member_limit");
			return;
		}
		int grantable = this.isAdministrator(player) || project.ownerId().equals(player.getUUID())
				? SharePermission.ALL : project.permissionsFor(player.getUUID());
		project.invite(target.getUUID(), target.getName().getString(), requestedPermissions & grantable);
		this.changed(project, player);
		this.sendNotice(target, UUID.randomUUID(), "logisticmatica.share.notice.invited");
	}

	private void handleInviteResponse(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		boolean accepted = reader.readBoolean();
		reader.requireFinished();
		SharedProject project = this.store.get(projectId);
		SharedProject.Member invitation = project != null ? project.members().get(player.getUUID()) : null;
		if (project == null || invitation == null || invitation.accepted() || invitation.accessRequested()) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.no_invite");
			return;
		}
		project.respondToInvite(player.getUUID(), accepted);
		this.changed(project, player);
	}

	private void handlePublicAccess(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		long expectedRevision = reader.readLong();
		ShareAccess access = ShareAccess.byId(reader.readInt());
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId,
				SharePermission.MANAGE_PERMISSIONS, payload.requestId());
		if (!this.checkRevision(player, project, expectedRevision, payload.requestId())) return;
		project.setPublicAccess(access);
		this.changed(project, player);
		this.containerScanDirty = true;
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.public_access_updated");
	}

	private void handleProjectStatus(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		long expectedRevision = reader.readLong();
		ProjectStatus status = ProjectStatus.byId(reader.readInt());
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId,
				SharePermission.UPDATE_STATUS, payload.requestId());
		if (!this.checkRevision(player, project, expectedRevision, payload.requestId())) return;
		project.setStatus(status);
		this.changed(project, player);
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.status_updated");
	}

	private void handleAccessRequest(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		int requestedPermissions = (SharePermission.sanitize(reader.readInt()) & SharePermission.MANAGER)
				| SharePermission.VIEW.mask();
		reader.requireFinished();
		SharedProject project = this.store.get(projectId);
		if (project == null || project.ownerId().equals(player.getUUID())
				|| project.acceptedBy(player.getUUID())) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.already_has_access");
			return;
		}
		SharedProject.Member existing = project.members().get(player.getUUID());
		if (existing != null && !existing.accessRequested()) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.pending_invite");
			return;
		}
		if (existing == null && project.members().size() >= ShareProtocol.MAX_MEMBERS_PER_PROJECT) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.member_limit");
			return;
		}
		project.requestAccess(player.getUUID(), player.getName().getString(), requestedPermissions);
		this.changed(project, player);
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.access_requested");
		MinecraftServer server = this.server;
		ServerPlayer owner = server != null ? server.getPlayerList().getPlayer(project.ownerId()) : null;
		if (owner != null) this.sendNotice(owner, UUID.randomUUID(),
				"logisticmatica.share.notice.access_request_received",
				player.getName().getString(), project.name());
	}

	private void handleAccessResponse(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		long expectedRevision = reader.readLong();
		UUID targetId = reader.readUuid();
		boolean accepted = reader.readBoolean();
		int requestedPermissions = SharePermission.sanitize(reader.readInt()) | SharePermission.VIEW.mask();
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId,
				SharePermission.MANAGE_PERMISSIONS, payload.requestId());
		if (!this.checkRevision(player, project, expectedRevision, payload.requestId())) return;
		int grantable = this.isAdministrator(player) || project.ownerId().equals(player.getUUID())
				? SharePermission.ALL : project.permissionsFor(player.getUUID());
		if (!project.respondToAccessRequest(targetId, accepted, requestedPermissions & grantable)) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.no_access_request");
			return;
		}
		this.changed(project, player);
		MinecraftServer server = this.server;
		ServerPlayer target = server != null ? server.getPlayerList().getPlayer(targetId) : null;
		if (target != null) this.sendNotice(target, UUID.randomUUID(), accepted
				? "logisticmatica.share.notice.access_approved"
				: "logisticmatica.share.notice.access_declined", project.name());
	}

	private void handlePermissions(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		UUID targetId = reader.readUuid();
		int requestedPermissions = SharePermission.sanitize(reader.readInt()) | SharePermission.VIEW.mask();
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId, SharePermission.MANAGE_PERMISSIONS, payload.requestId());
		if (project == null || targetId.equals(project.ownerId()) || !project.members().containsKey(targetId)) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.permissions");
			return;
		}
		int grantable = this.isAdministrator(player) || project.ownerId().equals(player.getUUID())
				? SharePermission.ALL : project.permissionsFor(player.getUUID());
		project.setPermissions(targetId, requestedPermissions & grantable);
		this.changed(project, player);
		this.containerScanDirty = true;
	}

	private void handleRemoveMember(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		UUID targetId = reader.readUuid();
		reader.requireFinished();
		SharedProject project = this.requireProject(player, projectId,
				SharePermission.MANAGE_PERMISSIONS, payload.requestId());
		if (project == null || targetId.equals(project.ownerId()) || !project.removeMember(targetId)) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.permissions");
			return;
		}
		this.changed(project, player);
		this.containerScanDirty = true;
	}


	private void handleDelete(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		UUID projectId = readProjectId(payload.body());
		SharedProject project = this.requireProject(player, projectId, SharePermission.DELETE, payload.requestId());
		if (project == null) {
			return;
		}
		Set<UUID> recipients = new LinkedHashSet<>();
		recipients.add(player.getUUID());
		this.subscriptions.forEach((playerId, subscribedProject) -> {
			if (projectId.equals(subscribedProject)) recipients.add(playerId);
		});
		this.store.remove(projectId);
		this.containerScanDirty = true;
		this.subscriptions.entrySet().removeIf(entry -> projectId.equals(entry.getValue()));
		this.pendingSnapshots.entrySet().removeIf(entry -> projectId.equals(entry.getValue().projectId));
		this.pendingDeltas.entrySet().removeIf(entry -> projectId.equals(entry.getValue().projectId));
		this.pendingProjectRecipients.remove(projectId);
		this.store.save();
		MinecraftServer server = this.server;
		if (server != null) {
			for (UUID recipient : recipients) {
				ServerPlayer online = server.getPlayerList().getPlayer(recipient);
				if (online != null) this.sendRemoved(online, projectId);
			}
		}
	}

	private void handleLeave(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		UUID projectId = readProjectId(payload.body());
		SharedProject project = this.store.get(projectId);
		if (project == null || project.ownerId().equals(player.getUUID()) || !project.removeMember(player.getUUID())) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.cannot_leave");
			return;
		}
		this.changed(project, player);
		this.containerScanDirty = true;
	}

	private void handleContainerToggle(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		String dimension = reader.readString();
		BlockPos requestedPos = new BlockPos(reader.readInt(), reader.readInt(), reader.readInt());
		reader.requireFinished();

		SharedProject project = this.requireProject(player, projectId, SharePermission.MANAGE_CONTAINERS, payload.requestId());
		if (project == null) {
			return;
		}
		ServerLevel level = this.requireDimension(player.level().getServer(), dimension);
		if (player.level() != level || player.distanceToSqr(requestedPos.getX() + 0.5,
				requestedPos.getY() + 0.5, requestedPos.getZ() + 0.5) > CONTAINER_BIND_DISTANCE_SQ) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.container_range");
			return;
		}

		BlockPos pos = ServerContainerAccess.canonical(level, requestedPos);
		SharedProject.ContainerKey key = new SharedProject.ContainerKey(dimension, pos.getX(), pos.getY(), pos.getZ());
		boolean nowMarked;
		if (project.containers().containsKey(key)) {
			project.removeContainer(key);
			nowMarked = false;
		} else {
			if (project.containers().size() >= ShareProtocol.MAX_CONTAINERS_PER_PROJECT
					|| this.totalContainerCount() >= ShareProtocol.MAX_CONTAINERS_GLOBAL) {
				this.sendError(player, payload.requestId(), "logisticmatica.share.error.container_limit");
				return;
			}
			for (SharedProject other : this.store.projects()) {
				if (other != project && other.containers().containsKey(key)) {
					this.sendError(player, payload.requestId(), "logisticmatica.share.error.container_bound");
					return;
				}
			}
			Map<String, Integer> snapshot = ServerContainerAccess.snapshot(level, pos);
			if (snapshot == null) {
				this.sendError(player, payload.requestId(), "logisticmatica.share.error.not_container");
				return;
			}
			if (snapshot.size() > ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER) {
				this.sendError(player, payload.requestId(), "logisticmatica.share.error.container_item_types");
				return;
			}
			project.putContainer(key, snapshot);
			nowMarked = true;
		}
		this.changed(project, player);
		this.containerScanDirty = true;
		if (nowMarked) {
			this.sendContainerDelta(project, List.of(project.containerView(key)), List.of());
		} else {
			this.sendContainerDelta(project, List.of(), List.of(wireKey(key)));
		}
		this.sendNotice(player, payload.requestId(),
				nowMarked ? "logisticmatica.message.mark.marked" : "logisticmatica.message.mark.unmarked");
	}

	private void handleContainerRefresh(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(payload.body());
		UUID projectId = reader.readUuid();
		String dimension = reader.readString();
		SharedProject.ContainerKey key = new SharedProject.ContainerKey(dimension,
				reader.readInt(), reader.readInt(), reader.readInt());
		reader.requireFinished();
		SharedProject project = this.requireProject(player, projectId, SharePermission.MANAGE_CONTAINERS, payload.requestId());
		if (project == null || !project.containers().containsKey(key)) {
			return;
		}
		ServerLevel level = ServerContainerAccess.level(player.level().getServer(), dimension);
		Map<String, Integer> snapshot = level != null ? ServerContainerAccess.snapshot(level,
				new BlockPos(key.x(), key.y(), key.z())) : null;
		if (snapshot != null && snapshot.size() <= ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER
				&& project.refreshContainer(key, snapshot)) {
			project.commitContainerRefresh();
			this.store.save();
			this.sendContainerDelta(project, List.of(project.containerView(key)), List.of());
		}
	}

	private void onServerTick(MinecraftServer server) {
		long tick = server.getTickCount();
		this.store.tick(tick);
		this.flushProjectUpdates(server);
		this.flushOutboundSync(server);
		if (tick < this.nextContainerScanTick) {
			return;
		}
		this.nextContainerScanTick = tick + CONTAINER_SCAN_INTERVAL_TICKS;
		if (this.containerScanDirty) this.rebuildContainerScanOrder(server);
		int total = this.containerScanOrder.size();
		if (total == 0) {
			this.containerScanCursor = 0;
			return;
		}
		int start = Math.floorMod(this.containerScanCursor, total);
		int budget = Math.min(CONTAINER_SCANS_PER_PASS, total);
		long deadline = System.nanoTime() + CONTAINER_SCAN_BUDGET_NANOS;
		int scanned = 0;
		Map<SharedProject, List<SharedProject.ContainerKey>> changed = new LinkedHashMap<>();
		for (int offset = 0; offset < budget; offset++) {
			if (offset > 0 && System.nanoTime() >= deadline) break;
			ContainerScanTarget target = this.containerScanOrder.get((start + offset) % total);
			SharedProject project = target.project();
			SharedProject.ContainerKey key = target.key();
			ServerLevel level = ServerContainerAccess.level(server, key.dimension());
			Map<String, Integer> snapshot = level != null ? ServerContainerAccess.snapshot(level,
					new BlockPos(key.x(), key.y(), key.z())) : null;
			scanned++;
			if (snapshot != null && snapshot.size() <= ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER
					&& project.refreshContainer(key, snapshot)) {
				changed.computeIfAbsent(project, ignored -> new ArrayList<>()).add(key);
			}
		}
		if (scanned > 0) this.containerScanCursor = (start + scanned) % total;
		if (!changed.isEmpty()) {
			for (Map.Entry<SharedProject, List<SharedProject.ContainerKey>> entry : changed.entrySet()) {
				entry.getKey().commitContainerRefresh();
			}
			this.store.save();
			changed.forEach((project, keys) -> this.sendContainerDelta(project,
					keys.stream().map(project::containerView).toList(), List.of()));
		}
	}

	private void rebuildContainerScanOrder(MinecraftServer server) {
		Set<UUID> activeProjects = new HashSet<>();
		Iterator<Map.Entry<UUID, UUID>> subscriptions = this.subscriptions.entrySet().iterator();
		while (subscriptions.hasNext()) {
			Map.Entry<UUID, UUID> entry = subscriptions.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			SharedProject project = this.store.get(entry.getValue());
			if (player == null || project == null
					|| (!this.isAdministrator(player) && !project.can(player.getUUID(), SharePermission.VIEW))) {
				subscriptions.remove();
				this.cancelPlayerSync(entry.getKey());
				continue;
			}
			activeProjects.add(entry.getValue());
		}
		for (PendingSnapshot snapshot : this.pendingSnapshots.values()) {
			activeProjects.remove(snapshot.projectId);
		}

		List<ContainerScanTarget> order = new ArrayList<>();
		for (SharedProject project : this.store.projects()) {
			if (!activeProjects.contains(project.id())) continue;
			for (SharedProject.ContainerKey key : project.containers().keySet()) {
				order.add(new ContainerScanTarget(project, key));
			}
		}
		this.containerScanOrder = List.copyOf(order);
		this.containerScanDirty = false;
		if (this.containerScanCursor >= this.containerScanOrder.size()) this.containerScanCursor = 0;
	}

	private int totalContainerCount() {
		return this.store.projects().stream().mapToInt(project -> project.containers().size()).sum();
	}

	private void changed(SharedProject project, ServerPlayer actor) {
		this.store.save();
		this.pendingProjectRecipients.computeIfAbsent(project.id(), ignored -> new LinkedHashSet<>())
				.add(actor.getUUID());
	}

	private void sendProjects(ServerPlayer player, UUID requestId) {
		boolean administrator = this.isAdministrator(player);
		List<SharedProjectView> projects = this.store.projects().stream()
				.sorted(Comparator.comparing(SharedProject::name, String.CASE_INSENSITIVE_ORDER))
				.map(project -> project.viewFor(player.getUUID(), administrator))
				.toList();
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.PROJECTS,
				requestId, ShareWire.encodeProjects(projects)));
	}

	private void sendPlayers(ServerPlayer player, UUID requestId) {
		MinecraftServer server = this.server;
		if (server == null) return;
		List<SharedPlayerView> players = server.getPlayerList().getPlayers().stream()
				.sorted(Comparator.comparing(online -> online.getName().getString(), String.CASE_INSENSITIVE_ORDER))
				.limit(ShareProtocol.MAX_ONLINE_PLAYERS)
				.map(online -> new SharedPlayerView(online.getUUID(), online.getName().getString()))
				.toList();
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.PLAYERS,
				requestId, ShareWire.encodePlayers(players)));
	}

	private void flushProjectUpdates(MinecraftServer server) {
		int budget = PROJECT_PACKETS_PER_TICK;
		Iterator<Map.Entry<UUID, Set<UUID>>> updates = this.pendingProjectRecipients.entrySet().iterator();
		while (updates.hasNext() && budget > 0) {
			Map.Entry<UUID, Set<UUID>> update = updates.next();
			SharedProject project = this.store.get(update.getKey());
			if (project == null) {
				updates.remove();
				continue;
			}
			this.subscriptions.forEach((playerId, projectId) -> {
				if (project.id().equals(projectId)) update.getValue().add(playerId);
			});
			Iterator<UUID> recipients = update.getValue().iterator();
			while (recipients.hasNext() && budget > 0) {
				ServerPlayer player = server.getPlayerList().getPlayer(recipients.next());
				recipients.remove();
				if (player == null) continue;
				this.sendProject(player, project, UUID.randomUUID());
				budget--;
			}
			if (update.getValue().isEmpty()) updates.remove();
		}
	}

	private void sendProject(ServerPlayer player, SharedProject project, UUID requestId) {
		boolean administrator = this.isAdministrator(player);
		byte[] body = ShareWire.encodeProjects(List.of(project.viewFor(player.getUUID(), administrator)));
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.PROJECT_CHANGED,
				requestId, body));
	}


	private void queueSnapshot(ServerPlayer player, SharedProject project, UUID requestId) {
		UUID playerId = player.getUUID();
		this.pendingSnapshots.put(playerId, new PendingSnapshot(project, requestId));
		this.pendingDeltas.remove(playerId);
		this.enqueueOutbound(playerId);
		this.containerScanDirty = true;
	}

	private void sendContainerDelta(SharedProject project, List<SharedContainerView> upserts,
			List<SharedContainerKey> removals) {
		if (upserts.isEmpty() && removals.isEmpty()) return;
		MinecraftServer server = this.server;
		if (server == null) return;

		for (Map.Entry<UUID, UUID> subscription : List.copyOf(this.subscriptions.entrySet())) {
			if (!project.id().equals(subscription.getValue())) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(subscription.getKey());
			if (player == null || (!this.isAdministrator(player)
					&& !project.can(player.getUUID(), SharePermission.VIEW))) {
				this.disconnect(subscription.getKey());
				continue;
			}
			if (this.pendingSnapshots.containsKey(player.getUUID())) {
				this.queueSnapshot(player, project, UUID.randomUUID());
				continue;
			}
			PendingDelta delta = this.pendingDeltas.computeIfAbsent(player.getUUID(),
					ignored -> new PendingDelta(project.id()));
			if (!delta.merge(project.containerRevision(), upserts, removals)) {
				this.queueSnapshot(player, project, UUID.randomUUID());
				continue;
			}
			this.enqueueOutbound(player.getUUID());
		}
	}

	private void flushOutboundSync(MinecraftServer server) {
		long deadline = System.nanoTime() + SYNC_WORK_BUDGET_NANOS;
		int candidates = this.outboundPlayers.size();
		int sent = 0;
		for (int attempt = 0; attempt < candidates && sent < SYNC_PACKETS_PER_TICK; attempt++) {
			if (sent > 0 && System.nanoTime() >= deadline) break;
			UUID playerId = this.outboundPlayers.pollFirst();
			if (playerId == null) break;
			this.outboundQueued.remove(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			UUID projectId = this.subscriptions.get(playerId);
			SharedProject project = projectId != null ? this.store.get(projectId) : null;
			if (player == null || project == null || (!this.isAdministrator(player)
					&& !project.can(playerId, SharePermission.VIEW))) {
				this.disconnect(playerId);
				continue;
			}

			PendingSnapshot snapshot = this.pendingSnapshots.get(playerId);
			if (snapshot != null) {
				if (!project.id().equals(snapshot.projectId)
						|| project.containerRevision() != snapshot.revision) {
					this.pendingSnapshots.put(playerId, new PendingSnapshot(project, snapshot.requestId));
					this.enqueueOutbound(playerId);
					this.containerScanDirty = true;
					continue;
				}
				int from = snapshot.cursor;
				int to = Math.min(snapshot.keys.size(), from + SNAPSHOT_CONTAINERS_PER_PACKET);
				List<SharedContainerView> values = new ArrayList<>(Math.max(0, to - from));
				for (int index = from; index < to; index++) {
					SharedContainerView view = project.containerView(snapshot.keys.get(index));
					if (view != null) values.add(view);
				}
				boolean complete = to >= snapshot.keys.size();
				SharedContainerSnapshot packet = new SharedContainerSnapshot(project.id(), snapshot.revision,
						from == 0, complete, values);
				this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.CONTAINER_SNAPSHOT,
						snapshot.requestId, ShareWire.encodeContainerSnapshot(packet)));
				snapshot.cursor = to;
				sent++;
				if (complete) {
					this.pendingSnapshots.remove(playerId);
					this.containerScanDirty = true;
				} else {
					this.enqueueOutbound(playerId);
				}
				continue;
			}

			PendingDelta delta = this.pendingDeltas.remove(playerId);
			if (delta == null || !project.id().equals(delta.projectId)) continue;
			SharedContainerDelta packet = new SharedContainerDelta(project.id(), delta.revision(),
					delta.upserts(), delta.removals());
			this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.CONTAINERS_CHANGED,
					UUID.randomUUID(), ShareWire.encodeContainerDelta(packet)));
			sent++;
		}
	}

	private void enqueueOutbound(UUID playerId) {
		if (this.outboundQueued.add(playerId)) this.outboundPlayers.addLast(playerId);
	}

	private void cancelPlayerSync(UUID playerId) {
		this.pendingSnapshots.remove(playerId);
		this.pendingDeltas.remove(playerId);
		this.outboundQueued.remove(playerId);
	}

	private void disconnect(UUID playerId) {
		this.subscriptions.remove(playerId);
		this.cancelPlayerSync(playerId);
		this.pendingInbound.remove(playerId);
		this.staleResyncTicks.remove(playerId);
		this.actionWindows.remove(playerId);
		this.containerScanDirty = true;
	}

	private static SharedContainerKey wireKey(SharedProject.ContainerKey key) {
		return new SharedContainerKey(key.dimension(), key.x(), key.y(), key.z());
	}

	private static SharedContainerKey wireKey(SharedContainerView container) {
		return new SharedContainerKey(container.dimension(), container.x(), container.y(), container.z());
	}

	@Nullable
	private SharedProject requireProject(ServerPlayer player, UUID id, SharePermission permission, UUID requestId) {
		SharedProject project = this.store.get(id);
		if (project == null || (!this.isAdministrator(player) && !project.can(player.getUUID(), permission))) {
			this.sendError(player, requestId, "logisticmatica.share.error.permissions");
			return null;
		}
		return project;
	}

	private boolean checkRevision(ServerPlayer player, @Nullable SharedProject project, long expected, UUID requestId) {
		if (project == null) {
			return false;
		}
		if (project.revision() != expected) {
			this.sendError(player, requestId, "logisticmatica.share.error.stale");
			MinecraftServer server = this.server;
			long tick = server != null ? server.getTickCount() : 0L;
			long previous = this.staleResyncTicks.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
			if (tick - previous >= 20L) {
				this.staleResyncTicks.put(player.getUUID(), tick);
				this.sendProject(player, project, UUID.randomUUID());
			}
			return false;
		}
		return true;
	}

	private boolean isAdministrator(ServerPlayer player) {
		return Permissions.check(player, ADMIN_PERMISSION, PermissionLevel.ADMINS);
	}

	private ServerLevel requireDimension(MinecraftServer server, String dimension) throws IOException {
		ServerLevel level = ServerContainerAccess.level(server, dimension);
		if (level == null) {
			throw new IOException("Unknown dimension " + dimension);
		}
		return level;
	}

	private void send(ServerPlayer player, ClientboundSharePayload payload) {
		try {
			if (ServerPlayNetworking.canSend(player, ClientboundSharePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		} catch (RuntimeException e) {
			Logisticmatica.LOGGER.debug("[{}] Dropped sharing packet for disconnected player {}: {}",
					Logisticmatica.MOD_NAME, player.getName().getString(), e.getMessage());
			this.disconnect(player.getUUID());
		}
	}

	private void sendNotice(ServerPlayer player, UUID requestId, String translationKey, String... arguments) {
		byte[] body = ShareWire.encode(writer -> {
			writer.writeString(translationKey);
			writer.writeInt(arguments.length);
			for (String argument : arguments) writer.writeString(argument);
		});
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.NOTICE, requestId, body));
	}

	private void sendError(ServerPlayer player, UUID requestId, String translationKey) {
		byte[] body = ShareWire.encode(writer -> {
			writer.writeString(translationKey);
			writer.writeInt(0);
		});
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.ERROR, requestId, body));
	}

	private void sendRemoved(ServerPlayer player, UUID projectId) {
		byte[] body = ShareWire.encode(writer -> writer.writeUuid(projectId));
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.PROJECT_REMOVED,
				UUID.randomUUID(), body));
	}

	private static UUID readProjectId(byte[] body) throws IOException {
		ShareWire.Reader reader = ShareWire.decode(body);
		UUID id = reader.readUuid();
		reader.requireFinished();
		return id;
	}

	private static int checkedOrdinal(int ordinal, int count, String label) throws IOException {
		if (ordinal < 0 || ordinal >= count) {
			throw new IOException("Invalid " + label + " ordinal " + ordinal);
		}
		return ordinal;
	}

	private static String cleanName(String name) throws IOException {
		String clean = name.strip();
		if (clean.isEmpty() || clean.length() > 128) {
			throw new IOException("Project name must contain 1-128 characters");
		}
		return clean;
	}
}
