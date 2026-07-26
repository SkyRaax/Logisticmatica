package com.skyraax.logisticmatica.client.gui;

import java.util.Locale;
import java.util.UUID;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedMemberView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Granular capability editor for one member of one shared placement. */
public class GuiMemberPermissions extends GuiBase implements SharingRefreshable {
	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private final UUID projectId;
	private final UUID memberId;
	private int permissions;
	private int serverPermissions;

	public GuiMemberPermissions(UUID projectId, UUID memberId, int permissions) {
		this.projectId = projectId;
		this.memberId = memberId;
		this.permissions = SharePermission.sanitize(permissions) | SharePermission.VIEW.mask();
		this.serverPermissions = this.permissions;
		this.useTitleHierarchy = false;
	}

	@Override
	public void initGui() {
		super.initGui();
		SharedProjectView project = this.sharing.project(this.projectId);
		SharedMemberView member = findMember(project, this.memberId);
		if (project == null || member == null) {
			GuiBase.openGui(this.getParent());
			return;
		}
		this.title = StringUtils.translate("logisticmatica.gui.title.member_permissions", member.playerName());
		int x = 12;
		int y = 30;
		for (SharePermission permission : SharePermission.values()) {
			String name = StringUtils.translate("logisticmatica.gui.share.permission."
					+ permission.name().toLowerCase(Locale.ROOT));
			String state = StringUtils.translate(permission.isIn(this.permissions)
					? "logisticmatica.gui.share.permission.on" : "logisticmatica.gui.share.permission.off");
			ButtonGeneric toggle = new ButtonGeneric(x, y, 230, 20, name + ": " + state);
			toggle.setEnabled(permission != SharePermission.VIEW);
			this.addButton(toggle, new Listener(permission, this));
			y += 23;
		}

		ButtonGeneric apply = new ButtonGeneric(12, this.getScreenHeight() - 34, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.apply_permissions"));
		this.addButton(apply, new ApplyListener(this));
		String back = StringUtils.translate("logisticmatica.gui.button.back");
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back);
		this.addButton(backButton, (button, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	@Override public void refreshSharing() {
		SharedMemberView member = findMember(this.sharing.project(this.projectId), this.memberId);
		if (member != null && member.permissions() != this.serverPermissions) {
			this.permissions = member.permissions();
			this.serverPermissions = member.permissions();
		}
		this.initGui();
	}

	private static SharedMemberView findMember(SharedProjectView project, UUID id) {
		if (project == null) return null;
		for (SharedMemberView member : project.members()) if (member.playerId().equals(id)) return member;
		return null;
	}

	private record Listener(SharePermission permission, GuiMemberPermissions gui)
			implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			this.gui.permissions ^= this.permission.mask();
			this.gui.permissions |= SharePermission.VIEW.mask();
			this.gui.initGui();
		}
	}

	private record ApplyListener(GuiMemberPermissions gui) implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			this.gui.sharing.setPermissions(this.gui.projectId, this.gui.memberId, this.gui.permissions);
			GuiBase.openGui(this.gui.getParent());
		}
	}
}
