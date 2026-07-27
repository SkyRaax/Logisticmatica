package com.skyraax.logisticmatica.client.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/** Layout of the projected item list above a marked container. */
public enum ContainerLabelLayout implements IConfigOptionListEntry {
	VERTICAL("vertical"),
	COLUMNS("columns");

	private final String value;

	ContainerLabelLayout(String value) {
		this.value = value;
	}

	@Override
	public String getStringValue() {
		return this.value;
	}

	@Override
	public String getDisplayName() {
		return StringUtils.translate("logisticmatica.config.label_layout." + this.value);
	}

	@Override
	public IConfigOptionListEntry cycle(boolean forward) {
		ContainerLabelLayout[] values = values();
		int offset = forward ? 1 : values.length - 1;
		return values[(this.ordinal() + offset) % values.length];
	}

	@Override
	public IConfigOptionListEntry fromString(String value) {
		for (ContainerLabelLayout layout : values()) {
			if (layout.value.equalsIgnoreCase(value)) return layout;
		}
		return VERTICAL;
	}
}
