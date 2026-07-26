package com.skyraax.logisticmatica.client.gui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Compact two-line summary of one shared project. */
public class WidgetSharedProjectEntry extends WidgetListEntryBase<SharedProjectView> {
	@Nullable private final SharedProjectView project;
	private final boolean odd;

	public WidgetSharedProjectEntry(int x, int y, int width, int height, boolean odd,
			@Nullable SharedProjectView project, int listIndex) {
		super(x, y, width, height, project, listIndex);
		this.project = project;
		this.odd = odd;
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		int background = selected || this.isMouseOver(mouseX, mouseY) ? 0xA0707070
				: this.odd ? 0xA0101010 : 0xA0303030;
		RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, background);
		if (this.project != null) {
			boolean owner = Minecraft.getInstance().player != null
					&& this.project.ownerId().equals(Minecraft.getInstance().player.getUUID());
			String status = this.project.pendingInvite()
					? StringUtils.translate("logisticmatica.gui.share.pending")
					: this.project.accessRequested()
							? StringUtils.translate("logisticmatica.gui.share.request_sent")
							: owner ? StringUtils.translate("logisticmatica.gui.share.role.owner")
							: this.project.member() ? role(this.project.myPermissions())
							: this.project.can(SharePermission.VIEW)
									? StringUtils.translate("logisticmatica.gui.share.public_role",
											role(this.project.myPermissions()))
									: StringUtils.translate("logisticmatica.gui.share.request_access");
			this.drawString(ctx, this.x + 6, this.y + 5, 0xFFFFFFFF, this.project.name());
			String detail = StringUtils.translate("logisticmatica.gui.share.project_detail",
					this.project.ownerName(), this.project.dimension(), this.project.x(), this.project.y(), this.project.z());
			this.drawString(ctx, this.x + 6, this.y + 18, 0xFFAAAAAA, detail);
			int statusColor = this.project.pendingInvite() || this.project.accessRequested()
					? 0xFFFFAA00 : this.project.can(SharePermission.VIEW) ? 0xFF55FF55 : 0xFFAAAAAA;
			this.drawString(ctx, this.x + this.width - this.getStringWidth(status) - 8,
					this.y + 5, statusColor, status);
		}
		super.render(ctx, mouseX, mouseY, selected);
	}

	public static String role(int permissions) {
		if ((permissions & SharePermission.MANAGER) == SharePermission.MANAGER)
			return StringUtils.translate("logisticmatica.gui.share.role.manager");
		if ((permissions & SharePermission.EDITOR) == SharePermission.EDITOR)
			return StringUtils.translate("logisticmatica.gui.share.role.editor");
		if ((permissions & SharePermission.BUILDER) == SharePermission.BUILDER)
			return StringUtils.translate("logisticmatica.gui.share.role.builder");
		return StringUtils.translate("logisticmatica.gui.share.role.viewer");
	}
}
