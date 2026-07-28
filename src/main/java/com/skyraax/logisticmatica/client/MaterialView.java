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
			int target = target(entry);
			if (Configs.Hud.ONLY_MISSING.getBooleanValue() && entry.getCountMissing() <= 0) continue;
			if (Configs.Hud.HIDE_COMPLETE.getBooleanValue() && entry.getCountAvailable() >= target) continue;
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
		return mode == MaterialAmountMode.REMAINING ? Math.max(0, entry.getCountMissing())
				: Math.max(0, entry.getCountTotal());
	}

	public static int shortage(MaterialListEntry entry) {
		return Math.max(0, target(entry) - entry.getCountAvailable());
	}
}
