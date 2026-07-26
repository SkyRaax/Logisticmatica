package com.skyraax.logisticmatica.client.share;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.interfaces.ISchematicPlacementEventListener;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementEventFlag;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementEventHandler;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.ContainerTracker;
import com.skyraax.logisticmatica.client.SubstitutionManager;
import com.skyraax.logisticmatica.client.Substitutions;
import com.skyraax.logisticmatica.client.gui.SharingRefreshable;
import com.skyraax.logisticmatica.share.ClientboundSharePayload;
import com.skyraax.logisticmatica.share.ServerboundSharePayload;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.ShareProtocol;
import com.skyraax.logisticmatica.share.ShareWire;
import com.skyraax.logisticmatica.share.SharedContainerView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Client bridge between the authoritative server model and real Litematica placements. */
public final class ClientShareManager implements ISchematicPlacementEventListener {
	private static final ClientShareManager INSTANCE = new ClientShareManager();
	private final Map<UUID, SharedProjectView> projects = new LinkedHashMap<>();
	private final Map<UUID, SchematicPlacement> placements = new HashMap<>();
	private final Map<UUID, SchematicPlacement> pendingCreates = new HashMap<>();
	private final Map<UUID, SchematicPlacement> replacements = new HashMap<>();
	private boolean serverAvailable;
	@Nullable private UUID serverId;
	private boolean applyingRemote;
	private String serverVersion = "";
	private int serverFeatures;
	private int serverMaxBytes = ShareProtocol.MAX_SCHEMATIC_BYTES;

