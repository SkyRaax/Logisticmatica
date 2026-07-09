package com.skyraax.logisticmatica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.InventoryUtils;

import fi.dy.masa.litematica.materials.MaterialListUtils;

/**
 * In single-player, refreshes tracked containers' content snapshots directly from the world (the
 * integrated server keeps client-side block entities populated), so items turn green without having
 * to open each container. On a server the client cannot read closed containers, so this does nothing
 * — the snapshot is filled when the player opens the container (see the container-screen mixin).
 */
public class ContainerContentTickHandler implements IClientTickHandler {
	private int counter;

	@Override
	public void onClientTick(Minecraft mc) {
		if (mc.level == null || mc.player == null || !mc.hasSingleplayerServer()) {
			return;
		}

		ContainerTracker tracker = ContainerTracker.getInstance();
		if (tracker.isEmpty()) {
			return;
		}

		// Refresh roughly once per second.
		if (++this.counter < 20) {
			return;
		}
		this.counter = 0;

		for (BlockPos pos : tracker.getMarked()) {
			Container inventory = InventoryUtils.getInventory(mc.level, pos);

			if (inventory != null) {
				tracker.setContents(pos, MaterialListUtils.getInventoryItemCounts(inventory));
			}
		}
	}
}
