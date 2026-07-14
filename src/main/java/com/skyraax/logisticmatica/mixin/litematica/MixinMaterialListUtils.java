package com.skyraax.logisticmatica.mixin.litematica;

import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;

import fi.dy.masa.malilib.util.data.ItemType;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.ContainerTracker;
import com.skyraax.logisticmatica.client.FocusState;
import com.skyraax.logisticmatica.client.SchematicKey;

/**
 * Folds the contents of the focused schematic's tracked containers into the material list's
 * "available" counts. Injected at the TAIL of {@link MaterialListUtils#updateAvailableCounts}, which
 * Litematica already calls from both the HUD and the list GUI to refresh availability from the player
 * inventory — so one hook updates both surfaces. Only the focused schematic's containers count, since
 * that is the schematic whose material list is shown. {@code remap = false}: the target is
 * Litematica's own method.
 */
@Mixin(MaterialListUtils.class)
public abstract class MixinMaterialListUtils {
	@Inject(method = "updateAvailableCounts(Ljava/util/List;Lnet/minecraft/world/entity/player/Player;)V",
			at = @At("TAIL"), remap = false)
	private static void logisticmatica$addTrackedCounts(List<MaterialListEntry> list, Player player, CallbackInfo ci) {
		LitematicaSchematic focused = FocusState.getSchematic();
		if (focused == null) {
			return;
		}

		Object2IntOpenHashMap<ItemType> tracked = ContainerTracker.getInstance().totalContentsFor(SchematicKey.of(focused));

		if (tracked.isEmpty()) {
			return;
		}

		for (MaterialListEntry entry : list) {
			int extra = tracked.getInt(new ItemType(entry.getStack(), true, false));

			if (extra > 0) {
				entry.setCountAvailable(entry.getCountAvailable() + extra);
			}
		}
	}
}
