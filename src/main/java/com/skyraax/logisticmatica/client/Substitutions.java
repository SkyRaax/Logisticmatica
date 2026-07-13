package com.skyraax.logisticmatica.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.util.BlockUtils;

/**
 * Pure helpers for material substitution: mapping one block onto another while preserving matching
 * block-state properties (so spruce stairs facing east become oak stairs facing east), enumerating a
 * schematic's original blocks, and (de)serialising blocks by registry id. No state of its own.
 */
public final class Substitutions {
	private Substitutions() {
	}

	/**
	 * The {@code from} state re-expressed as {@code toBlock}, copying every property the two blocks
	 * share (facing, axis, half, …). If the blocks have different property sets — e.g. a stair for a
	 * slab — we fall back to the target's default state rather than guess. A no-op maps to itself.
	 */
	public static BlockState withProperties(BlockState from, Block toBlock) {
		if (from.getBlock() == toBlock) {
			return from;
		}

		BlockState result = toBlock.defaultBlockState();

		if (haveSameProperties(from, result)) {
			for (Property<?> property : from.getProperties()) {
				result = copyProperty(result, from, property);
			}
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState copyProperty(BlockState target, BlockState source, Property<T> property) {
		return target.setValue(property, source.getValue(property));
	}

	/** True if both blocks define exactly the same set of properties (same impl Litematica uses). */
	private static boolean haveSameProperties(BlockState a, BlockState b) {
		return a.getBlock().getStateDefinition().getProperties()
				.equals(b.getBlock().getStateDefinition().getProperties());
	}

	/**
	 * Every distinct, item-having block that actually appears in the schematic, read straight from the
	 * subregion palettes so the result is the <em>original</em> blocks even when a substitution overlay
	 * is active (palette reads bypass the {@code get()} overlay). Sorted by display name.
	 */
	public static List<Block> originalBlocks(LitematicaSchematic schematic) {
		Set<Block> blocks = new LinkedHashSet<>();

		for (String region : schematic.getAreas().keySet()) {
			LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);

			if (container == null) {
				continue;
			}

			for (BlockState state : container.getPalette().fromMapping()) {
				Block block = state.getBlock();

				// Skip air and blocks with no item — you cannot hold or count those as a material.
				if (block != Blocks.AIR && block.asItem() != Items.AIR) {
					blocks.add(block);
				}
			}
		}

		List<Block> out = new ArrayList<>(blocks);
		out.sort(Comparator.comparing(block -> block.getName().getString()));
		return out;
	}

	/** Every block that can be a substitution target — i.e. has an item so it can be picked. Cached. */
	private static List<Block> substitutableBlocks;

	public static List<Block> substitutableBlocks() {
		if (substitutableBlocks == null) {
			List<Block> blocks = new ArrayList<>();

			for (Block block : BuiltInRegistries.BLOCK) {
				if (block != Blocks.AIR && block.asItem() != Items.AIR) {
					blocks.add(block);
				}
			}

			blocks.sort(Comparator.comparing(block -> block.getName().getString()));
			substitutableBlocks = blocks;
		}

		return substitutableBlocks;
	}

	public static String idOf(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	@Nullable
	public static Block blockOf(String id) {
		// Litematica's helper resolves the id through the block registry (and tolerates a trailing
		// "[properties]" suffix), which spares us referencing the resource-location type directly.
		return BlockUtils.getBlockFromString(id).orElse(null);
	}
}
