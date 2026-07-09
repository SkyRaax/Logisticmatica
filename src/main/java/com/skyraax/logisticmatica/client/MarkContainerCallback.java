package com.skyraax.logisticmatica.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.InventoryUtils;

/**
 * Hotkey callback: mark/unmark the container the player is looking at. Uses the vanilla crosshair
 * ({@link Minecraft#hitResult}) to find the real-world block, checks it is a container via
 * MaLiLib's {@link InventoryUtils#getInventory}, and toggles it in the {@link ContainerTracker}.
 */
public class MarkContainerCallback implements IHotkeyCallback {
	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || mc.player == null) {
			return false;
		}

		if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "logisticmatica.message.mark.no_target");
			return true;
		}

		BlockPos pos = blockHit.getBlockPos();
		Container container = InventoryUtils.getInventory(mc.level, pos);

		if (container == null) {
			InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "logisticmatica.message.mark.not_a_container");
			return true;
		}

		boolean nowMarked = ContainerTracker.getInstance().toggle(pos);
		ContainerTracker.getInstance().save();

		InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS,
				nowMarked ? "logisticmatica.message.mark.marked" : "logisticmatica.message.mark.unmarked");
		return true;
	}
}
