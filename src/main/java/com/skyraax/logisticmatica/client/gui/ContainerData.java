package com.skyraax.logisticmatica.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.util.data.ItemType;

import com.skyraax.logisticmatica.client.ContainerTracker;

/**
 * Turns the raw content cache in {@link ContainerTracker} into display-ready snapshots for the
 * container-overview screens: one {@link Snapshot} per marked container whose contents are known,
 * each carrying its items sorted by count. This is the read model the "view a chest from anywhere"
 * and "where is item X" screens are built on.
 */
public final class ContainerData {
	private ContainerData() {
	}

	/** One item stack and how many of it a container holds (summed across all its slots). */
	public record ItemCount(ItemStack stack, int count) {
	}

	/** A marked container's contents: its position, the schematic it belongs to, its items and the total. */
	public record Snapshot(BlockPos pos, @Nullable String schematicKey, List<ItemCount> items, int totalItems) {
	}

	/** All marked containers whose contents we know, nearest to the player first. */
	public static List<Snapshot> collectMarked() {
		ContainerTracker tracker = ContainerTracker.getInstance();
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player != null ? mc.player.position() : Vec3.ZERO;

		List<Snapshot> out = new ArrayList<>();

		for (BlockPos pos : tracker.allMarked()) {
			Object2IntOpenHashMap<ItemType> contents = tracker.getContents(pos);

			if (contents == null) contents = new Object2IntOpenHashMap<>();
			out.add(toSnapshot(pos, tracker.schematicKeyOf(pos), contents));
		}

		out.sort(Comparator.comparingDouble(snapshot -> distanceSq(snapshot.pos(), eye)));
		return out;
	}

	/** The snapshot of a single container, or null if we have never looked inside it. */
	@Nullable
	public static Snapshot snapshotOf(BlockPos pos) {
		ContainerTracker tracker = ContainerTracker.getInstance();
		Object2IntOpenHashMap<ItemType> contents = tracker.getContents(pos);
		return contents == null ? null : toSnapshot(pos, tracker.schematicKeyOf(pos), contents);
	}

	private static Snapshot toSnapshot(BlockPos pos, @Nullable String schematicKey,
			Object2IntOpenHashMap<ItemType> contents) {
		List<ItemCount> items = new ArrayList<>(contents.size());
		int total = 0;

		for (Object2IntMap.Entry<ItemType> entry : contents.object2IntEntrySet()) {
			items.add(new ItemCount(entry.getKey().getStack(), entry.getIntValue()));
			total += entry.getIntValue();
		}

		items.sort(Comparator.comparingInt(ItemCount::count).reversed());
		return new Snapshot(pos.immutable(), schematicKey, items, total);
	}

	private static double distanceSq(BlockPos pos, Vec3 eye) {
		double dx = pos.getX() + 0.5 - eye.x;
		double dy = pos.getY() + 0.5 - eye.y;
		double dz = pos.getZ() + 0.5 - eye.z;

		return dx * dx + dy * dy + dz * dz;
	}
}
