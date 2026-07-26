package com.skyraax.logisticmatica.server;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Vanilla-only container lookup used by the dedicated server. */
public final class ServerContainerAccess {
	private ServerContainerAccess() {
	}

	@Nullable
	public static ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return null;
		}
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	public static BlockPos canonical(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock
				&& state.hasProperty(BlockStateProperties.CHEST_TYPE)
				&& state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
			BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
			return pos.compareTo(partner) <= 0 ? pos.immutable() : partner.immutable();
		}
		return pos.immutable();
	}

	@Nullable
	public static Map<String, Integer> snapshot(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return null;
		}

		BlockState state = level.getBlockState(pos);
		Container container;
		if (state.getBlock() instanceof ChestBlock chest) {
			container = ChestBlock.getContainer(chest, state, level, pos, true);
		} else if (level.getBlockEntity(pos) instanceof Container blockContainer) {
			container = blockContainer;
		} else {
			return null;
		}

		if (container == null) {
			return null;
		}

		Map<String, Integer> items = new LinkedHashMap<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				items.merge(id, stack.getCount(), Integer::sum);
			}
		}
		return items;
	}

	public static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}
}
