package com.skyraax.logisticmatica.share;

/** Wire-level constants and stable message identifiers for the sharing protocol. */
public final class ShareProtocol {
	public static final int VERSION = 1;
	public static final int MAX_SCHEMATIC_BYTES = 32 * 1024 * 1024;
	public static final int MAX_ENVELOPE_BYTES = MAX_SCHEMATIC_BYTES + 1024 * 1024;
	public static final int MAX_PROJECTS_PER_PLAYER = 256;
	public static final int MAX_MEMBERS_PER_PROJECT = 256;
	public static final int MAX_CONTAINERS_PER_PROJECT = 2048;
	public static final int MAX_CONTAINERS_GLOBAL = 2048;
	public static final int MAX_ITEM_TYPES_PER_CONTAINER = 256;
	public static final int MAX_SUBSTITUTIONS_PER_PROJECT = 16_384;

	public static final int FEATURE_SCHEMATIC_TRANSFER = 1 << 0;
	public static final int FEATURE_LIVE_PLACEMENT = 1 << 1;
	public static final int FEATURE_PERMISSIONS = 1 << 2;
	public static final int FEATURE_SUBSTITUTIONS = 1 << 3;
	public static final int FEATURE_SERVER_CONTAINERS = 1 << 4;
	public static final int FEATURES = FEATURE_SCHEMATIC_TRANSFER | FEATURE_LIVE_PLACEMENT
			| FEATURE_PERMISSIONS | FEATURE_SUBSTITUTIONS | FEATURE_SERVER_CONTAINERS;

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
		REFRESH_CONTAINER;

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
		ERROR;

		public static ClientboundEvent byId(int id) {
			ClientboundEvent[] values = values();
			if (id < 0 || id >= values.length) {
				throw new IllegalArgumentException("Unknown clientbound event " + id);
			}
			return values[id];
		}
	}
}
