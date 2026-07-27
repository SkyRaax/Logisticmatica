package com.skyraax.logisticmatica.client.share;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/** Per-server, per-project local notification preferences. All categories default to visible. */
public final class ProjectNotificationSettings {
	private static final ProjectNotificationSettings INSTANCE = new ProjectNotificationSettings();
	private final Map<UUID, EnumSet<ProjectNotificationCategory>> disabled = new LinkedHashMap<>();
	@Nullable private UUID serverId;

	private ProjectNotificationSettings() {}

	public static ProjectNotificationSettings getInstance() {
		return INSTANCE;
	}

	public void activateServer(UUID serverId) {
		this.serverId = serverId;
		this.disabled.clear();
		this.load();
	}

	public void reset() {
		this.serverId = null;
		this.disabled.clear();
	}

	public boolean isEnabled(UUID projectId, ProjectNotificationCategory category) {
		EnumSet<ProjectNotificationCategory> values = this.disabled.get(projectId);
		return values == null || !values.contains(category);
	}

	public void forget(UUID projectId) {
		if (this.disabled.remove(projectId) != null) this.save();
	}

	public void toggle(UUID projectId, ProjectNotificationCategory category) {
		EnumSet<ProjectNotificationCategory> values = this.disabled.computeIfAbsent(projectId,
				ignored -> EnumSet.noneOf(ProjectNotificationCategory.class));
		if (!values.remove(category)) values.add(category);
		if (values.isEmpty()) this.disabled.remove(projectId);
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
			if (!project.getValue().isJsonArray()) continue;
			EnumSet<ProjectNotificationCategory> values = EnumSet.noneOf(ProjectNotificationCategory.class);
			for (JsonElement raw : project.getValue().getAsJsonArray()) {
				if (!raw.isJsonPrimitive()) continue;
				try { values.add(ProjectNotificationCategory.valueOf(raw.getAsString())); }
				catch (IllegalArgumentException ignored) {}
			}
			if (!values.isEmpty()) this.disabled.put(id, values);
		}
	}

	private void save() {
		Path file = this.file();
		if (file == null) return;
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, EnumSet<ProjectNotificationCategory>> project : this.disabled.entrySet()) {
			JsonArray values = new JsonArray();
			for (ProjectNotificationCategory category : project.getValue()) values.add(category.name());
			root.add(project.getKey().toString(), values);
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