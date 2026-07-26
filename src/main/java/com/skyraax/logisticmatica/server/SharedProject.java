package com.skyraax.logisticmatica.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.skyraax.logisticmatica.share.ShareAccess;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedContainerView;
import com.skyraax.logisticmatica.share.SharedMemberView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Mutable server-owned state for one shared placement. Accessed only on the logical server thread. */
public final class SharedProject {
	public static final int SCHEMA_VERSION = 2;

	public record ContainerKey(String dimension, int x, int y, int z) {
	}

	public static final class Member {
		private final UUID playerId;
		private String playerName;
		private int permissions;
		private boolean accepted;
		private boolean accessRequested;

		public Member(UUID playerId, String playerName, int permissions, boolean accepted, boolean accessRequested) {
			this.playerId = playerId;
			this.playerName = playerName;
			this.permissions = SharePermission.sanitize(permissions) | SharePermission.VIEW.mask();
			this.accepted = accepted;
			this.accessRequested = accessRequested && !accepted;
		}

		public Member(UUID playerId, String playerName, int permissions, boolean accepted) {
			this(playerId, playerName, permissions, accepted, false);
		}

		public UUID playerId() { return this.playerId; }
		public String playerName() { return this.playerName; }
		public int permissions() { return this.permissions; }
		public boolean accepted() { return this.accepted; }
		public boolean accessRequested() { return this.accessRequested; }
		public void setPlayerName(String playerName) { this.playerName = playerName; }
		public void setPermissions(int permissions) {
			this.permissions = SharePermission.sanitize(permissions) | SharePermission.VIEW.mask();
		}
		public void setAccepted(boolean accepted) {
			this.accepted = accepted;
			if (accepted) this.accessRequested = false;
		}
		public void setAccessRequested(boolean requested) { this.accessRequested = requested && !this.accepted; }
	}

	private final UUID id;
	private final UUID ownerId;
	private String ownerName;
	private String name;
	private long revision;
	private String dimension;
	private int x;
	private int y;
	private int z;
	private int rotation;
	private int mirror;
	private String schematicHash;
	private int schematicSize;
	private ShareAccess publicAccess = ShareAccess.REQUEST_ONLY;
	private final Map<UUID, Member> members = new LinkedHashMap<>();
	private final Map<String, String> substitutions = new LinkedHashMap<>();
	private final Map<ContainerKey, Map<String, Integer>> containers = new LinkedHashMap<>();

	public SharedProject(UUID id, UUID ownerId, String ownerName, String name, String dimension,
			int x, int y, int z, int rotation, int mirror, String schematicHash, int schematicSize) {
		this.id = id;
		this.ownerId = ownerId;
		this.ownerName = ownerName;
		this.name = name;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.rotation = rotation;
		this.mirror = mirror;
		this.schematicHash = schematicHash;
		this.schematicSize = schematicSize;
		this.revision = 1L;
	}

	public UUID id() { return this.id; }
	public UUID ownerId() { return this.ownerId; }
	public String ownerName() { return this.ownerName; }
	public String name() { return this.name; }
	public long revision() { return this.revision; }
	public String dimension() { return this.dimension; }
	public int x() { return this.x; }
	public int y() { return this.y; }
	public int z() { return this.z; }
	public int rotation() { return this.rotation; }
	public int mirror() { return this.mirror; }
	public String schematicHash() { return this.schematicHash; }
	public int schematicSize() { return this.schematicSize; }
	public ShareAccess publicAccess() { return this.publicAccess; }
	public Map<UUID, Member> members() { return this.members; }
	public Map<String, String> substitutions() { return this.substitutions; }
	public Map<ContainerKey, Map<String, Integer>> containers() { return this.containers; }

	public boolean visibleTo(UUID playerId) {
		return true;
	}

	public boolean acceptedBy(UUID playerId) {
		if (this.ownerId.equals(playerId)) {
			return true;
		}
		Member member = this.members.get(playerId);
		return member != null && member.accepted();
	}

	public boolean accessRequestedBy(UUID playerId) {
		Member member = this.members.get(playerId);
		return member != null && member.accessRequested();
	}

	public boolean isMember(UUID playerId) {
		return this.ownerId.equals(playerId) || this.acceptedBy(playerId);
	}

	public int permissionsFor(UUID playerId) {
		if (this.ownerId.equals(playerId)) {
			return SharePermission.ALL;
		}
		Member member = this.members.get(playerId);
		return member != null && member.accepted() ? member.permissions() : this.publicAccess.permissions();
	}

	public boolean can(UUID playerId, SharePermission permission) {
		return permission.isIn(this.permissionsFor(playerId));
	}

