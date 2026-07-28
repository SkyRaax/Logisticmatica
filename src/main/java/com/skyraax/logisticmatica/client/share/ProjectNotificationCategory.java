package com.skyraax.logisticmatica.client.share;

/** User-configurable categories for concrete shared-project activity messages. */
public enum ProjectNotificationCategory {
	PLACEMENT("placement", true),
	SCHEMATIC("schematic", true),
	SUBSTITUTIONS("substitutions", true),
	CONTAINER_MARKS("container_marks", true),
	CONTAINER_CONTENTS("container_contents", false),
	STATUS("status", true),
	ACCESS("access", true);

	private final String key;
	private final boolean defaultEnabled;

	ProjectNotificationCategory(String key, boolean defaultEnabled) {
		this.key = key;
		this.defaultEnabled = defaultEnabled;
	}

	public String translationKey() {
		return "logisticmatica.gui.notifications." + this.key;
	}

	public boolean defaultEnabled() {
		return this.defaultEnabled;
	}
}