package com.skyraax.logisticmatica.share;

/** Server-authoritative project phase visible to every directory viewer. */
public enum ProjectStatus {
	PLANNING("planning"),
	COLLECTING("collecting"),
	READY_TO_BUILD("ready_to_build"),
	BUILDING("building"),
	PAUSED("paused"),
	BLOCKED("blocked"),
	COMPLETED("completed");

	private final String key;

	ProjectStatus(String key) {
		this.key = key;
	}

	public String translationKey() {
		return "logisticmatica.project.status." + this.key;
	}

	public String descriptionKey() {
		return this.translationKey() + ".description";
	}

	public ProjectStatus next() {
		ProjectStatus[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	public static ProjectStatus byId(int id) {
		ProjectStatus[] values = values();
		if (id < 0 || id >= values.length) {
			throw new IllegalArgumentException("Unknown project status " + id);
		}
		return values[id];
	}
}
