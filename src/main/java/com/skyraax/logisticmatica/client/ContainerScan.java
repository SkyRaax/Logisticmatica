package com.skyraax.logisticmatica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import fi.dy.masa.litematica.materials.MaterialListUtils;

/**
 * Snapshots an open container's contents into the tracker (the server path, where a container can
 * only be read while its screen is open).
 *
 * <p>Isolated in its own class so the vanilla container-screen mixin can stay free of any Litematica
 * reference: the mixin only touches this class after it has confirmed Litematica is present, so a
 * client without Litematica never loads {@code MaterialListUtils}.
 */
public final class ContainerScan {
	private ContainerScan() {
	}

	public static void snapshot(BlockPos canonical, AbstractContainerMenu menu) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		// The first slot that is not part of the player's own inventory belongs to the container;
		// its backing Container is populated on the client while the screen is open.
		for (Slot slot : menu.slots) {
			if (slot.container != mc.player.getInventory()) {
				ContainerTracker.getInstance().setContents(canonical,
						MaterialListUtils.getInventoryItemCounts(slot.container));
				return;
			}
		}
	}
}
