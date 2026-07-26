package com.skyraax.logisticmatica.client.gui;

import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedMemberView;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** Project actions, invitation flow and per-member role administration. */
public class GuiSharedProjectDetails extends GuiBase implements SharingRefreshable {
	private static final int[] ROLES = {
			SharePermission.VIEWER, SharePermission.BUILDER,
			SharePermission.EDITOR, SharePermission.MANAGER
	};
	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private final UUID projectId;
	private String inviteName = "";
	private int invitePermissions = SharePermission.BUILDER;
	private boolean deleteArmed;

	public GuiSharedProjectDetails(UUID projectId) {
		this.projectId = projectId;
		this.useTitleHierarchy = false;
	}

	@Override
	public void initGui() {
		super.initGui();
		SharedProjectView project = this.sharing.project(this.projectId);
		if (project == null) {
			GuiBase.openGui(this.getParent());
			return;
		}
		this.title = StringUtils.translate("logisticmatica.gui.title.shared_project", project.name());
		int x = 12;
		int y = 28;
		String ownerLabel = StringUtils.translate("logisticmatica.gui.share.owner", project.ownerName());
		this.addLabel(x, y, this.getStringWidth(ownerLabel), 12, 0xFFFFFFFF, ownerLabel);
		y += 14;
		String location = StringUtils.translate("logisticmatica.gui.share.location", project.dimension(),
				project.x(), project.y(), project.z());
		this.addLabel(x, y, this.getStringWidth(location), 12, 0xFFAAAAAA, location);
		y += 18;

		if (project.pendingInvite()) {
			this.addButton(new ButtonGeneric(x, y, -1, 20,
					StringUtils.translate("logisticmatica.gui.share.accept")),
					new Listener(Action.ACCEPT, this, null));
			x += 88;
			this.addButton(new ButtonGeneric(x, y, -1, 20,
					StringUtils.translate("logisticmatica.gui.share.decline")),
					new Listener(Action.DECLINE, this, null));
		} else {
			x = this.addAction(x, y, "download", Action.DOWNLOAD, true);
			x = this.addAction(x, y, "upload", Action.UPLOAD,
					project.can(SharePermission.UPDATE_SCHEMATIC));
		}
		y += 28;

		if (!project.pendingInvite() && project.can(SharePermission.INVITE)) {
			String invite = StringUtils.translate("logisticmatica.gui.share.invite_label");
			this.addLabel(12, y, this.getStringWidth(invite), 12, 0xFFFFAA00, invite);
			y += 14;
			GuiTextFieldGeneric field = new GuiTextFieldGeneric(12, y, 140, 16, this.font);
			field.setValueWrapper(this.inviteName);
			this.addTextField(field, new InviteNameListener(this), TextFieldType.STRING);
			x = 158;
			x = this.addAction(x, y, "role", Action.INVITE_ROLE, true,
					WidgetSharedProjectEntry.role(this.invitePermissions));
			this.addAction(x, y, "invite", Action.INVITE, true);
			y += 28;
		}

		if (!project.pendingInvite()) {
			String members = StringUtils.translate("logisticmatica.gui.share.members");
			this.addLabel(12, y, this.getStringWidth(members), 12, 0xFFFFAA00, members);
			y += 14;
			for (SharedMemberView member : project.members()) {
				if (y + 22 >= this.getScreenHeight() - 38) break;
				String name = member.playerName() + (member.accepted() ? "" : " (pending)");
				this.addLabel(16, y + 5, this.getStringWidth(name), 12, 0xFFFFFFFF, name);
				String role = member.playerId().equals(project.ownerId())
						? StringUtils.translate("logisticmatica.gui.share.role.owner")
						: WidgetSharedProjectEntry.role(member.permissions());
				boolean editable = project.can(SharePermission.MANAGE_PERMISSIONS)
						&& !member.playerId().equals(project.ownerId());
				ButtonGeneric roleButton = new ButtonGeneric(180, y, 90, 20, role);
				roleButton.setEnabled(editable);
				this.addButton(roleButton, new Listener(Action.MEMBER_ROLE, this, member.playerId()));
				if (editable) {
					ButtonGeneric remove = new ButtonGeneric(274, y, -1, 20,
							StringUtils.translate("logisticmatica.gui.share.remove"));
					this.addButton(remove, new Listener(Action.REMOVE_MEMBER, this, member.playerId()));
				}
				y += 22;
			}
		}

		UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
		boolean canDelete = project.can(SharePermission.DELETE);
		String destructive = canDelete
				? StringUtils.translate(this.deleteArmed ? "logisticmatica.gui.share.confirm_delete"
						: "logisticmatica.gui.share.delete")
				: StringUtils.translate("logisticmatica.gui.share.leave");
		ButtonGeneric destructiveButton = new ButtonGeneric(12, this.getScreenHeight() - 34, -1, 20, destructive);
		destructiveButton.setEnabled(canDelete || self != null);
		this.addButton(destructiveButton, new Listener(canDelete ? Action.DELETE : Action.LEAVE, this, null));

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back);
		this.addButton(backButton, new Listener(Action.BACK, this, null));
	}

	private int addAction(int x, int y, String key, Action action, boolean enabled) {
		return this.addAction(x, y, key, action, enabled,
				StringUtils.translate("logisticmatica.gui.share." + key));
	}

	private int addAction(int x, int y, String key, Action action, boolean enabled, String label) {
		ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, label);
		button.setEnabled(enabled);
		this.addButton(button, new Listener(action, this, null));
		return x + button.getWidth() + 4;
	}

	@Override public void refreshSharing() { this.initGui(); }


	private static int nextRole(int current) {
		for (int i = 0; i < ROLES.length; i++) {
			if (ROLES[i] == current) return ROLES[(i + 1) % ROLES.length];
		}
		return SharePermission.VIEWER;
	}

	private enum Action {
		DOWNLOAD, UPLOAD, ACCEPT, DECLINE, INVITE_ROLE, INVITE,
		MEMBER_ROLE, REMOVE_MEMBER, DELETE, LEAVE, BACK
	}

	private record InviteNameListener(GuiSharedProjectDetails gui)
			implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override public boolean onTextChange(GuiTextFieldGeneric field) {
			this.gui.inviteName = field.getValueWrapper();
			return true;
		}
	}

	private record Listener(Action action, GuiSharedProjectDetails gui, @Nullable UUID memberId)
			implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.action) {
				case DOWNLOAD -> this.gui.sharing.download(this.gui.projectId);
				case UPLOAD -> this.gui.sharing.uploadCurrentSchematic(this.gui.projectId);
				case ACCEPT -> this.gui.sharing.respondToInvite(this.gui.projectId, true);
				case DECLINE -> this.gui.sharing.respondToInvite(this.gui.projectId, false);
				case INVITE_ROLE -> {
					this.gui.invitePermissions = nextRole(this.gui.invitePermissions);
					this.gui.initGui();
				}
				case INVITE -> {
					if (this.gui.inviteName.isBlank()) return;
					this.gui.sharing.invite(this.gui.projectId, this.gui.inviteName.strip(),
							this.gui.invitePermissions);
					this.gui.inviteName = "";
					this.gui.initGui();
				}
				case MEMBER_ROLE -> {
					SharedProjectView project = this.gui.sharing.project(this.gui.projectId);
					if (project != null && this.memberId != null) {
						for (SharedMemberView member : project.members()) {
							if (member.playerId().equals(this.memberId)) {
								GuiMemberPermissions permissions = new GuiMemberPermissions(
										project.id(), member.playerId(), member.permissions());
								permissions.setParent(this.gui);
								GuiBase.openGui(permissions);
								break;
							}
						}
					}
				}
				case REMOVE_MEMBER -> {
					if (this.memberId != null) this.gui.sharing.removeMember(this.gui.projectId, this.memberId);
				}
				case DELETE -> {
					if (this.gui.deleteArmed) this.gui.sharing.delete(this.gui.projectId);
					else { this.gui.deleteArmed = true; this.gui.initGui(); }
				}
				case LEAVE -> this.gui.sharing.leave(this.gui.projectId);
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
	}
}
