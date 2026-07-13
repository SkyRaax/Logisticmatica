package com.skyraax.logisticmatica.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.ContainerData;
import com.skyraax.logisticmatica.client.gui.ContainerData.ItemCount;
import com.skyraax.logisticmatica.client.gui.ContainerData.Snapshot;

/**
 * Floats the actual item <em>models</em> of a marked container's most-plentiful items above it, as an
 * alternative to the text label. Driven from a mixin on {@code LevelRenderer.submitFeatures}, since
 * MC 26.2's deferred render pipeline only exposes a {@link SubmitNodeCollector} there — items are
 * emitted through {@link ItemStackRenderState#submit} rather than an immediate buffer.
 *
 * <p>Only nearby containers are drawn, capped by {@link #MAX_LABELS}; each shows up to
 * {@link #MAX_ICONS} icons billboarded toward the camera.
 */
public final class ContainerIconRenderer {
	private static final double LABEL_DISTANCE = 48.0;
	private static final double LABEL_DISTANCE_SQ = LABEL_DISTANCE * LABEL_DISTANCE;
	private static final int MAX_LABELS = 16;
	private static final int MAX_ICONS = 4;

	private static final float ICON_SCALE = 0.5f;
	private static final float ICON_SPACING = 0.34f;
	private static final double LABEL_HEIGHT = 1.4;

	private ContainerIconRenderer() {
	}

	/** Submits floating item icons for every nearby marked container. Called each frame from the mixin. */
	public static void submit(LevelRenderState levelRenderState, SubmitNodeCollector collector) {
		if (!Configs.Hud.SHOW_CONTAINER_LABELS.getBooleanValue() || !Configs.Hud.LABEL_ICONS.getBooleanValue()) {
			return;
		}

		ContainerTracker tracker = ContainerTracker.getInstance();
		if (tracker.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}

		CameraRenderState camera = levelRenderState.cameraRenderState;
		Vec3 camPos = camera.pos;
		Quaternionf orientation = camera.orientation;
		Vec3 eye = mc.player.position();
		ItemModelResolver resolver = mc.getItemModelResolver();
		PoseStack pose = new PoseStack();

		int shown = 0;
		for (BlockPos pos : tracker.getMarked()) {
			if (distanceSq(pos, eye) > LABEL_DISTANCE_SQ) {
				continue;
			}

			Snapshot snapshot = ContainerData.snapshotOf(pos);
			if (snapshot == null || snapshot.items().isEmpty()) {
				continue;
			}

			submitContainer(pose, collector, resolver, mc.level, camPos, orientation, pos, snapshot);

			if (++shown >= MAX_LABELS) {
				break;
			}
		}
	}

	private static void submitContainer(PoseStack pose, SubmitNodeCollector collector, ItemModelResolver resolver,
			Level level, Vec3 camPos, Quaternionf orientation, BlockPos pos, Snapshot snapshot) {
		List<ItemCount> items = snapshot.items();
		int count = Math.min(MAX_ICONS, items.size());
		int light = LightCoordsUtil.getLightCoords(level, pos.above());

		double worldX = pos.getX() + 0.5;
		double worldY = pos.getY() + LABEL_HEIGHT;
		double worldZ = pos.getZ() + 0.5;
		float startX = -ICON_SPACING * (count - 1) / 2.0f;

		for (int i = 0; i < count; i++) {
			ItemStack stack = items.get(i).stack();

			// A fresh state per icon: the collector keeps what it needs, but reusing one instance while
			// re-populating it risks clobbering an already-submitted icon.
			ItemStackRenderState state = new ItemStackRenderState();
			resolver.updateForTopItem(state, stack, ItemDisplayContext.GROUND, level, null, 0);

			pose.pushPose();
			pose.translate(worldX - camPos.x, worldY - camPos.y, worldZ - camPos.z);
			pose.mulPose(orientation); // billboard: face the camera
			pose.translate(startX + i * ICON_SPACING, 0.0f, 0.0f);
			pose.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);

			state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}

	private static double distanceSq(BlockPos pos, Vec3 eye) {
		double dx = pos.getX() + 0.5 - eye.x;
		double dy = pos.getY() + 0.5 - eye.y;
		double dz = pos.getZ() + 0.5 - eye.z;

		return dx * dx + dy * dy + dz * dz;
	}
}
