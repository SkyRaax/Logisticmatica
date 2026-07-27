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

	/** Translation key for the short public-mode name shown after the "Public:" prefix. */
	public String publicRoleKey() {
		return switch (this) {
			case REQUEST_ONLY -> "logisticmatica.gui.share.request_access";
			case PUBLIC_VIEWER -> "logisticmatica.gui.share.public.viewer";
			case PUBLIC_SUPPLIER -> "logisticmatica.gui.share.public.supplier";
			case PUBLIC_EDITOR -> "logisticmatica.gui.share.public.editor";
		};
	}

	public static ShareAccess byId(int id) {
		ShareAccess[] values = values();
		if (id < 0 || id >= values.length) {
			throw new IllegalArgumentException("Unknown public access mode " + id);
		}
		return values[id];
	}
}
