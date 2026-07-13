package com.skyraax.logisticmatica.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.position.Vec3d;
import fi.dy.masa.malilib.util.text.TextAlignment;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.ContainerData;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * Floats a small label above each nearby marked container listing its most-plentiful items — the
 * "chest tracker" glance: know what a chest holds without opening it. Labels are drawn through
 * MaLiLib's on-demand text-plate renderer, which we feed by scheduling a plate every frame.
 *
 * <p>The label text is rebuilt only every {@link #REBUILD_INTERVAL} frames (reading and sorting a
 * container's contents each frame would be wasteful), while the cheap scheduling happens every frame
 * so the plates never flicker. Only containers within {@link #LABEL_DISTANCE} blocks are labelled,
 * capped at {@link #MAX_LABELS}, so a big storage hall cannot fill the screen or the frame budget.
 */
public class ContainerLabelRenderer implements IRenderer {
	private static final double LABEL_DISTANCE = 48.0;
	private static final double LABEL_DISTANCE_SQ = LABEL_DISTANCE * LABEL_DISTANCE;
	private static final int MAX_LABELS = 24;
	private static final int MAX_LINES = 3;
	private static final int REBUILD_INTERVAL = 20;

	/** The text plate multiplier convention is {@code configScale * 0.01F} (see MaLiLib's examples). */
	private static final float SCALE = 2.0f * 0.01f;

	private record Plate(Vec3d pos, List<String> lines) {
	}

	private final List<Plate> plates = new ArrayList<>();
	private int frame;

	@Override
	public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float ticks, ProfilerFiller profiler) {
		if (!Configs.Hud.SHOW_CONTAINER_LABELS.getBooleanValue() || ContainerTracker.getInstance().isEmpty()) {
			this.plates.clear();
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			this.plates.clear();
			return;
		}

		if (this.frame++ % REBUILD_INTERVAL == 0) {
			this.rebuild(mc.player.position());
		}

		boolean seeThrough = Configs.Hud.LABEL_SEE_THROUGH.getBooleanValue();

		for (Plate plate : this.plates) {
			RenderUtils.scheduleTextPlate(plate.lines(), plate.pos(), SCALE, seeThrough, TextAlignment.CENTER);
		}
	}

	private void rebuild(Vec3 eye) {
		this.plates.clear();

		// collectMarked() is already sorted nearest-first, so honouring MAX_LABELS keeps the closest.
		for (Snapshot snapshot : ContainerData.collectMarked()) {
			BlockPos pos = snapshot.pos();

			if (distanceSq(pos, eye) > LABEL_DISTANCE_SQ) {
				continue;
			}

			this.plates.add(new Plate(new Vec3d(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5),
					buildLines(snapshot)));

			if (this.plates.size() >= MAX_LABELS) {
				break;
			}
		}
	}

	private static List<String> buildLines(Snapshot snapshot) {
		List<ItemCount> items = snapshot.items();
		List<String> lines = new ArrayList<>(MAX_LINES + 1);

		int shown = Math.min(MAX_LINES, items.size());
		for (int i = 0; i < shown; i++) {
			ItemCount item = items.get(i);
			lines.add(item.count() + "× " + item.stack().getHoverName().getString());
		}

		int remaining = items.size() - shown;
		if (remaining > 0) {
			lines.add("+" + remaining);
		}

		return lines;
	}

	private static double distanceSq(BlockPos pos, Vec3 eye) {
		double dx = pos.getX() + 0.5 - eye.x;
		double dy = pos.getY() + 0.5 - eye.y;
		double dz = pos.getZ() + 0.5 - eye.z;

		return dx * dx + dy * dy + dz * dz;
	}
}
