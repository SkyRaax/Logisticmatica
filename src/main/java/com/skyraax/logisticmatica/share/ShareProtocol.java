package com.skyraax.logisticmatica.share;

/** Wire-level constants and stable message identifiers for the sharing protocol. */
public final class ShareProtocol {
	public static final int VERSION = 5;
	public static final int MAX_SCHEMATIC_BYTES = 32 * 1024 * 1024;
	public static final int MAX_ENVELOPE_BYTES = MAX_SCHEMATIC_BYTES + 4 * 1024 * 1024;
	public static final int MAX_PROJECTS_PER_PLAYER = 256;
	public static final int MAX_MEMBERS_PER_PROJECT = 256;
	public static final int MAX_CONTAINERS_PER_PROJECT = 65_536;
	public static final int MAX_CONTAINERS_GLOBAL = 262_144;
	public static final int MAX_CONTAINER_CHANGES_PER_PACKET = 64;
	public static final int MAX_ITEM_TYPES_PER_CONTAINER = 256;
	public static final int MAX_SUBSTITUTIONS_PER_PROJECT = 16_384;
	public static final int MAX_ONLINE_PLAYERS = 1_024;

	public static final int FEATURE_SCHEMATIC_TRANSFER = 1 << 0;
	public static final int FEATURE_LIVE_PLACEMENT = 1 << 1;
	public static final int FEATURE_PERMISSIONS = 1 << 2;
	public static final int FEATURE_SUBSTITUTIONS = 1 << 3;
	public static final int FEATURE_SERVER_CONTAINERS = 1 << 4;
	public static final int FEATURE_PROJECT_DIRECTORY = 1 << 5;
	public static final int FEATURE_PUBLIC_ACCESS = 1 << 6;
	public static final int FEATURE_CONTAINER_DELTAS = 1 << 7;
	public static final int FEATURE_PROJECT_STATUS = 1 << 8;
	public static final int FEATURES = FEATURE_SCHEMATIC_TRANSFER | FEATURE_LIVE_PLACEMENT
			| FEATURE_PERMISSIONS | FEATURE_SUBSTITUTIONS | FEATURE_SERVER_CONTAINERS
			| FEATURE_PROJECT_DIRECTORY | FEATURE_PUBLIC_ACCESS | FEATURE_CONTAINER_DELTAS
			| FEATURE_PROJECT_STATUS;

	private ShareProtocol() {
	}

	public enum ServerboundAction {
		HELLO,
		LIST_PROJECTS,
		CREATE_PROJECT,
		DOWNLOAD_PROJECT,
		UPDATE_TRANSFORM,
		UPDATE_SCHEMATIC,
		UPDATE_SUBSTITUTIONS,
		INVITE,
		RESPOND_INVITE,
		SET_PERMISSIONS,
		REMOVE_MEMBER,
		DELETE_PROJECT,
		LEAVE_PROJECT,
		TOGGLE_CONTAINER,
		REFRESH_CONTAINER,
		LIST_PLAYERS,
		SET_PUBLIC_ACCESS,
		REQUEST_ACCESS,
		RESPOND_ACCESS,
		SUBSCRIBE_PROJECT,
		UNSUBSCRIBE_PROJECT,
		SET_PROJECT_STATUS;

		public static ServerboundAction byId(int id) {
			ServerboundAction[] values = values();
			if (id < 0 || id >= values.length) {
				throw new IllegalArgumentException("Unknown serverbound action " + id);
			}
			return values[id];
		}
	}

	public enum ClientboundEvent {
		HELLO,
		PROJECTS,
		PROJECT_DATA,
		PROJECT_CHANGED,
		PROJECT_REMOVED,
		NOTICE,
		ERROR,
		PLAYERS,
		CONTAINER_SNAPSHOT,
		CONTAINERS_CHANGED;

		public static ClientboundEvent byId(int id) {
			ClientboundEvent[] values = values();
			if (id < 0 || id >= values.length) {
				throw new IllegalArgumentException("Unknown clientbound event " + id);
			}
			return values[id];
		}
	}
}
