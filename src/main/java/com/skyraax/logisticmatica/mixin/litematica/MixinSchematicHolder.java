package com.skyraax.logisticmatica.mixin.litematica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;

import com.skyraax.logisticmatica.client.ContainerTracker;
import com.skyraax.logisticmatica.client.SubstitutionManager;

/**
 * Re-applies stored material substitutions whenever a schematic is added to the holder (the path the
 * "Load schematic" GUI takes), so a swap defined in an earlier session is restored as soon as the
 * schematic is loaded — without the {@code .litematic} on disk ever having been altered.
 *
 * <p>{@code remap = false}: the target is Litematica's own class/method.
 */
@Mixin(value = SchematicHolder.class, remap = false)
public class MixinSchematicHolder {
	@Inject(method = "addSchematic", at = @At("TAIL"), remap = false)
	private void logisticmatica$reapplySubstitutions(LitematicaSchematic schematic, boolean allowDuplicates, CallbackInfo ci) {
		ContainerTracker.getInstance().reconcileSchematic(schematic);
		SubstitutionManager.getInstance().reapply(schematic);
	}
}
