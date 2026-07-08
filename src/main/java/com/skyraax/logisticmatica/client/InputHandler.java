package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Registers Logisticmatica's hotkeys with MaLiLib so they can be bound in the config GUI
 * and fire their callbacks. The callbacks themselves are attached in
 * {@link LogisticmaticaInitHandler}.
 */
public class InputHandler implements IKeybindProvider {
	private static final InputHandler INSTANCE = new InputHandler();

	private InputHandler() {
	}

	public static InputHandler getInstance() {
		return INSTANCE;
	}

	@Override
	public void addKeysToMap(IKeybindManager manager) {
		for (IHotkey hotkey : Configs.Hotkeys.HOTKEY_LIST) {
			manager.addKeybindToMap(hotkey.getKeybind());
		}
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory(Logisticmatica.MOD_NAME,
				Logisticmatica.MOD_ID + ".hotkeys.category.main", Configs.Hotkeys.HOTKEY_LIST);
	}
}
