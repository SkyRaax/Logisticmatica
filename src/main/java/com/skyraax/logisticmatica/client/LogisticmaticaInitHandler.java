package com.skyraax.logisticmatica.client;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.config.Configs;
import com.skyraax.logisticmatica.client.gui.GuiConfigs;
import com.skyraax.logisticmatica.client.hud.MaterialHudRenderer;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

/**
 * MaLiLib initialization handler — the single place where all client-side systems are
 * registered once the game is ready and MaLiLib can accept configs, keybinds and
 * renderers without class-loading issues.
 */
public class LogisticmaticaInitHandler implements IInitializationHandler {
	@Override
	public void registerModHandlers() {
		ConfigManager.getInstance().registerConfigHandler(Logisticmatica.MOD_ID, new Configs());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(
				new ModInfo(Logisticmatica.MOD_ID, Logisticmatica.MOD_NAME, GuiConfigs::new));

		// Renderers: the material HUD (in-game GUI overlay), the tracked-container highlight, and the
		// floating item labels above marked containers (both world renderers).
		RenderEventHandler.getInstance().registerInGameGuiRenderer(new MaterialHudRenderer());
		RenderEventHandler.getInstance().registerWorldLastRenderer(new ContainerHighlightRenderer());
		RenderEventHandler.getInstance().registerWorldLastRenderer(new ContainerLabelRenderer());

		// Hotkeys: register the keybinds and attach callbacks.
		InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
		Configs.Hotkeys.TOGGLE_HUD.getKeybind().setCallback(
				new KeyCallbackToggleBooleanConfigWithMessage(Configs.Hud.ENABLED));
		Configs.Hotkeys.OPEN_MATERIAL_LIST.getKeybind().setCallback(new OpenMaterialListCallback());
		Configs.Hotkeys.OPEN_FOCUS_PICKER.getKeybind().setCallback(new OpenFocusPickerCallback());
		Configs.Hotkeys.OPEN_CONTAINER_OVERVIEW.getKeybind().setCallback(new OpenContainerOverviewCallback());
		Configs.Hotkeys.OPEN_SUBSTITUTIONS.getKeybind().setCallback(new OpenSubstitutionsCallback());
		Configs.Hotkeys.MARK_CONTAINER.getKeybind().setCallback(new MarkContainerCallback());

		// Persistence + counting: load tracked containers on world join, and (single-player) keep
		// their content snapshots fresh each tick so their items count towards the list.
		WorldLoadHandler.getInstance().registerWorldLoadPostHandler(new ContainerTrackerWorldLoad());
		TickHandler.getInstance().registerClientTickHandler(new ContainerContentTickHandler());

		// Load persisted material substitutions once; they are re-applied to schematics on load/join.
		SubstitutionManager.getInstance().load();

		Logisticmatica.LOGGER.info("[{}] Config, HUD, hotkeys and container tracking registered.", Logisticmatica.MOD_NAME);
	}
}
