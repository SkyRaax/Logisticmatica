package com.skyraax.logisticmatica.client;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.util.data.Color4f;

/** Temporary, non-persistent world beacon started from the container overview. */
public final class ContainerLocator {
	private static final long DURATION_MILLIS = 15_000L;
	@Nullable private static Target target;

	private ContainerLocator() {}

	public static void highlight(String schematicKey, BlockPos pos) {
		target = new Target(schematicKey, pos.immutable(), System.currentTimeMillis() + DURATION_MILLIS);
	}

	@Nullable
	public static Target target() {
		Target current = target;
		if (current != null && current.expiresAtMillis() <= System.currentTimeMillis()) {
			target = null;
			return null;
		}
		return current;
	}

	public static boolean matches(String schematicKey, BlockPos pos) {
		Target current = target();
		return current != null && current.schematicKey().equals(schematicKey)
				&& current.pos().equals(pos);
	}

	public static Color4f color() {
		double phase = (System.currentTimeMillis() % 700L) / 700.0 * Math.PI * 2.0;
		float pulse = (float) ((Math.sin(phase) + 1.0) * 0.5);
		return new Color4f(1.0f, 0.45f + pulse * 0.5f, 0.05f, 0.65f + pulse * 0.35f);
	}

	public record Target(String schematicKey, BlockPos pos, long expiresAtMillis) {}
}
