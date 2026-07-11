package com.skyraax.logisticmatica.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import fi.dy.masa.malilib.interfaces.IWorldLoadListener;

/**
 * Loads the tracked-container set when a world/dimension is joined and clears the in-memory set on
 * leave. The set is already persisted on every change (see {@link MarkContainerCallback}), so no
 * explicit save-on-leave is needed — which also sidesteps the world name being unavailable at that
 * moment.
 *
 * <p>Also re-applies stored material substitutions to any schematics already loaded on join, so a
 * saved swap is restored even for schematics that were not (re)loaded through the GUI.
 */
public class ContainerTrackerWorldLoad implements IWorldLoadListener {
	@Override
	public void onWorldLoadPost(@Nullable ClientLevel worldBefore, @Nullable ClientLevel worldAfter, Minecraft mc) {
		if (worldAfter != null) {
			ContainerTracker.getInstance().load();
			SubstitutionManager.getInstance().reapplyAll();
		} else {
			ContainerTracker.getInstance().clear();
		}
	}
}
