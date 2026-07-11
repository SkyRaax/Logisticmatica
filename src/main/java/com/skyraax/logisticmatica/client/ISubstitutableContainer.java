package com.skyraax.logisticmatica.client;

import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;

/**
 * Duck interface mixed into Litematica's {@code LitematicaBlockStateContainer} so a substitution map
 * can be attached to it. When set, the container's block-state reads return the substitute block
 * (keeping the original's properties) instead of the stored one — a non-destructive overlay that
 * leaves the packed palette (and therefore the {@code .litematic} file) untouched.
 *
 * <p>Lives outside the mixin package on purpose: a helper referenced by a mixin must not be loaded
 * from within a mixin-owned package, or it triggers an {@code IllegalClassLoadError}.
 */
public interface ISubstitutableContainer {
	/** Sets (or clears, when null/empty) the block-to-block substitution map for this container. */
	void logisticmatica$setSubstitutions(@Nullable Map<Block, Block> substitutions);

	/** The active substitution map, or null if none is set. */
	@Nullable
	Map<Block, Block> logisticmatica$getSubstitutions();
}
