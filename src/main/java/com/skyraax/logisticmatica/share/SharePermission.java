package com.skyraax.logisticmatica.share;

import java.util.EnumSet;

/** Granular capabilities granted to a member of one shared project. */
public enum SharePermission {
	VIEW(1 << 0),
	MOVE(1 << 1),
	UPDATE_SCHEMATIC(1 << 2),
	SUBSTITUTE(1 << 3),
	MANAGE_CONTAINERS(1 << 4),
	INVITE(1 << 5),
	MANAGE_PERMISSIONS(1 << 6),
	DELETE(1 << 7);

	public static final int ALL = (1 << values().length) - 1;
	public static final int VIEWER = VIEW.mask;
	public static final int BUILDER = VIEW.mask | MANAGE_CONTAINERS.mask;
	public static final int EDITOR = BUILDER | MOVE.mask | UPDATE_SCHEMATIC.mask | SUBSTITUTE.mask;
	public static final int MANAGER = EDITOR | INVITE.mask | MANAGE_PERMISSIONS.mask;

	private final int mask;

	SharePermission(int mask) {
		this.mask = mask;
	}

	public int mask() {
		return this.mask;
	}

	public boolean isIn(int permissions) {
		return (permissions & this.mask) != 0;
	}

	public static int sanitize(int permissions) {
		return permissions & ALL;
	}

	public static EnumSet<SharePermission> unpack(int permissions) {
		EnumSet<SharePermission> result = EnumSet.noneOf(SharePermission.class);
		for (SharePermission permission : values()) {
			if (permission.isIn(permissions)) {
				result.add(permission);
			}
		}
		return result;
	}
}
