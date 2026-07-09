package com.skyraax.logisticmatica.client;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Helpers for treating a double chest as one tracked unit.
 *
 * <ul>
 *   <li>{@link #canonical} picks one stable representative block for a container, so marking either
 *       half toggles the same entry (and later its contents are read only once).</li>
 *   <li>{@link #blocks} lists every block the container occupies (both halves of a double chest),
 *       so all of them get highlighted.</li>
 * </ul>
 */
public final class ContainerBlocks {
	private ContainerBlocks() {
	}

	/** The representative block of the container at {@code pos} (the lower half of a double chest). */
	public static BlockPos canonical(Level level, BlockPos pos) {
		BlockPos partner = doubleChestPartner(level, pos);

		if (partner != null) {
			return pos.compareTo(partner) <= 0 ? pos.immutable() : partner;
		}

		return pos.immutable();
	}

	/** All blocks the container occupies: two for a double chest, one otherwise. */
	public static List<BlockPos> blocks(Level level, BlockPos pos) {
		List<BlockPos> list = new ArrayList<>(2);
		list.add(pos.immutable());

		BlockPos partner = doubleChestPartner(level, pos);
		if (partner != null) {
			list.add(partner);
		}

		return list;
	}

	@Nullable
	private static BlockPos doubleChestPartner(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof ChestBlock
				&& state.hasProperty(BlockStateProperties.CHEST_TYPE)
				&& state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
			return pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
		}

		return null;
	}
}
