package com.skyraax.logisticmatica.mixin.litematica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonOnOff;
import fi.dy.masa.malilib.util.GuiUtils;

import fi.dy.masa.litematica.gui.GuiMaterialList;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Adds a "Logisticmatica HUD: ON/OFF" toggle button to Litematica's material-list screen, so the
 * HUD can be switched on/off right from the menu the player is already in.
 *
 * <p><strong>{@code remap = false} is essential:</strong> this injects into another mod's method
 * ({@code GuiMaterialList.initGui}), whose name must NOT be run through Minecraft's obfuscation
 * mapping (it isn't in it), otherwise the injector silently finds no target. Lives in a separate
 * {@code "required": false} mixin config so a client without Litematica just skips it instead of
 * crashing. {@code addButton} is a public {@link GuiBase} method, so no accessor is needed.
 */
@Mixin(GuiMaterialList.class)
public abstract class MixinGuiMaterialList {
	@Inject(method = "initGui", at = @At("RETURN"), remap = false)
	private void logisticmatica$addHudToggle(CallbackInfo ci) {
		Logisticmatica.LOGGER.info("[{}] Adding HUD toggle button to the material-list screen.",
				Logisticmatica.MOD_NAME);

		GuiMaterialList self = (GuiMaterialList) (Object) this;
		GuiBase gui = (GuiBase) (Object) this;

		// First-guess placement: top row, in the gap left of Litematica's multiplier field.
		// Fine-tuned once we have an in-game screenshot.
		int x = GuiUtils.getScaledWindowWidth() - 200;
		int y = 24;

		ButtonOnOff button = new ButtonOnOff(x, y, -1, false,
				"logisticmatica.gui.button.toggle_hud", Configs.Hud.ENABLED.getBooleanValue());
		gui.addButton(button, new HudToggleListener(self));
	}
}
