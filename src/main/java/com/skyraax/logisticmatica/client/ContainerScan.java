package com.skyraax.logisticmatica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import fi.dy.masa.malilib.util.InventoryUtils;
import fi.dy.masa.litematica.materials.MaterialListUtils;

/**
 * Caches an open container's contents (the server path, where a container can only be read while its
 * screen is open). Every opened container is cached, not just the marked ones, so marking takes
 * effect immediately.
 *
 * <p>Isolated in its own class so the vanilla container-screen mixin stays free of any Litematica
 * reference: the mixin only touches this class after confirming Litematica is present, so a client
 * without Litematica never loads {@code MaterialListUtils}.
 */
public final class ContainerScan {
	private ContainerScan() {
	}

	/**
	 * Snapshots the open menu's container into the cache, keyed by the block position — but only if
	 * that block really is a container and the open menu really is its inventory (this guards against
	 * e.g. opening your own inventory while looking at a chest).
	 */
	public static void snapshotIfContainer(BlockPos canonical, AbstractContainerMenu menu) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || mc.player == null) {
			return;
		}

		Container blockInventory = InventoryUtils.getInventory(mc.level, canonical);

		if (blockInventory == null) {
			return;
		}

		// The first slot that is not part of the player's own inventory belongs to the opened
		// container; its backing Container is populated on the client while the screen is open.
		for (Slot slot : menu.slots) {
			Container menuInventory = slot.container;

			if (menuInventory != mc.player.getInventory()) {
				if (menuInventory.getContainerSize() == blockInventory.getContainerSize()) {
					ContainerTracker tracker = ContainerTracker.getInstance();
					tracker.setContents(canonical, MaterialListUtils.getInventoryItemCounts(menuInventory));

					// Persist a marked container's freshly-read contents so they survive a rejoin (matters
					// on servers, where a closed container cannot be read again).
					if (tracker.isMarked(canonical)) {
						tracker.save();
					}
				}

				return;
			}
		}
	}
}
