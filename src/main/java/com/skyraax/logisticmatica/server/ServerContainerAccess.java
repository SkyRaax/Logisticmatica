package com.skyraax.logisticmatica.server;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Vanilla-only container lookup used by the dedicated server. */
public final class ServerContainerAccess {
	private static final int MAX_NESTED_DEPTH = 8;
	private static final int MAX_EXPANDED_STACKS = 4_096;

	enum ReadStatus {
		AVAILABLE,
		UNLOADED,
		NOT_CONTAINER,
		TOO_COMPLEX
	}

	record ReadResult(ReadStatus status, Map<String, Integer> items) {
		ReadResult {
			items = Map.copyOf(items);
		}

		boolean shouldRemoveMark() {
			return this.status == ReadStatus.NOT_CONTAINER;
		}
	}

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
		ReadResult result = read(level, pos);
		return result.status() == ReadStatus.AVAILABLE ? result.items() : null;
	}

	static ReadResult read(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return new ReadResult(ReadStatus.UNLOADED, Map.of());
		}

		BlockState state = level.getBlockState(pos);
		Container container;
		if (state.getBlock() instanceof ChestBlock chest) {
			container = ChestBlock.getContainer(chest, state, level, pos, true);
		} else if (level.getBlockEntity(pos) instanceof Container blockContainer) {
			container = blockContainer;
		} else {
			return new ReadResult(ReadStatus.NOT_CONTAINER, Map.of());
		}

		if (container == null) {
			return new ReadResult(ReadStatus.NOT_CONTAINER, Map.of());
		}

		return snapshot(container);
	}

	static ReadResult snapshot(Container container) {
		Map<String, Integer> items = new LinkedHashMap<>();
		ItemCounter counter = new ItemCounter(items);
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			counter.addTopLevel(container.getItem(slot));
			if (counter.tooComplex()) return new ReadResult(ReadStatus.TOO_COMPLEX, Map.of());
		}
		return new ReadResult(ReadStatus.AVAILABLE, items);
	}

	/** Mirrors Litematica's inventory counting without loading any client-only classes on the server. */
	private static final class ItemCounter {
		private final Map<String, Integer> items;
		private int visitedStacks;
		private boolean tooComplex;

		private ItemCounter(Map<String, Integer> items) {
			this.items = items;
		}

		private void addTopLevel(ItemStack stack) {
			if (!this.visit(stack)) return;

			ItemContainerContents stored = shulkerContents(stack);
			if (stored != null && !stored.equals(ItemContainerContents.EMPTY)) {
				this.addStoredItems(stored.nonEmptyItemCopyStream().iterator(), 1);
				return;
			}

			BundleContents bundle = bundleContents(stack);
			if (bundle != null && !bundle.isEmpty()) {
				this.addBundleItems(bundle.itemCopyStream().iterator(), 1);
				return;
			}

			this.addItem(stack);
		}

		private void addStoredItems(Iterator<ItemStack> stacks, int depth) {
			if (!this.enter(depth)) return;
			while (stacks.hasNext() && !this.tooComplex) {
				ItemStack stack = stacks.next();
				if (!this.visit(stack)) continue;
				BundleContents bundle = bundleContents(stack);
				if (bundle != null && !bundle.isEmpty()) {
					this.addBundleItems(bundle.itemCopyStream().iterator(), depth + 1);
				}
				// Litematica counts a bundle stored inside another item both as a bundle and by contents.
				this.addItem(stack);
			}
		}

		private void addBundleItems(Iterator<ItemStack> stacks, int depth) {
			if (!this.enter(depth)) return;
			while (stacks.hasNext() && !this.tooComplex) {
				ItemStack stack = stacks.next();
				if (!this.visit(stack)) continue;
				BundleContents nested = bundleContents(stack);
				if (nested != null && !nested.isEmpty()) {
					this.addBundleItems(nested.itemCopyStream().iterator(), depth + 1);
				}
				// This intentionally matches Litematica's nested-bundle accounting.
				this.addItem(stack);
			}
		}

		private boolean visit(ItemStack stack) {
			if (stack.isEmpty()) return false;
			if (++this.visitedStacks > MAX_EXPANDED_STACKS) {
				this.tooComplex = true;
				return false;
			}
			return true;
		}

		private boolean enter(int depth) {
			if (depth <= MAX_NESTED_DEPTH) return true;
			this.tooComplex = true;
			return false;
		}

		private void addItem(ItemStack stack) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			this.items.merge(id, stack.getCount(), Integer::sum);
		}

		private boolean tooComplex() {
			return this.tooComplex;
		}
	}

	@Nullable
	private static ItemContainerContents shulkerContents(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ShulkerBoxBlock) {
			return stack.get(DataComponents.CONTAINER);
		}
		return null;
	}

	@Nullable
	private static BundleContents bundleContents(ItemStack stack) {
		return stack.getItem() instanceof BundleItem ? stack.get(DataComponents.BUNDLE_CONTENTS) : null;
	}

	public static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}
}
