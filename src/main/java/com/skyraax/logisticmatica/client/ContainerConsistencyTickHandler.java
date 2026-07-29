package com.skyraax.logisticmatica.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.InventoryUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/**
 * Repairs local container marks whose loaded world block is repeatedly confirmed to no longer be a
 * container. Server projections are excluded because their authoritative missing-state grace is
 * handled by the server.
 */
public class ContainerConsistencyTickHandler implements IClientTickHandler {
	private static final int CHECK_INTERVAL_TICKS = 100;
	private static final int MAX_CHECKS_PER_PASS = 64;
	private static final int MISSING_CONFIRMATIONS = 3;

	private final Map<BlockPos, Integer> missingConfirmations = new HashMap<>();
	private int counter;
	private int cursor;

	@Override
	public void onClientTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			this.missingConfirmations.clear();
			this.cursor = 0;
			return;
		}
		if (++this.counter < CHECK_INTERVAL_TICKS) return;
		this.counter = 0;

		ContainerTracker tracker = ContainerTracker.getInstance();
		List<BlockPos> positions = List.copyOf(tracker.allMarked());
		if (positions.isEmpty()) {
			this.missingConfirmations.clear();
			this.cursor = 0;
			return;
		}

		int visited = 0;
		int checked = 0;
		int removed = 0;
		int index = Math.floorMod(this.cursor, positions.size());
		while (visited < positions.size() && checked < MAX_CHECKS_PER_PASS) {
			BlockPos pos = positions.get(index);
			index = (index + 1) % positions.size();
			visited++;

			if (tracker.isServerBinding(pos) || !mc.level.hasChunkAt(pos)) {
				this.missingConfirmations.remove(pos);
				continue;
			}
			checked++;
			Container inventory = InventoryUtils.getInventory(mc.level, pos);
			if (inventory != null) {
				this.missingConfirmations.remove(pos);
				continue;
			}

			int confirmations = this.missingConfirmations.merge(pos, 1, Integer::sum);
			if (confirmations >= MISSING_CONFIRMATIONS && tracker.forceRemove(pos)) {
				this.missingConfirmations.remove(pos);
				removed++;
				Logisticmatica.LOGGER.info("[{}] Removed stale local container mark at {}.",
						Logisticmatica.MOD_NAME, pos);
			}
		}
		this.cursor = index;
		this.missingConfirmations.keySet().removeIf(pos ->
				!tracker.isMarked(pos) || tracker.isServerBinding(pos));

		if (removed > 0) {
			tracker.save();
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING,
					"logisticmatica.message.container.auto_repaired", removed);
		}
	}
}
