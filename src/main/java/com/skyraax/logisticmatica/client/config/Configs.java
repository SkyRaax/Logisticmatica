package com.skyraax.logisticmatica.client.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.skyraax.logisticmatica.Logisticmatica;

/**
 * Logisticmatica's configuration, built on MaLiLib's config framework so it shows up in an
 * in-game config screen and persists to {@code config/logisticmatica.json}. Structured into
 * the same nested-category pattern Litematica uses.
 */
public class Configs implements IConfigHandler {
	private static final String CONFIG_FILE_NAME = Logisticmatica.MOD_ID + ".json";

	private static final String HUD_KEY = Logisticmatica.MOD_ID + ".config.hud";

	public static class Hud {
		public static final ConfigBoolean    ENABLED         = new ConfigBoolean("hudEnabled", true).apply(HUD_KEY);
		public static final ConfigBoolean    RENDER_IN_GUIS  = new ConfigBoolean("hudRenderInGuis", false).apply(HUD_KEY);
		public static final ConfigOptionList ALIGNMENT       = new ConfigOptionList("hudAlignment", HudAlignment.TOP_LEFT).apply(HUD_KEY);
		public static final ConfigInteger    OFFSET_X        = new ConfigInteger("hudOffsetX", 4, 0, 32000).apply(HUD_KEY);
		public static final ConfigInteger    OFFSET_Y        = new ConfigInteger("hudOffsetY", 4, 0, 32000).apply(HUD_KEY);
		public static final ConfigDouble     SCALE           = new ConfigDouble("hudScale", 1.0, 0.1, 4.0).apply(HUD_KEY);
		public static final ConfigInteger    MAX_LINES       = new ConfigInteger("hudMaxLines", 0, 0, 1024).apply(HUD_KEY);
		public static final ConfigBoolean    HIDE_COMPLETE   = new ConfigBoolean("hudHideComplete", false).apply(HUD_KEY);
		public static final ConfigBoolean    ONLY_MISSING    = new ConfigBoolean("hudOnlyMissing", false).apply(HUD_KEY);
		public static final ConfigBoolean    SHOW_HEADER     = new ConfigBoolean("hudShowHeader", true).apply(HUD_KEY);
		public static final ConfigBoolean    SHOW_BACKGROUND = new ConfigBoolean("hudBackground", true).apply(HUD_KEY);
		public static final ConfigBoolean    SHOW_ITEM_ICONS = new ConfigBoolean("hudShowItemIcons", true).apply(HUD_KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				ENABLED, RENDER_IN_GUIS, ALIGNMENT, OFFSET_X, OFFSET_Y, SCALE, MAX_LINES,
				HIDE_COMPLETE, ONLY_MISSING, SHOW_HEADER, SHOW_BACKGROUND, SHOW_ITEM_ICONS
		);
	}

	private static final String COLORS_KEY = Logisticmatica.MOD_ID + ".config.colors";

	public static class Colors {
		public static final ConfigColor TEXT                = new ConfigColor("colorText",              "#FFFFFFFF").apply(COLORS_KEY);
		public static final ConfigColor HEADER              = new ConfigColor("colorHeader",            "#FFFFAA00").apply(COLORS_KEY);
		public static final ConfigColor HAVE                = new ConfigColor("colorHave",              "#FF55FF55").apply(COLORS_KEY);
		public static final ConfigColor MISSING             = new ConfigColor("colorMissing",           "#FFFF5555").apply(COLORS_KEY);
		public static final ConfigColor BACKGROUND          = new ConfigColor("colorBackground",        "#B0000000").apply(COLORS_KEY);
		public static final ConfigColor CONTAINER_HIGHLIGHT = new ConfigColor("colorContainerHighlight", "#FF55FF55").apply(COLORS_KEY);

		public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
				TEXT, HEADER, HAVE, MISSING, BACKGROUND, CONTAINER_HIGHLIGHT
		);
	}

	private static final String HOTKEYS_KEY = Logisticmatica.MOD_ID + ".config.hotkeys";

	public static class Hotkeys {
		public static final ConfigHotkey OPEN_MATERIAL_LIST = new ConfigHotkey("openMaterialList", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey TOGGLE_HUD         = new ConfigHotkey("toggleHud", "").apply(HOTKEYS_KEY);
		public static final ConfigHotkey MARK_CONTAINER     = new ConfigHotkey("markContainer", "").apply(HOTKEYS_KEY);

		public static final ImmutableList<IHotkey> HOTKEY_LIST = ImmutableList.of(
				OPEN_MATERIAL_LIST, TOGGLE_HUD, MARK_CONTAINER
		);
	}

	public static void loadFromFile() {
		Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

		if (Files.exists(configFile) && Files.isReadable(configFile)) {
			JsonElement element = JsonUtils.parseJsonFile(configFile);

			if (element != null && element.isJsonObject()) {
				JsonObject root = element.getAsJsonObject();

				ConfigUtils.readConfigBase(root, "HUD", Hud.OPTIONS);
				ConfigUtils.readConfigBase(root, "Colors", Colors.OPTIONS);
				ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);

				Logisticmatica.LOGGER.debug("[{}] Loaded config from '{}'.", Logisticmatica.MOD_NAME, configFile);
			} else {
				Logisticmatica.LOGGER.warn("[{}] Failed to read config file '{}'.", Logisticmatica.MOD_NAME, configFile);
			}
		}
	}

	public static void saveToFile() {
		Path dir = FileUtils.getConfigDirectory();

		if (!Files.exists(dir)) {
			FileUtils.createDirectoriesIfMissing(dir);
		}

		if (Files.isDirectory(dir)) {
			JsonObject root = new JsonObject();

			ConfigUtils.writeConfigBase(root, "HUD", Hud.OPTIONS);
			ConfigUtils.writeConfigBase(root, "Colors", Colors.OPTIONS);
			ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);

			JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
		}
	}

	@Override
	public void load() {
		loadFromFile();
	}

	@Override
	public void save() {
		saveToFile();
	}
}
