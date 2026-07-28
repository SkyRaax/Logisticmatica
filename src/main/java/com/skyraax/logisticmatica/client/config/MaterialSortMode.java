package com.skyraax.logisticmatica.client.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/** Sort key shared by Logisticmatica's material screen and in-game HUD. */
public enum MaterialSortMode implements IConfigOptionListEntry {
	SHORTAGE("shortage"),
	REQUIRED("required"),
	AVAILABLE("available"),
	NAME("name");

	private final String value;

	MaterialSortMode(String value) {
		this.value = value;
	}

	@Override public String getStringValue() { return this.value; }
	@Override public String getDisplayName() {
		return StringUtils.translate("logisticmatica.config.material_sort." + this.value);
	}
	@Override public IConfigOptionListEntry cycle(boolean forward) {
		MaterialSortMode[] values = values();
		int offset = forward ? 1 : values.length - 1;
		return values[(this.ordinal() + offset) % values.length];
	}
	@Override public IConfigOptionListEntry fromString(String value) {
		for (MaterialSortMode mode : values()) if (mode.value.equalsIgnoreCase(value)) return mode;
		return SHORTAGE;
	}
}
