package com.skyraax.logisticmatica.client.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.skyraax.logisticmatica.client.FocusController;
import com.skyraax.logisticmatica.client.FocusState;
import com.skyraax.logisticmatica.client.ProjectStatusPresentation;
import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedProjectView;

/** One canonical workspace for server projects, local placements and unplaced local schematics. */
public class GuiProjects extends GuiListBase<GuiProjects.ProjectEntry, GuiProjects.Entry, GuiProjects.ProjectList>
		implements SharingRefreshable {
	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private String filterText = "";

	public GuiProjects() {
		super(10, 68);
		this.useTitleHierarchy = false;
		this.updateTitle();
	}

	private void updateTitle() {
		String status = this.sharing.serverAvailable()
				? StringUtils.translate("logisticmatica.gui.share.connected", this.sharing.serverVersion())
				: StringUtils.translate("logisticmatica.gui.share.unavailable");
		this.title = StringUtils.translate("logisticmatica.gui.title.projects", status);
	}

	@Override protected int getBrowserWidth() { return this.getScreenWidth() - 20; }
	@Override protected int getBrowserHeight() { return this.getScreenHeight() - 104; }

	@Override
	protected ProjectList createListWidget(int listX, int listY) {
		ProjectList list = new ProjectList(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
		list.setFilterText(this.filterText);
		return list;
	}

	@Override
	public void initGui() {
		super.initGui();
		NavBar.add(this, NavBar.Tab.PROJECTS);
		int x = 12;
		int y = 46;
		GuiTextFieldGeneric search = new GuiTextFieldGeneric(x, y, 180, 16, this.font);
		search.setValueWrapper(this.filterText);
		this.addTextField(search, new SearchListener(this), TextFieldType.STRING);
		x += 186;

		ButtonGeneric unfocus = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.projects.unfocus"));
		unfocus.setEnabled(FocusState.getSchematic() != null || DataManager.getMaterialList() != null);
		this.addButton(unfocus, new ActionListener(Action.UNFOCUS, this));
		x += unfocus.getWidth() + 4;

		ButtonGeneric refresh = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.button.material_list.refresh"));
		refresh.setEnabled(this.sharing.serverAvailable());
		this.addButton(refresh, new ActionListener(Action.REFRESH, this));
		x += refresh.getWidth() + 4;

		ButtonGeneric share = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.choose_placement"));
		share.setEnabled(this.sharing.serverAvailable());
		share.setHoverStrings("logisticmatica.gui.share.choose_placement.hover");
		this.addButton(share, new ActionListener(Action.SHARE, this));
		x += share.getWidth() + 4;

		ButtonGeneric help = new ButtonGeneric(x, y, -1, 20,
				StringUtils.translate("logisticmatica.gui.share.help"));
		this.addButton(help, new ActionListener(Action.HELP, this));

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		this.addButton(new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back),
				new ActionListener(Action.BACK, this));
	}

	private void open(ProjectEntry entry) {
		if (entry.project() != null) {
			GuiSharedProjectDetails details = new GuiSharedProjectDetails(entry.project().id());
			details.setParent(this);
			GuiBase.openGui(details);
		} else if (entry.placement() != null) {
			FocusController.focusPlacement(entry.placement());
			this.initGui();
		} else if (entry.schematic() != null) {
			FocusController.focusSchematic(entry.schematic());
			this.initGui();
		}
	}

	@Override
	public void refreshSharing() {
		this.updateTitle();
		this.initGui();
	}

	private enum Action { UNFOCUS, REFRESH, SHARE, HELP, BACK }

	private record ActionListener(Action action, GuiProjects gui) implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			switch (this.action) {
				case UNFOCUS -> { FocusController.clear(); this.gui.initGui(); }
				case REFRESH -> this.gui.sharing.refreshProjects();
				case SHARE -> {
					GuiPlacementPicker picker = new GuiPlacementPicker(GuiPlacementPicker.Mode.SHARE, null);
					picker.setParent(this.gui);
					GuiBase.openGui(picker);
				}
				case HELP -> {
					GuiSharingHelp help = new GuiSharingHelp();
					help.setParent(this.gui);
					GuiBase.openGui(help);
				}
				case BACK -> GuiBase.openGui(this.gui.getParent());
			}
		}
	}

	private record SearchListener(GuiProjects gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override public boolean onTextChange(GuiTextFieldGeneric field) {
			this.gui.filterText = field.getValueWrapper();
			ProjectList list = this.gui.getListWidget();
			if (list != null) {
				list.setFilterText(this.gui.filterText);
				list.refreshEntries();
				list.resetScrollbarPosition();
			}
			return true;
		}
	}

	record ProjectEntry(@Nullable SharedProjectView project, @Nullable SchematicPlacement placement,
			@Nullable LitematicaSchematic schematic) {
		String name() {
			if (this.project != null) return this.project.name();
			if (this.placement != null) return this.placement.getName();
			return this.schematic != null ? this.schematic.getMetadata().getName() : "";
		}

		boolean focused(ClientShareManager sharing) {
			if (this.project != null) return sharing.isFocused(this.project.id());
			if (this.placement != null) return FocusController.isFocused(this.placement);
			return this.schematic != null && FocusState.getPlacement() == null
					&& FocusState.getSchematic() == this.schematic;
		}

		int kind() { return this.project != null ? 0 : this.placement != null ? 1 : 2; }
	}

	static final class ProjectList extends WidgetListBase<ProjectEntry, Entry> {
		private final GuiProjects gui;
		private String filterText = "";

		ProjectList(int x, int y, int width, int height, GuiProjects gui) {
			super(x, y, width, height, null);
			this.gui = gui;
			this.browserEntryHeight = 36;
		}

		void setFilterText(String value) { this.filterText = value != null ? value : ""; }
		@Override protected boolean hasFilter() { return !this.filterText.isBlank(); }
		@Override protected String getFilterText() { return this.filterText.toLowerCase(Locale.ROOT); }

		@Override
		protected Collection<ProjectEntry> getAllEntries() {
			List<ProjectEntry> entries = new ArrayList<>();
			for (SharedProjectView project : this.gui.sharing.projects()) {
				entries.add(new ProjectEntry(project, null, null));
			}
			Set<LitematicaSchematic> represented = Collections.newSetFromMap(new IdentityHashMap<>());
			for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
				represented.add(placement.getSchematic());
				if (this.gui.sharing.isSharedPlacement(placement)) continue;
				entries.add(new ProjectEntry(null, placement, placement.getSchematic()));
			}
			for (LitematicaSchematic schematic : SchematicHolder.getInstance().getAllSchematics()) {
				if (!represented.contains(schematic) && !this.gui.sharing.isSharedSchematic(schematic)) {
					entries.add(new ProjectEntry(null, null, schematic));
				}
			}
			entries.sort(Comparator.comparing((ProjectEntry entry) -> !entry.focused(this.gui.sharing))
					.thenComparingInt(ProjectEntry::kind)
					.thenComparing(ProjectEntry::name, String.CASE_INSENSITIVE_ORDER));
			return entries;
		}

		@Override
		protected List<String> getEntryStringsForFilter(ProjectEntry entry) {
			List<String> values = new ArrayList<>();
			values.add(entry.name());
			if (entry.project() != null) {
				values.add(entry.project().ownerName());
				values.add(entry.project().dimension());
				values.add(ProjectStatusPresentation.label(entry.project().status()));
				values.add("server shared project");
			} else if (entry.schematic() != null) {
				values.add(String.valueOf(entry.schematic().getFile()));
				values.add("local");
			}
			return values;
		}

		@Override protected boolean onEntryClicked(@Nullable ProjectEntry entry, int index) {
			if (entry != null) this.gui.open(entry);
			return true;
		}

		@Override protected Entry createListEntryWidget(int x, int y, int index, boolean odd,
				@Nullable ProjectEntry entry) {
			return new Entry(x, y, this.browserEntryWidth, this.browserEntryHeight, odd, entry, index,
					this.gui.sharing);
		}
	}

	static final class Entry extends WidgetListEntryBase<ProjectEntry> {
		@Nullable private final ProjectEntry entry;
		private final boolean odd;
		private final ClientShareManager sharing;

		Entry(int x, int y, int width, int height, boolean odd, @Nullable ProjectEntry entry,
				int index, ClientShareManager sharing) {
			super(x, y, width, height, entry, index);
			this.entry = entry;
			this.odd = odd;
			this.sharing = sharing;
		}

		@Override public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
			int background = selected || this.isMouseOver(mouseX, mouseY) ? 0xA0707070
					: this.odd ? 0xA0101010 : 0xA0303030;
			RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, background);
			if (this.entry != null) {
				boolean focused = this.entry.focused(this.sharing);
				this.drawString(ctx, this.x + 6, this.y + 5, focused ? 0xFF55FF55 : 0xFFFFFFFF,
						(focused ? "\u2605 " : "") + this.entry.name());
				String detail;
				String status;
				int statusColor;
				SharedProjectView project = this.entry.project();
				if (project != null) {
					detail = StringUtils.translate("logisticmatica.gui.projects.server_detail",
							project.ownerName(), project.dimension(), project.x(), project.y(), project.z(),
							project.containerCount());
					String workspaceStatus = project.pendingInvite() ? StringUtils.translate("logisticmatica.gui.share.pending")
							: project.accessRequested() ? StringUtils.translate("logisticmatica.gui.share.request_sent")
							: !project.can(SharePermission.VIEW)
									? StringUtils.translate("logisticmatica.gui.share.request_access")
									: StringUtils.translate(this.sharing.isFocused(project.id())
											? "logisticmatica.gui.share.status.focused"
											: this.sharing.isLoaded(project.id())
													? "logisticmatica.gui.share.status.loaded"
													: "logisticmatica.gui.share.status.not_loaded");
					status = ProjectStatusPresentation.label(project.status()) + "  |  " + workspaceStatus;
					statusColor = ProjectStatusPresentation.color(project.status());
				} else {
					Path file = this.entry.schematic() != null ? this.entry.schematic().getFile() : null;
					String filename = file != null ? String.valueOf(file.getFileName())
							: StringUtils.translate("logisticmatica.gui.share.unsaved");
					detail = StringUtils.translate(this.entry.placement() != null
							? "logisticmatica.gui.projects.local_placement"
							: "logisticmatica.gui.projects.local_schematic", filename);
					status = StringUtils.translate("logisticmatica.gui.projects.local");
					statusColor = focused ? 0xFF55FF55 : 0xFFAAAAAA;
				}
				this.drawString(ctx, this.x + 6, this.y + 20, 0xFFAAAAAA, detail);
				this.drawString(ctx, this.x + this.width - this.getStringWidth(status) - 8,
						this.y + 5, statusColor, status);
			}
			super.render(ctx, mouseX, mouseY, selected);
		}
	}
}
