package com.skyraax.logisticmatica.share;

/** Public access policy for a server-listed shared project. */
public enum ShareAccess {
	REQUEST_ONLY(0),
	PUBLIC_VIEWER(SharePermission.VIEWER),
	PUBLIC_SUPPLIER(SharePermission.BUILDER),
	PUBLIC_EDITOR(SharePermission.EDITOR);

	private final int permissions;

	ShareAccess(int permissions) {
		this.permissions = permissions;
	}

	public int permissions() {
		return this.permissions;
	}

	public static ShareAccess byId(int id) {
		ShareAccess[] values = values();
		if (id < 0 || id >= values.length) {
			throw new IllegalArgumentException("Unknown public access mode " + id);
		}
		return values[id];
	}
}
