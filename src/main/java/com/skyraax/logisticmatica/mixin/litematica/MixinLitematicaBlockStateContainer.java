package com.skyraax.logisticmatica.mixin.litematica;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;

import com.skyraax.logisticmatica.client.ISubstitutableContainer;
import com.skyraax.logisticmatica.client.Substitutions;

/**
 * Overlays material substitutions onto a schematic subregion's block reads. When a substitution map
 * is attached (via {@link ISubstitutableContainer}), {@code get(x,y,z)} returns the substitute block
 * — carrying the original's properties — instead of the stored one. The packed palette is left
 * untouched, so this never changes what gets saved; clearing the map instantly restores the original.
 *
 * <p>{@code remap = false} because the target is Litematica's own class/method, not a Minecraft one.
 * Uses {@code @ModifyReturnValue} rather than a cancellable inject so no callback object is allocated
 * per read — {@code get} is called for every cell during a rebuild. The per-state results are also
 * memoised so a rebuild does not recompute the property copy for every cell.
 */
@Mixin(value = LitematicaBlockStateContainer.class, remap = false)
public class MixinLitematicaBlockStateContainer implements ISubstitutableContainer {
	// volatile + a concurrent cache: a rebuild may read one container from several worker threads.
	@Unique @Nullable private volatile Map<Block, Block> logisticmatica$substitutions;
	@Unique @Nullable private volatile Map<BlockState, BlockState> logisticmatica$cache;

	@Override
	public void logisticmatica$setSubstitutions(@Nullable Map<Block, Block> substitutions) {
		boolean active = substitutions != null && !substitutions.isEmpty();
		this.logisticmatica$cache = active ? new ConcurrentHashMap<>() : null;
		this.logisticmatica$substitutions = active ? substitutions : null;
	}

	@Override
	@Nullable
	public Map<Block, Block> logisticmatica$getSubstitutions() {
		return this.logisticmatica$substitutions;
	}

	@ModifyReturnValue(method = "get(III)Lnet/minecraft/world/level/block/state/BlockState;",
			at = @At("RETURN"), remap = false)
	private BlockState logisticmatica$applySubstitution(BlockState original) {
		Map<Block, Block> substitutions = this.logisticmatica$substitutions;
		if (substitutions == null) {
			return original;
		}

		Block target = substitutions.get(original.getBlock());
		if (target == null) {
			return original;
		}

		// Read the cache into a local so a concurrent clear cannot NPE us mid-call.
		Map<BlockState, BlockState> cache = this.logisticmatica$cache;
		if (cache == null) {
			return Substitutions.withProperties(original, target);
		}

		return cache.computeIfAbsent(original, state -> Substitutions.withProperties(state, target));
	}
}
