package com.skyraax.logisticmatica.mixin.litematica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonOnOff;
import fi.dy.masa.malilib.util.GuiUtils;

import fi.dy.masa.litematica.gui.GuiMaterialList;

import com.skyraax.logisticmatica.client.config.Configs;

/**
 * Adds a "Logisticmatica HUD: ON/OFF" toggle button to Litematica's material-list screen, so the
 * HUD can be switched on/off right from the menu the player is already in.
 *
 * <p>Lives in a separate {@code "required": false} mixin config so a client without Litematica
 * simply skips this mixin instead of crashing. {@code addButton} is a public method of
 * {@link GuiBase} (Litematica's screen ultimately extends it), so no accessor is needed.
 */
@Mixin(GuiMaterialList.class)
public abstract class MixinGuiMaterialList {
	@Inject(method = "initGui", at = @At("TAIL"))
	private void logisticmatica$addHudToggle(CallbackInfo ci) {
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