	public void setPublicAccess(ShareAccess access) {
		if (this.publicAccess == access) return;
		this.publicAccess = access;
		this.bumpRevision();
	}

	public void updateTransform(String dimension, int x, int y, int z, int rotation, int mirror) {
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.rotation = rotation;
		this.mirror = mirror;
		this.bumpRevision();
	}

	public void updateSchematic(String hash, int size) {
		this.schematicHash = hash;
		this.schematicSize = size;
		this.bumpRevision();
	}

	public void replaceSubstitutions(Map<String, String> values) {
		this.substitutions.clear();
		this.substitutions.putAll(values);
		this.bumpRevision();
	}

	public void invite(UUID playerId, String playerName, int permissions) {
		Member member = this.members.get(playerId);
		if (member == null) {
			this.members.put(playerId, new Member(playerId, playerName, permissions, false, false));
		} else {
			member.setPlayerName(playerName);
			member.setPermissions(permissions);
			member.setAccessRequested(false);
		}
		this.bumpRevision();
	}

	public void respondToInvite(UUID playerId, boolean accepted) {
		Member member = this.members.get(playerId);
		if (member == null || member.accessRequested()) {
			return;
		}
		if (accepted) {
			member.setAccepted(true);
		} else {
			this.members.remove(playerId);
		}
		this.bumpRevision();
	}

	public void requestAccess(UUID playerId, String playerName, int permissions) {
		Member member = this.members.get(playerId);
		if (member == null) {
			this.members.put(playerId, new Member(playerId, playerName, permissions, false, true));
		} else if (!member.accepted()) {
			member.setPlayerName(playerName);
			member.setPermissions(permissions);
			member.setAccessRequested(true);
		}
		this.bumpRevision();
	}

	public boolean respondToAccessRequest(UUID playerId, boolean accepted, int permissions) {
		Member member = this.members.get(playerId);
		if (member == null || !member.accessRequested()) return false;
		if (accepted) {
			member.setPermissions(permissions);
			member.setAccepted(true);
		} else {
			this.members.remove(playerId);
		}
		this.bumpRevision();
		return true;
	}

	public void setPermissions(UUID playerId, int permissions) {
		Member member = this.members.get(playerId);
		if (member != null) {
			member.setPermissions(permissions);
			this.bumpRevision();
		}
	}

	public boolean removeMember(UUID playerId) {
		if (this.members.remove(playerId) != null) {
			this.bumpRevision();
			return true;
		}
		return false;
	}

	/** Updates a cache snapshot and returns true when its contents actually changed. */
	public boolean putContainer(ContainerKey key, Map<String, Integer> items) {
		Map<String, Integer> immutable = Map.copyOf(items);
		Map<String, Integer> previous = this.containers.put(key, immutable);
		if (!immutable.equals(previous)) {
			this.bumpRevision();
			return true;
		}
		return false;
	}

	/** Updates volatile inventory contents without invalidating placement-edit revisions. */
	public boolean refreshContainer(ContainerKey key, Map<String, Integer> items) {
		Map<String, Integer> immutable = Map.copyOf(items);
		Map<String, Integer> previous = this.containers.put(key, immutable);
		return !immutable.equals(previous);
	}


	public boolean removeContainer(ContainerKey key) {
		if (this.containers.remove(key) != null) {
			this.bumpRevision();
			return true;
		}
		return false;
	}

	private void bumpRevision() {
		this.revision = Math.max(1L, this.revision + 1L);
	}

