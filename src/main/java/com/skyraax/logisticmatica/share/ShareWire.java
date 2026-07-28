package com.skyraax.logisticmatica.share;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded binary codec for the versioned payload body. */
public final class ShareWire {
	private static final int MAX_STRING_BYTES = 32_767;

	private ShareWire() {
	}

	@FunctionalInterface
	public interface Encoder {
		void encode(Writer writer) throws IOException;
	}

	public static byte[] encode(Encoder encoder) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				encoder.encode(new Writer(output));
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Could not encode sharing payload", e);
		}
	}

	public static Reader decode(byte[] body) {
		return new Reader(new DataInputStream(new ByteArrayInputStream(body)));
	}

	public static byte[] encodeProjects(List<SharedProjectView> projects) {
		return encode(writer -> {
			writer.writeCount(projects.size(), ShareProtocol.MAX_PROJECTS_PER_PLAYER);
			for (SharedProjectView project : projects) {
				writer.writeProject(project);
			}
		});
	}

	public static List<SharedProjectView> decodeProjects(byte[] body) throws IOException {
		Reader reader = decode(body);
		int count = reader.readCount(ShareProtocol.MAX_PROJECTS_PER_PLAYER);
		List<SharedProjectView> projects = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			projects.add(reader.readProject());
		}
		reader.requireFinished();
		return projects;
	}

	public static byte[] encodePlayers(List<SharedPlayerView> players) {
		return encode(writer -> {
			writer.writeCount(players.size(), ShareProtocol.MAX_ONLINE_PLAYERS);
			for (SharedPlayerView player : players) {
				writer.writeUuid(player.playerId());
				writer.writeString(player.playerName());
			}
		});
	}

	public static List<SharedPlayerView> decodePlayers(byte[] body) throws IOException {
		Reader reader = decode(body);
		int count = reader.readCount(ShareProtocol.MAX_ONLINE_PLAYERS);
		List<SharedPlayerView> players = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			players.add(new SharedPlayerView(reader.readUuid(), reader.readString()));
		}
		reader.requireFinished();
		return players;
	}
	public static byte[] encodeContainerSnapshot(SharedContainerSnapshot snapshot) {
		return encode(writer -> {
			writer.writeUuid(snapshot.projectId());
			writer.writeLong(snapshot.revision());
			writer.writeBoolean(snapshot.reset());
			writer.writeBoolean(snapshot.complete());
			writer.writeContainers(snapshot.containers(), ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET);
		});
	}

	public static SharedContainerSnapshot decodeContainerSnapshot(byte[] body) throws IOException {
		Reader reader = decode(body);
		SharedContainerSnapshot snapshot = new SharedContainerSnapshot(reader.readUuid(), reader.readLong(),
				reader.readBoolean(), reader.readBoolean(),
				reader.readContainers(ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET));
		reader.requireFinished();
		return snapshot;
	}

	public static byte[] encodeContainerDelta(SharedContainerDelta delta) {
		return encode(writer -> {
			writer.writeUuid(delta.projectId());
			writer.writeLong(delta.revision());
			writer.writeContainers(delta.upserts(), ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET);
			writer.writeCount(delta.removals().size(), ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET);
			for (SharedContainerKey key : delta.removals()) writer.writeContainerKey(key);
		});
	}

	public static SharedContainerDelta decodeContainerDelta(byte[] body) throws IOException {
		Reader reader = decode(body);
		UUID projectId = reader.readUuid();
		long revision = reader.readLong();
		List<SharedContainerView> upserts = reader.readContainers(ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET);
		int removedCount = reader.readCount(ShareProtocol.MAX_CONTAINER_CHANGES_PER_PACKET);
		List<SharedContainerKey> removals = new ArrayList<>(removedCount);
		for (int i = 0; i < removedCount; i++) removals.add(reader.readContainerKey());
		reader.requireFinished();
		return new SharedContainerDelta(projectId, revision, upserts, removals);
	}

	public static final class Writer {
		private final DataOutputStream output;

		private Writer(DataOutputStream output) {
			this.output = output;
		}

		public void writeBoolean(boolean value) throws IOException {
			this.output.writeBoolean(value);
		}

		public void writeInt(int value) throws IOException {
			this.output.writeInt(value);
		}

		public void writeLong(long value) throws IOException {
			this.output.writeLong(value);
		}

		public void writeUuid(UUID value) throws IOException {
			this.output.writeLong(value.getMostSignificantBits());
			this.output.writeLong(value.getLeastSignificantBits());
		}

		public void writeString(String value) throws IOException {
			byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			if (bytes.length > MAX_STRING_BYTES) {
				throw new IOException("String exceeds " + MAX_STRING_BYTES + " bytes");
			}
			this.output.writeInt(bytes.length);
			this.output.write(bytes);
		}

		public void writeBytes(byte[] value, int maximum) throws IOException {
			if (value.length > maximum) {
				throw new IOException("Byte array exceeds " + maximum + " bytes");
			}
			this.output.writeInt(value.length);
			this.output.write(value);
		}

		public void writeStringMap(Map<String, String> values, int maximum) throws IOException {
			this.writeCount(values.size(), maximum);
			for (Map.Entry<String, String> entry : values.entrySet()) {
				this.writeString(entry.getKey());
				this.writeString(entry.getValue());
			}
		}

		public void writeContainers(List<SharedContainerView> containers) throws IOException {
			this.writeContainers(containers, ShareProtocol.MAX_CONTAINERS_PER_PROJECT);
		}

		public void writeContainers(List<SharedContainerView> containers, int maximum) throws IOException {
			this.writeCount(containers.size(), maximum);
			for (SharedContainerView container : containers) {
				this.writeString(container.dimension());
				this.writeInt(container.x());
				this.writeInt(container.y());
				this.writeInt(container.z());
				this.writeCount(container.items().size(), ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER);
				for (Map.Entry<String, Integer> item : container.items().entrySet()) {
					this.writeString(item.getKey());
					this.writeInt(item.getValue());
				}
			}
		}

		public void writeContainerKey(SharedContainerKey key) throws IOException {
			this.writeString(key.dimension());
			this.writeInt(key.x());
			this.writeInt(key.y());
			this.writeInt(key.z());
		}

		public void writeCount(int count, int maximum) throws IOException {
			if (count < 0 || count > maximum) {
				throw new IOException("Collection count " + count + " exceeds " + maximum);
			}
			this.output.writeInt(count);
		}

		private void writeProject(SharedProjectView project) throws IOException {
			this.writeUuid(project.id());
			this.writeLong(project.revision());
			this.writeString(project.name());
			this.writeInt(project.status().ordinal());
			this.writeUuid(project.ownerId());
			this.writeString(project.ownerName());
			this.writeString(project.dimension());
			this.writeInt(project.x());
			this.writeInt(project.y());
			this.writeInt(project.z());
			this.writeInt(project.rotation());
			this.writeInt(project.mirror());
			this.writeString(project.schematicHash());
			this.writeInt(project.schematicSize());
			this.writeInt(project.myPermissions());
			this.writeInt(project.publicAccess().ordinal());
			this.writeBoolean(project.member());
			this.writeBoolean(project.pendingInvite());
			this.writeBoolean(project.accessRequested());

			this.writeCount(project.members().size(), ShareProtocol.MAX_MEMBERS_PER_PROJECT + 1);
			for (SharedMemberView member : project.members()) {
				this.writeUuid(member.playerId());
				this.writeString(member.playerName());
				this.writeInt(member.permissions());
				this.writeBoolean(member.accepted());
				this.writeBoolean(member.accessRequested());
			}

			this.writeStringMap(project.substitutions(), ShareProtocol.MAX_SUBSTITUTIONS_PER_PROJECT);
			this.writeLong(project.containerRevision());
			this.writeInt(project.containerCount());
		}
	}

	public static final class Reader {
		private final DataInputStream input;

		private Reader(DataInputStream input) {
			this.input = input;
		}

		public boolean readBoolean() throws IOException {
			return this.input.readBoolean();
		}

		public int readInt() throws IOException {
			return this.input.readInt();
		}

		public long readLong() throws IOException {
			return this.input.readLong();
		}

		public UUID readUuid() throws IOException {
			return new UUID(this.input.readLong(), this.input.readLong());
		}

		public String readString() throws IOException {
			int length = this.readCount(MAX_STRING_BYTES);
			byte[] bytes = this.input.readNBytes(length);
			if (bytes.length != length) {
				throw new EOFException("Truncated string");
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}

		public byte[] readBytes(int maximum) throws IOException {
			int length = this.readCount(maximum);
			byte[] bytes = this.input.readNBytes(length);
			if (bytes.length != length) {
				throw new EOFException("Truncated byte array");
			}
			return bytes;
		}

		public Map<String, String> readStringMap(int maximum) throws IOException {
			int count = this.readCount(maximum);
			Map<String, String> values = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				values.put(this.readString(), this.readString());
			}
			return values;
		}

		public List<SharedContainerView> readContainers() throws IOException {
			return this.readContainers(ShareProtocol.MAX_CONTAINERS_PER_PROJECT);
		}

		public List<SharedContainerView> readContainers(int maximum) throws IOException {
			int containerCount = this.readCount(maximum);
			List<SharedContainerView> containers = new ArrayList<>(containerCount);
			for (int i = 0; i < containerCount; i++) {
				String dimension = this.readString();
				int x = this.readInt();
				int y = this.readInt();
				int z = this.readInt();
				int itemCount = this.readCount(ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER);
				Map<String, Integer> items = new LinkedHashMap<>();
				for (int j = 0; j < itemCount; j++) {
					items.put(this.readString(), this.readInt());
				}
				containers.add(new SharedContainerView(dimension, x, y, z, items));
			}
			return containers;
		}

		public SharedContainerKey readContainerKey() throws IOException {
			return new SharedContainerKey(this.readString(), this.readInt(), this.readInt(), this.readInt());
		}

		public int readCount(int maximum) throws IOException {
			int count = this.input.readInt();
			if (count < 0 || count > maximum) {
				throw new IOException("Invalid collection/byte count " + count + " (max " + maximum + ")");
			}
			return count;
		}

		public void requireFinished() throws IOException {
			if (this.input.available() != 0) {
				throw new IOException("Trailing bytes in sharing payload");
			}
		}

		private SharedProjectView readProject() throws IOException {
			UUID id = this.readUuid();
			long revision = this.readLong();
			String name = this.readString();
			ProjectStatus status = ProjectStatus.byId(this.readInt());
			UUID ownerId = this.readUuid();
			String ownerName = this.readString();
			String dimension = this.readString();
			int x = this.readInt();
			int y = this.readInt();
			int z = this.readInt();
			int rotation = this.readInt();
			int mirror = this.readInt();
			String schematicHash = this.readString();
			int schematicSize = this.readInt();
			int myPermissions = this.readInt();
			ShareAccess publicAccess = ShareAccess.byId(this.readInt());
			boolean member = this.readBoolean();
			boolean pendingInvite = this.readBoolean();
			boolean accessRequested = this.readBoolean();

			int memberCount = this.readCount(ShareProtocol.MAX_MEMBERS_PER_PROJECT + 1);
			List<SharedMemberView> members = new ArrayList<>(memberCount);
			for (int i = 0; i < memberCount; i++) {
				members.add(new SharedMemberView(this.readUuid(), this.readString(), this.readInt(),
						this.readBoolean(), this.readBoolean()));
			}

			Map<String, String> substitutions = this.readStringMap(ShareProtocol.MAX_SUBSTITUTIONS_PER_PROJECT);
			long containerRevision = this.readLong();
			int containerCount = this.readCount(ShareProtocol.MAX_CONTAINERS_PER_PROJECT);

			return new SharedProjectView(id, revision, name, status, ownerId, ownerName, dimension, x, y, z,
					rotation, mirror, schematicHash, schematicSize, myPermissions, publicAccess,
					member, pendingInvite, accessRequested, members, substitutions,
					containerRevision, containerCount);
		}
	}
}