	private ClientShareManager() {}
	public static ClientShareManager getInstance() { return INSTANCE; }

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ClientboundSharePayload.TYPE, (payload, context) ->
				context.client().execute(() -> INSTANCE.handle(payload)));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> INSTANCE.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.reset());
		SchematicPlacementEventHandler.getInstance().registerSchematicPlacementEventListener(
				INSTANCE, List.of(SchematicPlacementEventFlag.ALL_EVENTS));
	}

	public boolean serverAvailable() { return this.serverAvailable; }
	public String serverVersion() { return this.serverVersion; }
	public int serverFeatures() { return this.serverFeatures; }
	public List<SharedProjectView> projects() {
		return this.projects.values().stream()
				.sorted(Comparator.comparing(SharedProjectView::name, String.CASE_INSENSITIVE_ORDER)).toList();
	}
	@Nullable public SharedProjectView project(UUID id) { return this.projects.get(id); }
	@Nullable public SchematicPlacement placement(UUID id) { return this.placements.get(id); }

	@Nullable
	public SharedProjectView projectFor(LitematicaSchematic schematic) {
		for (Map.Entry<UUID, SchematicPlacement> entry : this.placements.entrySet()) {
			if (entry.getValue().getSchematic() == schematic) return this.projects.get(entry.getKey());
		}
		return null;
	}

	private void onJoin() {
		this.reset();
		if (ClientPlayNetworking.canSend(ServerboundSharePayload.TYPE)) {
			byte[] body = ShareWire.encode(w -> w.writeString(Logisticmatica.MOD_VERSION));
			this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.HELLO, body));
		}
	}

	private void reset() {
		this.serverAvailable = false;
		this.serverVersion = "";
		this.serverId = null;
		this.serverFeatures = 0;
		this.projects.clear();
		this.placements.clear();
		this.pendingCreates.clear();
		this.replacements.clear();
		this.refreshScreen();
	}

	private void handle(ClientboundSharePayload payload) {
		if (payload.protocolVersion() != ShareProtocol.VERSION) {
			this.serverAvailable = false;
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.protocol");
			return;
		}
		try {
			switch (payload.event()) {
				case HELLO -> this.handleHello(payload.body());
				case PROJECTS -> this.handleProjects(payload.body());
				case PROJECT_CHANGED -> this.handleProjectChanged(payload.requestId(), payload.body());
				case PROJECT_DATA -> this.handleProjectData(payload.body());
				case PROJECT_REMOVED -> this.handleProjectRemoved(payload.body());
				case NOTICE -> this.showMessage(payload.body(), MessageType.SUCCESS);
				case ERROR -> this.showMessage(payload.body(), MessageType.ERROR);
			}
		} catch (IOException | RuntimeException e) {
			Logisticmatica.LOGGER.error("[{}] Invalid sharing response {}: {}",
					Logisticmatica.MOD_NAME, payload.event(), e.getMessage(), e);
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.invalid_response");
		}
		this.refreshScreen();
	}

	private void handleHello(byte[] body) throws IOException {
		ShareWire.Reader r = ShareWire.decode(body);
		this.serverAvailable = r.readInt() == ShareProtocol.VERSION;
		this.serverFeatures = r.readInt();
		this.serverMaxBytes = r.readInt();
		this.serverVersion = r.readString();
		this.serverId = r.readUuid();
		r.requireFinished();
		if (this.serverAvailable) InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS,
				"logisticmatica.share.notice.handshake", this.serverVersion);
	}

	private void handleProjects(byte[] body) throws IOException {
		this.projects.clear();
		for (SharedProjectView project : ShareWire.decodeProjects(body)) this.projects.put(project.id(), project);
		this.reconcilePlacements();
	}

	private void handleProjectChanged(UUID requestId, byte[] body) throws IOException {
		List<SharedProjectView> changed = ShareWire.decodeProjects(body);
		if (changed.size() != 1) throw new IOException("Expected one changed project");
		SharedProjectView project = changed.getFirst();
		this.projects.put(project.id(), project);
		SchematicPlacement created = this.pendingCreates.remove(requestId);
		if (created != null) {
			this.replacements.put(project.id(), created);
			this.download(project.id());
		}
		SchematicPlacement placement = this.placements.get(project.id());
		if (placement != null) {
			Minecraft mc = Minecraft.getInstance();
			boolean correctDimension = mc.level != null
					&& mc.level.dimension().identifier().toString().equals(project.dimension());
			if (!correctDimension) {
				this.applyingRemote = true;
				try { DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement); }
				finally { this.applyingRemote = false; }
				this.placements.remove(project.id());
			} else if (!String.valueOf(placement.getSchematicFile()).contains(project.schematicHash())) {
				this.download(project.id());
			} else {
				this.applyProject(project, placement);
			}
		}
		this.syncContainers();
	}

	private void handleProjectData(byte[] body) throws IOException {
		ShareWire.Reader r = ShareWire.decode(body);
		UUID id = r.readUuid();
		long revision = r.readLong();
		String hash = r.readString();
		String name = r.readString();
		byte[] schematicBytes = r.readBytes(Math.min(this.serverMaxBytes, ShareProtocol.MAX_SCHEMATIC_BYTES));
		r.requireFinished();
		if (!hash.equals(sha256(schematicBytes))) throw new IOException("Schematic hash mismatch");
		SharedProjectView project = this.projects.get(id);
		if (project == null || !project.schematicHash().equals(hash) || project.revision() < revision) {
			this.refreshProjects();
			throw new IOException("Stale schematic metadata");
		}
		Path file = this.sharedDirectory().resolve(id + "-" + hash + ".litematic");
		Files.createDirectories(file.getParent());
		Files.write(file, schematicBytes);
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !mc.level.dimension().identifier().toString().equals(project.dimension())) {
			InfoUtils.showGuiOrInGameMessage(MessageType.INFO,
					"logisticmatica.share.notice.saved_other_dimension", name);
			return;
		}
		SchematicPlacement old = this.replacements.remove(id);
		if (old == null) old = this.placements.remove(id);
		if (old != null) {
			this.applyingRemote = true;
			try { DataManager.getSchematicPlacementManager().removeSchematicPlacement(old); }
			finally { this.applyingRemote = false; }
		}
		this.loadPlacement(project, file);
	}

	private void handleProjectRemoved(byte[] body) throws IOException {
		ShareWire.Reader r = ShareWire.decode(body);
		UUID id = r.readUuid();
		r.requireFinished();
		this.projects.remove(id);
		SchematicPlacement placement = this.placements.remove(id);
		if (placement != null) {
			this.applyingRemote = true;
			try { DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement); }
			finally { this.applyingRemote = false; }
		}
		this.syncContainers();
	}

	private void showMessage(byte[] body, MessageType type) throws IOException {
		ShareWire.Reader r = ShareWire.decode(body);
		String key = r.readString();
		r.requireFinished();
		InfoUtils.showGuiOrInGameMessage(type, key);
	}

	private void reconcilePlacements() {
		this.placements.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		String dimension = mc.level.dimension().identifier().toString();

		Path serverDirectory = this.sharedDirectory().toAbsolutePath().normalize();
		for (SchematicPlacement placement : List.copyOf(
				DataManager.getSchematicPlacementManager().getAllSchematicsPlacements())) {
			SharedProjectView project = this.projects.get(placement.getHashId());
			Path schematicFile = placement.getSchematicFile();
			boolean fromThisServer = schematicFile != null
					&& schematicFile.toAbsolutePath().normalize().startsWith(serverDirectory);
			if (fromThisServer && (project == null || project.pendingInvite()
					|| !project.dimension().equals(dimension))) {
				this.applyingRemote = true;
				try { DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement); }
				finally { this.applyingRemote = false; }
				continue;
			}
			if (project != null && !project.pendingInvite() && project.dimension().equals(dimension)) {
				this.placements.put(project.id(), placement);
				this.applyProject(project, placement);
			}
		}

		for (SharedProjectView project : this.projects.values()) {
			if (!project.pendingInvite() && project.dimension().equals(dimension)
					&& !this.placements.containsKey(project.id())) {
				Path cached = this.sharedDirectory().resolve(project.id() + "-" + project.schematicHash() + ".litematic");
				if (Files.isRegularFile(cached)) this.loadPlacement(project, cached);
			}
		}
		this.syncContainers();
	}

	private void loadPlacement(SharedProjectView project, Path file) {
		LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file);
		if (schematic == null) {
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.load_schematic");
			return;
		}
		SchematicPlacement placement = SchematicPlacement.createFor(schematic,
				new BlockPos(project.x(), project.y(), project.z()), project.name(), true, false, project.id());
		this.applyingRemote = true;
		try {
			placement.setRotation(Rotation.values()[project.rotation()], null);
			placement.setMirror(Mirror.values()[project.mirror()], null);
			if (placement.isLocked() == project.can(SharePermission.MOVE)) placement.toggleLocked();
			DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
			this.placements.put(project.id(), placement);
			this.applySubstitutions(project, schematic);
		} finally {
			this.applyingRemote = false;
		}
		this.syncContainers();
	}

	private void applyProject(SharedProjectView project, SchematicPlacement placement) {
		this.applyingRemote = true;
		try {
			BlockPos origin = new BlockPos(project.x(), project.y(), project.z());
			if (!placement.getOrigin().equals(origin)) placement.setOrigin(origin, null);
			Rotation rotation = Rotation.values()[project.rotation()];
			if (placement.getRotation() != rotation) placement.setRotation(rotation, null);
			Mirror mirror = Mirror.values()[project.mirror()];
			if (placement.getMirror() != mirror) placement.setMirror(mirror, null);
			if (placement.isLocked() == project.can(SharePermission.MOVE)) placement.toggleLocked();
			this.applySubstitutions(project, placement.getSchematic());
		} finally {
			this.applyingRemote = false;
		}
	}

	private void applySubstitutions(SharedProjectView project, LitematicaSchematic schematic) {
		Map<Block, Block> blocks = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : project.substitutions().entrySet()) {
			Block from = Substitutions.blockOf(entry.getKey());
			Block to = Substitutions.blockOf(entry.getValue());
			if (from != null && to != null) blocks.put(from, to);
		}
		SubstitutionManager.getInstance().replaceAll(schematic, blocks);
	}

	public void refreshProjects() {
		if (this.requireServer()) this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.LIST_PROJECTS,
				new byte[0]));
	}

	public void download(UUID projectId) {
		if (this.requireServer()) this.sendProjectId(ShareProtocol.ServerboundAction.DOWNLOAD_PROJECT, projectId);
	}

	public void shareSelectedPlacement() {
		if (!this.requireServer()) return;
		SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
		if (placement == null) {
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "logisticmatica.share.error.no_selection");
			return;
		}
		Path file = placement.getSchematicFile();
		if (file == null || !Files.isRegularFile(file)) {
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.unsaved_schematic");
			return;
		}
		try {
			byte[] schematic = Files.readAllBytes(file);
			if (schematic.length > this.serverMaxBytes) {
				InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.schematic_too_large");
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			BlockPos origin = placement.getOrigin();
			byte[] body = ShareWire.encode(w -> {
				w.writeString(placement.getName());
				w.writeString(mc.level.dimension().identifier().toString());
				w.writeInt(origin.getX());
				w.writeInt(origin.getY());
				w.writeInt(origin.getZ());
				w.writeInt(placement.getRotation().ordinal());
				w.writeInt(placement.getMirror().ordinal());
				w.writeBytes(schematic, ShareProtocol.MAX_SCHEMATIC_BYTES);
			});
			ServerboundSharePayload payload = ServerboundSharePayload.of(ShareProtocol.ServerboundAction.CREATE_PROJECT, body);
			this.pendingCreates.put(payload.requestId(), placement);
			this.send(payload);
		} catch (IOException e) {
			Logisticmatica.LOGGER.error("[{}] Could not read schematic for sharing", Logisticmatica.MOD_NAME, e);
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.read_schematic");
		}
	}

	public void uploadCurrentSchematic(UUID projectId) {
		SharedProjectView project = this.projects.get(projectId);
		SchematicPlacement placement = this.placements.get(projectId);
		if (project == null || placement == null || !project.can(SharePermission.UPDATE_SCHEMATIC)) return;
		Path file = placement.getSchematicFile();
		if (file == null || !Files.isRegularFile(file)) {
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.unsaved_schematic");
			return;
		}
		try {
			byte[] schematic = Files.readAllBytes(file);
			byte[] body = ShareWire.encode(w -> {
				w.writeUuid(projectId);
				w.writeLong(project.revision());
				w.writeBytes(schematic, ShareProtocol.MAX_SCHEMATIC_BYTES);
			});
			this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.UPDATE_SCHEMATIC, body));
		} catch (IOException e) {
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.read_schematic");
		}
	}

	public void invite(UUID projectId, String playerName, int permissions) {
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(projectId);
			w.writeString(playerName);
			w.writeInt(permissions);
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.INVITE, body));
	}

	public void respondToInvite(UUID projectId, boolean accepted) {
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(projectId);
			w.writeBoolean(accepted);
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.RESPOND_INVITE, body));
	}

	public void setPermissions(UUID projectId, UUID playerId, int permissions) {
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(projectId);
			w.writeUuid(playerId);
			w.writeInt(permissions);
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.SET_PERMISSIONS, body));
	}

	public void removeMember(UUID projectId, UUID playerId) {
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(projectId);
			w.writeUuid(playerId);
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.REMOVE_MEMBER, body));
	}

	public void delete(UUID projectId) { this.sendProjectId(ShareProtocol.ServerboundAction.DELETE_PROJECT, projectId); }
	public void leave(UUID projectId) { this.sendProjectId(ShareProtocol.ServerboundAction.LEAVE_PROJECT, projectId); }

	/** Returns true when the schematic is shared, even when the mutation is denied locally. */
	public boolean toggleContainerFor(LitematicaSchematic schematic, BlockPos pos) {
		SharedProjectView project = this.projectFor(schematic);
		if (project == null) return false;
		if (!project.can(SharePermission.MANAGE_CONTAINERS)) {
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.permissions");
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return true;
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(project.id());
			w.writeString(mc.level.dimension().identifier().toString());
			w.writeInt(pos.getX());
			w.writeInt(pos.getY());
			w.writeInt(pos.getZ());
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.TOGGLE_CONTAINER, body));
		return true;
	}

	public void onLocalSubstitutionsChanged(LitematicaSchematic schematic) {
		if (this.applyingRemote) return;
		SharedProjectView project = this.projectFor(schematic);
		if (project == null) return;
		if (!project.can(SharePermission.SUBSTITUTE)) {
			this.applyingRemote = true;
			try { this.applySubstitutions(project, schematic); }
			finally { this.applyingRemote = false; }
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.permissions");
			return;
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (Map.Entry<Block, Block> entry : SubstitutionManager.getInstance().getAll(schematic).entrySet()) {
			values.put(Substitutions.idOf(entry.getKey()), Substitutions.idOf(entry.getValue()));
		}
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(project.id());
			w.writeLong(project.revision());
			w.writeStringMap(values, ShareProtocol.MAX_SUBSTITUTIONS_PER_PROJECT);
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.UPDATE_SUBSTITUTIONS, body));
	}

	public void syncContainers() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		String dimension = mc.level.dimension().identifier().toString();
		ContainerTracker tracker = ContainerTracker.getInstance();
		tracker.clearServerBindings();
		for (SharedProjectView project : this.projects.values()) {
			SchematicPlacement placement = this.placements.get(project.id());
			if (placement == null || !project.dimension().equals(dimension)) continue;
			for (SharedContainerView container : project.containers()) {
				if (container.dimension().equals(dimension)) {
					tracker.setServerBinding(placement.getSchematic(),
							new BlockPos(container.x(), container.y(), container.z()), container.items());
				}
			}
		}
	}

	/** Reconciles cached shared placements after a world or dimension transition. */
	public void onWorldChanged() {
		if (this.serverAvailable) this.reconcilePlacements();
	}

	@Override
	public void onPlacementAdded(SchematicPlacement placement) {
		if (!this.applyingRemote && this.projects.containsKey(placement.getHashId())) {
			this.placements.put(placement.getHashId(), placement);
			this.applyProject(this.projects.get(placement.getHashId()), placement);
		}
	}

	@Override
	public void onPlacementRemoved(SchematicPlacement placement) {
		this.placements.remove(placement.getHashId(), placement);
	}

	@Override
	public void onPlacementUpdated(SchematicPlacement placement) {
		if (this.applyingRemote) return;
		SharedProjectView project = this.projects.get(placement.getHashId());
		if (project == null || !project.can(SharePermission.MOVE)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		BlockPos origin = placement.getOrigin();
		byte[] body = ShareWire.encode(w -> {
			w.writeUuid(project.id());
			w.writeLong(project.revision());
			w.writeString(mc.level.dimension().identifier().toString());
			w.writeInt(origin.getX());
			w.writeInt(origin.getY());
			w.writeInt(origin.getZ());
			w.writeInt(placement.getRotation().ordinal());
			w.writeInt(placement.getMirror().ordinal());
		});
		this.send(ServerboundSharePayload.of(ShareProtocol.ServerboundAction.UPDATE_TRANSFORM, body));
	}

	@Override
	public void onToggleLocked(SchematicPlacement placement, boolean locked) {
		if (this.applyingRemote || locked) return;
		SharedProjectView project = this.projects.get(placement.getHashId());
		if (project != null && !project.can(SharePermission.MOVE)) {
			this.applyingRemote = true;
			try { placement.toggleLocked(); }
			finally { this.applyingRemote = false; }
			InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.permissions");
		}
	}

	private void sendProjectId(ShareProtocol.ServerboundAction action, UUID projectId) {
		this.send(ServerboundSharePayload.of(action, ShareWire.encode(w -> w.writeUuid(projectId))));
	}

	private boolean requireServer() {
		if (this.serverAvailable && ClientPlayNetworking.canSend(ServerboundSharePayload.TYPE)) return true;
		InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "logisticmatica.share.error.no_server");
		return false;
	}

	private void send(ServerboundSharePayload payload) {
		if (ClientPlayNetworking.canSend(ServerboundSharePayload.TYPE)) ClientPlayNetworking.send(payload);
	}

	private Path sharedDirectory() {
		String server = this.serverId != null ? this.serverId.toString() : "unknown";
		return FileUtils.getConfigDirectory().resolve(Logisticmatica.MOD_ID).resolve("shared").resolve(server);
	}

	private static String sha256(byte[] bytes) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 unavailable", e);
		}
	}

	private void refreshScreen() {
		if (GuiUtils.getCurrentScreen() instanceof SharingRefreshable refreshable) refreshable.refreshSharing();
	}
}
