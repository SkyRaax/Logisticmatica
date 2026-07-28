package com.skyraax.logisticmatica.client.share;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import fi.dy.masa.malilib.util.StringUtils;

/** Builds compact, localized descriptions of container-content changes. */
final class ItemChangeFormatter {
	private static final int MAX_ITEMS = 4;

	private ItemChangeFormatter() {}

	static boolean accumulate(Map<String, Integer> changes, Map<String, Integer> previous,
			Map<String, Integer> current) {
		boolean changed = false;
		Set<String> ids = new HashSet<>(previous.keySet());
		ids.addAll(current.keySet());
		for (String id : ids) {
			int difference = current.getOrDefault(id, 0) - previous.getOrDefault(id, 0);
			if (difference == 0) continue;
			changed = true;
			int total = changes.getOrDefault(id, 0) + difference;
			if (total == 0) changes.remove(id);
			else changes.put(id, total);
		}
		return changed;
	}

	static String format(Map<String, Integer> changes, boolean additions) {
		List<Map.Entry<String, Integer>> entries = changes.entrySet().stream()
				.filter(entry -> additions ? entry.getValue() > 0 : entry.getValue() < 0)
				.sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(entry ->
						Math.abs(entry.getValue())).reversed()
						.thenComparing(Map.Entry::getKey))
				.toList();
		if (entries.isEmpty()) return StringUtils.translate("logisticmatica.share.activity.none");

		List<String> parts = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_ITEMS, entries.size()); i++) {
			Map.Entry<String, Integer> entry = entries.get(i);
			parts.add(Math.abs(entry.getValue()) + "x " + itemName(entry.getKey()));
		}
		if (entries.size() > MAX_ITEMS) {
			parts.add(StringUtils.translate("logisticmatica.share.activity.and_more", entries.size() - MAX_ITEMS));
		}
		return String.join(", ", parts);
	}

	private static String itemName(String id) {
		Identifier location = Identifier.tryParse(id);
		if (location == null) return id;
		Item item = BuiltInRegistries.ITEM.getValue(location);
		return item == Items.AIR ? id : new ItemStack(item).getHoverName().getString();
	}
}
