package com.skyraax.logisticmatica.client.share;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/** Per-server, per-project local notification overrides with category-specific defaults. */
public final class ProjectNotificationSettings {
	private static final ProjectNotificationSettings INSTANCE = new ProjectNotificationSettings();
	private final Map<UUID, EnumMap<ProjectNotificationCategory, Boolean>> overrides = new LinkedHashMap<>();
	@Nullable private UUID serverId;

	private ProjectNotificationSettings() {}

	public static ProjectNotificationSettings getInstance() {
		return INSTANCE;
	}

	public void activateServer(UUID serverId) {
		this.serverId = serverId;
		this.overrides.clear();
		this.load();
	}

	public void reset() {
		this.serverId = null;
		this.overrides.clear();
	}

	public boolean isEnabled(UUID projectId, ProjectNotificationCategory category) {
		Map<ProjectNotificationCategory, Boolean> values = this.overrides.get(projectId);
		return values == null ? category.defaultEnabled()
				: values.getOrDefault(category, category.defaultEnabled());
	}

	public void forget(UUID projectId) {
		if (this.overrides.remove(projectId) != null) this.save();
	}

	public void toggle(UUID projectId, ProjectNotificationCategory category) {
		boolean enabled = !this.isEnabled(projectId, category);
		EnumMap<ProjectNotificationCategory, Boolean> values = this.overrides.computeIfAbsent(projectId,
				ignored -> new EnumMap<>(ProjectNotificationCategory.class));
		if (enabled == category.defaultEnabled()) values.remove(category);
		else values.put(category, enabled);
		if (values.isEmpty()) this.overrides.remove(projectId);
		this.save();
	}

	private void load() {
		Path file = this.file();
		if (file == null) return;
		JsonElement element = JsonUtils.parseJsonFile(file);
		if (element == null || !element.isJsonObject()) return;
		for (Map.Entry<String, JsonElement> project : element.getAsJsonObject().entrySet()) {
			UUID id;
			try { id = UUID.fromString(project.getKey()); }
			catch (IllegalArgumentException ignored) { continue; }
			EnumMap<ProjectNotificationCategory, Boolean> values =
					new EnumMap<>(ProjectNotificationCategory.class);
			if (project.getValue().isJsonObject()) {
				for (Map.Entry<String, JsonElement> raw : project.getValue().getAsJsonObject().entrySet()) {
					if (!raw.getValue().isJsonPrimitive()) continue;
					try {
						ProjectNotificationCategory category = ProjectNotificationCategory.valueOf(raw.getKey());
						boolean enabled = raw.getValue().getAsBoolean();
						if (enabled != category.defaultEnabled()) values.put(category, enabled);
					} catch (IllegalArgumentException ignored) {}
				}
			} else if (project.getValue().isJsonArray()) {
				// v1 stored only disabled categories. Preserve every prior choice, including
				// categories whose defaults changed in a newer version.
				EnumSet<ProjectNotificationCategory> disabled =
						EnumSet.noneOf(ProjectNotificationCategory.class);
				for (JsonElement raw : project.getValue().getAsJsonArray()) {
					if (!raw.isJsonPrimitive()) continue;
					try { disabled.add(ProjectNotificationCategory.valueOf(raw.getAsString())); }
					catch (IllegalArgumentException ignored) {}
				}
				for (ProjectNotificationCategory category : ProjectNotificationCategory.values()) {
					boolean enabled = !disabled.contains(category);
					if (enabled != category.defaultEnabled()) values.put(category, enabled);
				}
			}
			if (!values.isEmpty()) this.overrides.put(id, values);
		}
	}

	private void save() {
		Path file = this.file();
		if (file == null) return;
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, EnumMap<ProjectNotificationCategory, Boolean>> project : this.overrides.entrySet()) {
			JsonObject values = new JsonObject();
			for (Map.Entry<ProjectNotificationCategory, Boolean> entry : project.getValue().entrySet()) {
				values.addProperty(entry.getKey().name(), entry.getValue());
			}
			if (!values.isEmpty()) root.add(project.getKey().toString(), values);
		}
		FileUtils.createDirectoriesIfMissing(file.getParent());
		JsonUtils.writeJsonToFile(root, file);
	}

	@Nullable
	private Path file() {
		if (this.serverId == null) return null;
		return FileUtils.getConfigDirectory().resolve(Logisticmatica.MOD_ID).resolve("shared")
				.resolve(this.serverId.toString()).resolve("notifications.json");
	}
}