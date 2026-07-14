package com.skyraax.logisticmatica.client;

/**
 * Records when any non-menu hotkey last fired, so the menu hotkey can suppress itself right after a
 * chord that shares its key. This is the reliable fix for the "T opens the menu, T+C is a chord"
 * case: MaLiLib fires the single-key menu bind's release even after a chord used the key, so the menu
 * callback checks here and skips if another hotkey was just used.
 */
public final class HotkeyActivity {
	/** How long after another hotkey fires the menu stays suppressed. Covers a chord tap-and-release. */
	private static final long WINDOW_MS = 500;

	private static long lastOtherHotkeyMs = Long.MIN_VALUE;

	private HotkeyActivity() {
	}

	/** Called by every non-menu hotkey when it fires. */
	public static void mark() {
		lastOtherHotkeyMs = System.currentTimeMillis();
	}

	/** True if another hotkey fired within the suppression window. */
	public static boolean recentlyActive() {
		return System.currentTimeMillis() - lastOtherHotkeyMs < WINDOW_MS;
	}
}
