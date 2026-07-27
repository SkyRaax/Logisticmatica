package com.skyraax.logisticmatica.client.gui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.GuiContext;

/** A native MaLiLib button that renders the full-size Logisticmatica icon at menu-icon size. */
public final class ButtonLogisticmaticaMenu extends ButtonBase {
	private static final IGuiIcon ICON = LogisticmaticaIcon.INSTANCE;

	public ButtonLogisticmaticaMenu(int x, int y, int width, int height, String label) {
		super(x, y, width, height, label);
	}

	@Override
	public void render(GuiContext context, int mouseX, int mouseY, boolean selected) {
		super.render(context, mouseX, mouseY, selected);
		if (!this.visible) return;

		this.hovered = this.isMouseOver(mouseX, mouseY);
		context.blitSprite(RenderPipelines.GUI_TEXTURED, this.getTexture(this.hovered),
				this.x, this.y, this.width, this.height);
		ICON.renderAt(context, this.x + 4, this.y + (this.height - ICON.getHeight()) / 2,
				0.0F, this.enabled, this.hovered);

		int color = !this.enabled ? 0xFFA0A0A0 : this.hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
		this.drawStringWithShadow(context, this.x + ICON.getWidth() + 8,
				this.y + (this.height - 8) / 2, color, this.displayString);
	}

	/**
	 * MaLiLib's generic icon path assumes a 256-pixel sprite sheet. The mod icon is a standalone
	 * 128-pixel texture, so it is drawn at native size on a scaled pose instead.
	 */
	private enum LogisticmaticaIcon implements IGuiIcon {
		INSTANCE;

		private static final int SOURCE_SIZE = 128;
		private static final int DISPLAY_SIZE = 16;
		private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
				"logisticmatica", "icon.png");

		@Override public int getWidth() { return DISPLAY_SIZE; }
		@Override public int getHeight() { return DISPLAY_SIZE; }
		@Override public int getU() { return 0; }
		@Override public int getV() { return 0; }
		@Override public Identifier getTexture() { return TEXTURE; }

		@Override
		public void renderAt(GuiContext context, int x, int y, float z, boolean enabled, boolean selected) {
			float scale = DISPLAY_SIZE / (float) SOURCE_SIZE;
			context.pose().pushMatrix();
			context.pose().translate(x, y);
			context.pose().scale(scale, scale);
			context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
					0, 0, 0.0F, 0.0F, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE);
			context.pose().popMatrix();
		}
	}
}
