package com.skyraax.logisticmatica.server;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Side-safe bridge used by vanilla mixins to enqueue relevant container changes. */
public final class ServerContainerDirtyListener {
	private ServerContainerDirtyListener() {
	}

	public static void mark(@Nullable Level level, BlockPos pos) {
		if (level instanceof ServerLevel serverLevel) {
			ShareServer.markContainerDirty(serverLevel, pos);
		}
	}
}
