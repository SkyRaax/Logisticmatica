package com.skyraax.logisticmatica.client.share;

/** User-configurable categories for concrete shared-project activity messages. */
public enum ProjectNotificationCategory {
	PLACEMENT("placement"),
	SCHEMATIC("schematic"),
	SUBSTITUTIONS("substitutions"),
	CONTAINER_MARKS("container_marks"),
	CONTAINER_CONTENTS("container_contents"),
	ACCESS("access");

	private final String key;

	ProjectNotificationCategory(String key) {
		this.key = key;
	}

	public String translationKey() {
		return "logisticmatica.gui.notifications." + this.key;
	}
}