package com.skyraax.logisticmatica.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;

import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.config.MaterialAmountMode;
import com.skyraax.logisticmatica.client.config.MaterialSortMode;

/** One persistent material view model consumed by both the menu list and HUD. */
public final class MaterialView {
	private MaterialView() {}

	public static List<MaterialListEntry> entries(MaterialListBase materialList) {
		List<MaterialListEntry> entries = new ArrayList<>();
		for (MaterialListEntry entry : materialList.getMaterialsAll()) {
			if (shouldHide(entry.getCountTotal(), entry.getCountMissing(), entry.getCountAvailable(),
					(MaterialAmountMode) Configs.Hud.MATERIAL_AMOUNT.getOptionListValue(),
					Configs.Hud.ONLY_MISSING.getBooleanValue(),
					Configs.Hud.HIDE_COMPLETE.getBooleanValue())) continue;
			entries.add(entry);
		}

		MaterialSortMode mode = (MaterialSortMode) Configs.Hud.MATERIAL_SORT.getOptionListValue();
		Comparator<MaterialListEntry> comparator = switch (mode) {
			case SHORTAGE -> Comparator.comparingInt(MaterialView::shortage);
			case REQUIRED -> Comparator.comparingInt(MaterialView::target);
			case AVAILABLE -> Comparator.comparingInt(MaterialListEntry::getCountAvailable);
			case NAME -> Comparator.comparing(entry -> entry.getStack().getHoverName().getString(),
					String.CASE_INSENSITIVE_ORDER);
		};
		if (Configs.Hud.MATERIAL_SORT_DESCENDING.getBooleanValue()) comparator = comparator.reversed();
		if (mode != MaterialSortMode.NAME) {
			comparator = comparator.thenComparing(entry -> entry.getStack().getHoverName().getString(),
					String.CASE_INSENSITIVE_ORDER);
		}
		entries.sort(comparator);
		return entries;
	}

	public static int target(MaterialListEntry entry) {
		MaterialAmountMode mode = (MaterialAmountMode) Configs.Hud.MATERIAL_AMOUNT.getOptionListValue();
		return target(entry.getCountTotal(), entry.getCountMissing(), mode);
	}

	public static int target(int total, int missing, MaterialAmountMode mode) {
		return mode == MaterialAmountMode.REMAINING ? Math.max(0, missing) : Math.max(0, total);
	}

	/**
	 * Keeps the two filters independent: a zero remaining target is controlled by "already built",
	 * not treated as automatically supplied by the separate inventory/container filter.
	 */
	public static boolean shouldHide(int total, int missing, int available, MaterialAmountMode mode,
			boolean hideBuilt, boolean hideSupplied) {
		if (hideBuilt && missing <= 0) {
			return true;
		}

		int target = target(total, missing, mode);
		return hideSupplied && target > 0 && available >= target;
	}

	public static int shortage(MaterialListEntry entry) {
		return Math.max(0, target(entry) - entry.getCountAvailable());
	}
}
