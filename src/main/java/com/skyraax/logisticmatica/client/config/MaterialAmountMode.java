package com.skyraax.logisticmatica.client.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/** Which requirement is shown and compared against available materials. */
public enum MaterialAmountMode implements IConfigOptionListEntry {
	TOTAL("total"),
	REMAINING("remaining");

	private final String value;

	MaterialAmountMode(String value) {
		this.value = value;
	}

	@Override public String getStringValue() { return this.value; }
	@Override public String getDisplayName() {
		return StringUtils.translate("logisticmatica.config.material_amount." + this.value);
	}
	@Override public IConfigOptionListEntry cycle(boolean forward) {
		return this == TOTAL ? REMAINING : TOTAL;
	}
	@Override public IConfigOptionListEntry fromString(String value) {
		return REMAINING.value.equalsIgnoreCase(value) ? REMAINING : TOTAL;
	}
}
