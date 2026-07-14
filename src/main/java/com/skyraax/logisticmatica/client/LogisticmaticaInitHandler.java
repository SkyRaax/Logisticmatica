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
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
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

		// Renderers: the material HUD and the chest-peek panel (in-game GUI overlays), plus the
		// tracked-container highlight (world). The container label captures the camera transform in the
		// world pass and draws its projected panel in the GUI pass, so it registers for both.
		RenderEventHandler.getInstance().registerInGameGuiRenderer(new MaterialHudRenderer());
		RenderEventHandler.getInstance().registerInGameGuiRenderer(new ContainerPeekRenderer());
		RenderEventHandler.getInstance().registerWorldLastRenderer(new ContainerHighlightRenderer());

		ContainerLabelRenderer labelRenderer = new ContainerLabelRenderer();
		RenderEventHandler.getInstance().registerWorldLastRenderer(labelRenderer);
		RenderEventHandler.getInstance().registerInGameGuiRenderer(labelRenderer);

		// Hotkeys: register the keybinds and attach callbacks. Every callback except the menu is wrapped
		// so it records activity (see HotkeyActivity), letting the menu hotkey suppress itself right
		// after a chord that shares its key.
		InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
		Configs.Hotkeys.OPEN_MENU.getKeybind().setCallback(new OpenMenuCallback());
		Configs.Hotkeys.TOGGLE_HUD.getKeybind().setCallback(
				marking(new KeyCallbackToggleBooleanConfigWithMessage(Configs.Hud.ENABLED)));
		Configs.Hotkeys.OPEN_MATERIAL_LIST.getKeybind().setCallback(marking(new OpenMaterialListCallback()));
		Configs.Hotkeys.OPEN_FOCUS_PICKER.getKeybind().setCallback(marking(new OpenFocusPickerCallback()));
		Configs.Hotkeys.OPEN_CONTAINER_OVERVIEW.getKeybind().setCallback(marking(new OpenContainerOverviewCallback()));
		Configs.Hotkeys.OPEN_SUBSTITUTIONS.getKeybind().setCallback(marking(new OpenSubstitutionsCallback()));
		Configs.Hotkeys.CYCLE_HUD_PAGE.getKeybind().setCallback(marking((action, key) -> {
			MaterialHudRenderer.cycleHudPage();
			return true;
		}));
		Configs.Hotkeys.MARK_CONTAINER.getKeybind().setCallback(marking(new MarkContainerCallback()));

		// Persistence + counting: load tracked containers on world join, and (single-player) keep
		// their content snapshots fresh each tick so their items count towards the list.
		WorldLoadHandler.getInstance().registerWorldLoadPostHandler(new ContainerTrackerWorldLoad());
		TickHandler.getInstance().registerClientTickHandler(new ContainerContentTickHandler());

		// Load persisted material substitutions once; they are re-applied to schematics on load/join.
		SubstitutionManager.getInstance().load();

		Logisticmatica.LOGGER.info("[{}] Config, HUD, hotkeys and container tracking registered.", Logisticmatica.MOD_NAME);
	}

	/** Wraps a hotkey callback so it records activity, so the menu hotkey can defer to a chord that shares its key. */
	private static IHotkeyCallback marking(IHotkeyCallback delegate) {
		return (action, key) -> {
			HotkeyActivity.mark();
			return delegate.onKeyAction(action, key);
		};
	}
}