	public SharedProjectView viewFor(UUID playerId, boolean administrator) {
		int permissions = administrator ? SharePermission.ALL : this.permissionsFor(playerId);
		Member viewer = this.members.get(playerId);
		boolean pending = !administrator && viewer != null && !viewer.accepted() && !viewer.accessRequested();
		boolean requested = !administrator && viewer != null && viewer.accessRequested();
		boolean member = this.isMember(playerId);
		boolean mayView = administrator || SharePermission.VIEW.isIn(permissions);
		boolean maySeeRoster = administrator || this.ownerId.equals(playerId)
				|| SharePermission.INVITE.isIn(permissions)
				|| SharePermission.MANAGE_PERMISSIONS.isIn(permissions);

		List<SharedMemberView> memberViews = new ArrayList<>();
		memberViews.add(new SharedMemberView(this.ownerId, this.ownerName, SharePermission.ALL, true));
		if (maySeeRoster) {
			this.members.values().stream()
					.sorted(Comparator.comparing(Member::playerName, String.CASE_INSENSITIVE_ORDER))
					.map(entry -> new SharedMemberView(entry.playerId(), entry.playerName(),
							entry.permissions(), entry.accepted(), entry.accessRequested()))
					.forEach(memberViews::add);
		}

		List<SharedContainerView> containerViews = mayView ? this.containers.entrySet().stream()
				.map(entry -> new SharedContainerView(entry.getKey().dimension(), entry.getKey().x(),
						entry.getKey().y(), entry.getKey().z(), entry.getValue()))
				.toList() : List.of();

		return new SharedProjectView(this.id, this.revision, this.name, this.ownerId, this.ownerName,
				this.dimension, this.x, this.y, this.z, this.rotation, this.mirror,
				mayView ? this.schematicHash : "", mayView ? this.schematicSize : 0, permissions,
				this.publicAccess, member, pending, requested, memberViews,
				mayView ? this.substitutions : Map.of(), containerViews);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("schema", SCHEMA_VERSION);
		json.addProperty("id", this.id.toString());
		json.addProperty("ownerId", this.ownerId.toString());
		json.addProperty("ownerName", this.ownerName);
		json.addProperty("name", this.name);
		json.addProperty("revision", this.revision);
		json.addProperty("dimension", this.dimension);
		json.addProperty("x", this.x);
		json.addProperty("y", this.y);
		json.addProperty("z", this.z);
		json.addProperty("rotation", this.rotation);
		json.addProperty("mirror", this.mirror);
		json.addProperty("schematicHash", this.schematicHash);
		json.addProperty("schematicSize", this.schematicSize);
		json.addProperty("publicAccess", this.publicAccess.name());

		JsonArray membersJson = new JsonArray();
		for (Member member : this.members.values()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("playerId", member.playerId().toString());
			entry.addProperty("playerName", member.playerName());
			entry.addProperty("permissions", member.permissions());
			entry.addProperty("accepted", member.accepted());
			entry.addProperty("accessRequested", member.accessRequested());
			membersJson.add(entry);
		}
		json.add("members", membersJson);

		JsonObject substitutionsJson = new JsonObject();
		this.substitutions.forEach(substitutionsJson::addProperty);
		json.add("substitutions", substitutionsJson);

		JsonArray containersJson = new JsonArray();
		for (Map.Entry<ContainerKey, Map<String, Integer>> container : this.containers.entrySet()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("dimension", container.getKey().dimension());
			entry.addProperty("x", container.getKey().x());
			entry.addProperty("y", container.getKey().y());
			entry.addProperty("z", container.getKey().z());
			JsonObject items = new JsonObject();
			container.getValue().forEach(items::addProperty);
			entry.add("items", items);
			containersJson.add(entry);
		}
		json.add("containers", containersJson);
		return json;
	}

	@Nullable
	public static SharedProject fromJson(JsonObject json) {
		try {
			UUID id = UUID.fromString(json.get("id").getAsString());
			UUID ownerId = UUID.fromString(json.get("ownerId").getAsString());
			SharedProject project = new SharedProject(id, ownerId, json.get("ownerName").getAsString(),
					json.get("name").getAsString(), json.get("dimension").getAsString(),
					json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt(),
					json.get("rotation").getAsInt(), json.get("mirror").getAsInt(),
					json.get("schematicHash").getAsString(), json.get("schematicSize").getAsInt());
			project.revision = Math.max(1L, json.get("revision").getAsLong());
			if (json.has("publicAccess")) {
				project.publicAccess = ShareAccess.valueOf(json.get("publicAccess").getAsString());
			}

			if (json.has("members") && json.get("members").isJsonArray()) {
				for (JsonElement raw : json.getAsJsonArray("members")) {
					JsonObject member = raw.getAsJsonObject();
					UUID playerId = UUID.fromString(member.get("playerId").getAsString());
					project.members.put(playerId, new Member(playerId, member.get("playerName").getAsString(),
							member.get("permissions").getAsInt(), member.get("accepted").getAsBoolean(),
							member.has("accessRequested") && member.get("accessRequested").getAsBoolean()));
				}
			}

			if (json.has("substitutions") && json.get("substitutions").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("substitutions").entrySet()) {
					project.substitutions.put(entry.getKey(), entry.getValue().getAsString());
				}
			}

			if (json.has("containers") && json.get("containers").isJsonArray()) {
				for (JsonElement raw : json.getAsJsonArray("containers")) {
					JsonObject container = raw.getAsJsonObject();
					ContainerKey key = new ContainerKey(container.get("dimension").getAsString(),
							container.get("x").getAsInt(), container.get("y").getAsInt(), container.get("z").getAsInt());
					Map<String, Integer> items = new LinkedHashMap<>();
					for (Map.Entry<String, JsonElement> item : container.getAsJsonObject("items").entrySet()) {
						int count = item.getValue().getAsInt();
						if (count > 0) {
							items.put(item.getKey(), count);
						}
					}
					project.containers.put(key, Map.copyOf(items));
				}
			}

			return project;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
