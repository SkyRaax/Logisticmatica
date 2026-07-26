package com.skyraax.logisticmatica.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import me.lucko.fabric.api.permissions.v0.Permissions;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.share.ClientboundSharePayload;
import com.skyraax.logisticmatica.share.ServerboundSharePayload;
import com.skyraax.logisticmatica.share.ShareAccess;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.ShareProtocol;
import com.skyraax.logisticmatica.share.ShareWire;
import com.skyraax.logisticmatica.share.SharedPlayerView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Server-authoritative sharing service: validates every mutation, persists it and broadcasts views. */
public final class ShareServer {
	private static final ShareServer INSTANCE = new ShareServer();
	private static final String ADMIN_PERMISSION = Logisticmatica.MOD_ID + ".admin";
	private static final int CONTAINER_REFRESH_INTERVAL = 20;
	private static final int CONTAINER_SCANS_PER_INTERVAL = 256;
	private static final double CONTAINER_BIND_DISTANCE_SQ = 64.0;

	private final ShareStore store = new ShareStore();
	private int tickCounter;
	private int containerScanCursor;
	@Nullable private MinecraftServer server;

	private ShareServer() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ServerboundSharePayload.TYPE, (payload, context) ->
				context.server().execute(() -> INSTANCE.handle(payload, context.player())));
		ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(INSTANCE::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onServerTick);
	}

	private void onServerStarted(MinecraftServer server) {
		this.server = server;
		this.store.start(server);
		this.tickCounter = 0;
		this.containerScanCursor = 0;
	}

	private void onServerStopping(MinecraftServer server) {
		this.store.stop();
		this.server = null;
	}

	private void handle(ServerboundSharePayload payload, ServerPlayer player) {
		if (payload.protocolVersion() != ShareProtocol.VERSION) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.protocol");
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
		this.sendProjects(player, UUID.randomUUID());
		this.sendPlayers(player, UUID.randomUUID());
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
		reader.requireFinished();
		this.requireDimension(player.level().getServer(), dimension);

		String hash = this.store.storeSchematic(schematic);
		SharedProject project = new SharedProject(UUID.randomUUID(), player.getUUID(), player.getName().getString(),
				name, dimension, x, y, z, rotation, mirror, hash, schematic.length);
		this.store.put(project);
		this.store.save();
		this.sendProjectChanged(project);
		this.sendProject(player, project, payload.requestId());
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.created");
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
		this.changed(project);
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
		this.changed(project);
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
		this.changed(project);
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
		this.changed(project);
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
		this.changed(project);
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
		this.changed(project);
		this.sendNotice(player, payload.requestId(), "logisticmatica.share.notice.public_access_updated");
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
		this.changed(project);
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
		this.changed(project);
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
		this.changed(project);
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
		this.changed(project);
	}


	private void handleDelete(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		UUID projectId = readProjectId(payload.body());
		SharedProject project = this.requireProject(player, projectId, SharePermission.DELETE, payload.requestId());
		if (project == null) {
			return;
		}
		this.store.remove(projectId);
		this.store.save();
		for (ServerPlayer online : player.level().getServer().getPlayerList().getPlayers()) {
			this.sendRemoved(online, projectId);
		}
	}

	private void handleLeave(ServerPlayer player, ServerboundSharePayload payload) throws IOException {
		UUID projectId = readProjectId(payload.body());
		SharedProject project = this.store.get(projectId);
		if (project == null || project.ownerId().equals(player.getUUID()) || !project.removeMember(player.getUUID())) {
			this.sendError(player, payload.requestId(), "logisticmatica.share.error.cannot_leave");
			return;
		}
		this.changed(project);
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
		this.changed(project);
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
			this.changed(project);
		}
	}

	private void onServerTick(MinecraftServer server) {
		if (++this.tickCounter < CONTAINER_REFRESH_INTERVAL) {
			return;
		}
		this.tickCounter = 0;

		int total = this.totalContainerCount();
		if (total == 0) {
			this.containerScanCursor = 0;
			return;
		}
		int start = Math.floorMod(this.containerScanCursor, total);
		int budget = Math.min(CONTAINER_SCANS_PER_INTERVAL, total);
		int index = 0;
		Set<SharedProject> changed = new LinkedHashSet<>();
		for (SharedProject project : this.store.projects()) {
			for (SharedProject.ContainerKey key : project.containers().keySet()) {
				int relative = Math.floorMod(index++ - start, total);
				if (relative >= budget) continue;
				ServerLevel level = ServerContainerAccess.level(server, key.dimension());
				Map<String, Integer> snapshot = level != null ? ServerContainerAccess.snapshot(level,
						new BlockPos(key.x(), key.y(), key.z())) : null;
				if (snapshot != null && snapshot.size() <= ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER
						&& project.refreshContainer(key, snapshot)) {
					changed.add(project);
				}
			}
		}
		this.containerScanCursor = (start + budget) % total;
		if (!changed.isEmpty()) {
			this.store.save();
			changed.forEach(this::sendProjectChanged);
		}
	}

	private int totalContainerCount() {
		return this.store.projects().stream().mapToInt(project -> project.containers().size()).sum();
	}

	private void changed(SharedProject project) {
		this.store.save();
		this.sendProjectChanged(project);
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

	private void sendProjectChanged(SharedProject project) {
		MinecraftServer server = this.server;
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			this.sendProject(player, project, UUID.randomUUID());
		}
	}

	private void sendProject(ServerPlayer player, SharedProject project, UUID requestId) {
		boolean administrator = this.isAdministrator(player);
		byte[] body = ShareWire.encodeProjects(List.of(project.viewFor(player.getUUID(), administrator)));
		this.send(player, ClientboundSharePayload.of(ShareProtocol.ClientboundEvent.PROJECT_CHANGED,
				requestId, body));
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
			this.sendProjects(player, UUID.randomUUID());
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
		if (ServerPlayNetworking.canSend(player, ClientboundSharePayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
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
