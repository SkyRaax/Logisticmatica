package com.skyraax.logisticmatica.client.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
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
import com.skyraax.logisticmatica.client.share.ClientShareManager;
import com.skyraax.logisticmatica.share.SharePermission;
import com.skyraax.logisticmatica.share.SharedProjectView;

/**
 * Lets the player choose which schematic Logisticmatica focuses on — its material list is what the
 * HUD and the list screen show — or clear the focus entirely.
 *
 * <p>Litematica sets the "active" material list implicitly, only when the player opens one from a
 * schematic's own menu, which leaves no way to switch or clear it afterwards. This screen makes that
 * choice explicit and offers both sources:
 *
 * <ul>
 *   <li><b>Placements</b> — a placement's material list compares the schematic against the blocks
 *       actually in the world, so it shows what is still <em>missing</em>. This is what you want
 *       while building.</li>
 *   <li><b>Loaded schematics</b> — a bare schematic only knows its <em>total</em> requirements.
 *       Useful for gathering materials before the schematic is placed anywhere.</li>
 * </ul>
 *
 * <p>This is also the client-side seed of the "focused build" that a shared, server-side project
 * will grow from.
 */
public class GuiFocusPicker extends GuiListBase<GuiFocusPicker.FocusEntry, GuiFocusPicker.Entry,
		GuiFocusPicker.FocusList> {
	private final ClientShareManager sharing = ClientShareManager.getInstance();
	private String filterText = "";

	public GuiFocusPicker() {
		super(10, 68);
		this.useTitleHierarchy = false;
		this.title = StringUtils.translate("logisticmatica.gui.title.focus");
	}

	@Override protected int getBrowserWidth() { return this.getScreenWidth() - 20; }
	@Override protected int getBrowserHeight() { return this.getScreenHeight() - 104; }

	@Override
	protected FocusList createListWidget(int listX, int listY) {
		FocusList list = new FocusList(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
		list.setFilterText(this.filterText);
		return list;
	}

	@Override
	public void initGui() {
		super.initGui();
		NavBar.add(this, NavBar.Tab.FOCUS);

		GuiTextFieldGeneric search = new GuiTextFieldGeneric(12, 46, 220, 16, this.font);
		search.setValueWrapper(this.filterText);
		this.addTextField(search, new SearchListener(this), TextFieldType.STRING);

		ButtonGeneric clear = new ButtonGeneric(238, 44, -1, 20,
				StringUtils.translate("logisticmatica.gui.button.focus.clear"));
		clear.setEnabled(DataManager.getMaterialList() != null || FocusState.getSchematic() != null);
		this.addButton(clear, (button, mouseButton) -> {
			FocusController.clear();
			this.initGui();
		});

		String activeLabel = StringUtils.translate("logisticmatica.gui.label.focus.active", this.activeName());
		int labelX = 238 + clear.getWidth() + 8;
		if (labelX + this.getStringWidth(activeLabel) < this.getScreenWidth() - 12) {
			this.addLabel(labelX, 49, this.getStringWidth(activeLabel), 12, 0xFFFFFFFF, activeLabel);
		}

		if (DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().isEmpty()
				&& SchematicHolder.getInstance().getAllSchematics().isEmpty()) {
			String empty = StringUtils.translate("logisticmatica.gui.label.focus.nothing_loaded");
			this.addLabel(16, 74, this.getStringWidth(empty), 12, 0xFFAAAAAA, empty);
		}

		String back = StringUtils.translate("logisticmatica.gui.button.back");
		this.addButton(new ButtonGeneric(this.getScreenWidth() - this.getStringWidth(back) - 30,
				this.getScreenHeight() - 34, this.getStringWidth(back) + 20, 20, back),
				(button, mouseButton) -> GuiBase.openGui(this.getParent()));
	}

	private String activeName() {
		SchematicPlacement placement = FocusState.getPlacement();
		if (placement != null) return placement.getName();
		LitematicaSchematic schematic = FocusState.getSchematic();
		if (schematic != null) return schematic.getMetadata().getName();
		var active = DataManager.getMaterialList();
		if (active != null) return active.getName();
		return StringUtils.translate("logisticmatica.gui.label.focus.none");
	}

	private void choose(FocusEntry entry) {
		if (entry.placement() != null) {
			SharedProjectView project = this.sharing.projectFor(entry.placement());
			if (project != null) {
				FocusController.focusSharedPlacement(entry.placement(), project.id());
			} else {
				FocusController.focusPlacement(entry.placement());
			}
		}
		else FocusController.focusSchematic(entry.schematic());
		this.initGui();
	}

	private record SearchListener(GuiFocusPicker gui) implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override public boolean onTextChange(GuiTextFieldGeneric field) {
			this.gui.filterText = field.getValueWrapper();
			FocusList list = this.gui.getListWidget();
			if (list != null) {
				list.setFilterText(this.gui.filterText);
				list.refreshEntries();
				list.resetScrollbarPosition();
			}
			return true;
		}
	}

	record FocusEntry(@Nullable SchematicPlacement placement, LitematicaSchematic schematic) {
		boolean isPlacement() { return this.placement != null; }
		String name() {
			return this.placement != null ? this.placement.getName() : this.schematic.getMetadata().getName();
		}
		boolean focused() {
			if (this.placement != null) return FocusController.isFocused(this.placement);
			return FocusState.getPlacement() == null && FocusState.getSchematic() == this.schematic;
		}
	}

	static final class FocusList extends WidgetListBase<FocusEntry, Entry> {
		private final GuiFocusPicker gui;
		private String filterText = "";

		FocusList(int x, int y, int width, int height, GuiFocusPicker gui) {
			super(x, y, width, height, null);
			this.gui = gui;
			this.browserEntryHeight = 36;
		}

		void setFilterText(String value) { this.filterText = value != null ? value : ""; }
		@Override protected boolean hasFilter() { return !this.filterText.isBlank(); }
		@Override protected String getFilterText() { return this.filterText.toLowerCase(Locale.ROOT); }

		@Override
		protected Collection<FocusEntry> getAllEntries() {
			List<FocusEntry> entries = new ArrayList<>();
			Set<LitematicaSchematic> represented = Collections.newSetFromMap(new IdentityHashMap<>());
			for (SchematicPlacement placement : DataManager.getSchematicPlacementManager()
					.getAllSchematicsPlacements()) {
				entries.add(new FocusEntry(placement, placement.getSchematic()));
				represented.add(placement.getSchematic());
			}
			for (LitematicaSchematic schematic : SchematicHolder.getInstance().getAllSchematics()) {
				if (!represented.contains(schematic) && !this.gui.sharing.isSharedSchematic(schematic))
					entries.add(new FocusEntry(null, schematic));
			}
			entries.sort(Comparator.comparing((FocusEntry entry) -> !entry.focused())
					.thenComparing(entry -> !entry.isPlacement())
					.thenComparing(FocusEntry::name, String.CASE_INSENSITIVE_ORDER));
			return entries;
		}

		@Override
		protected List<String> getEntryStringsForFilter(FocusEntry entry) {
			List<String> values = new ArrayList<>();
			values.add(entry.name());
			Path file = entry.schematic().getFile();
			values.add(String.valueOf(file));
			if (entry.placement() != null) values.add(entry.placement().getOrigin().toShortString());
			SharedProjectView project = entry.placement() != null ? this.gui.sharing.projectFor(entry.placement())
					: this.gui.sharing.projectFor(entry.schematic());
			if (project != null) {
				values.add(project.ownerName());
				values.add(project.dimension());
				values.add("shared");
			}
			return values;
		}

		@Override protected boolean onEntryClicked(@Nullable FocusEntry entry, int index) {
			if (entry != null) this.gui.choose(entry);
			return true;
		}

		@Override protected Entry createListEntryWidget(int x, int y, int index, boolean odd,
				@Nullable FocusEntry entry) {
			return new Entry(x, y, this.browserEntryWidth, this.browserEntryHeight, odd, entry, index,
					this.gui.sharing);
		}
	}

	static final class Entry extends WidgetListEntryBase<FocusEntry> {
		@Nullable private final FocusEntry entry;
		private final boolean odd;
		private final ClientShareManager sharing;

		Entry(int x, int y, int width, int height, boolean odd, @Nullable FocusEntry entry,
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
				boolean focused = this.entry.focused();
				SharedProjectView project = this.entry.placement() != null
						? this.sharing.projectFor(this.entry.placement()) : this.sharing.projectFor(this.entry.schematic());
				String name = (focused ? "\u2605 " : "") + this.entry.name();
				this.drawString(ctx, this.x + 6, this.y + 5, focused ? 0xFF55FF55 : 0xFFFFFFFF, name);

				String kind = StringUtils.translate(this.entry.isPlacement()
						? "logisticmatica.gui.focus.kind.placement"
						: "logisticmatica.gui.focus.kind.schematic");
				String detail;
				if (project != null && this.entry.placement() != null) {
					detail = this.entry.placement().getOrigin().toShortString() + "  \u00b7  "
							+ StringUtils.translate("logisticmatica.gui.focus.detail.shared", project.ownerName());
				} else {
					Path file = this.entry.schematic().getFile();
					String fileName = file != null ? String.valueOf(file.getFileName())
							: StringUtils.translate("logisticmatica.gui.share.unsaved");
					detail = kind + "  \u00b7  " + fileName;
					if (this.entry.placement() != null) {
						detail = this.entry.placement().getOrigin().toShortString() + "  \u00b7  " + detail;
					}
				}
				this.drawString(ctx, this.x + 6, this.y + 20, 0xFFAAAAAA, detail);

				String status = project != null
						? StringUtils.translate(project.can(SharePermission.MOVE)
								? focused ? "logisticmatica.gui.focus.status.focused_shared_editable"
										: "logisticmatica.gui.focus.status.shared_editable"
								: focused ? "logisticmatica.gui.focus.status.focused_shared_read_only"
										: "logisticmatica.gui.focus.status.shared_read_only")
						: focused ? StringUtils.translate("logisticmatica.gui.focus.status.focused") : "";
				if (!status.isEmpty()) {
					this.drawString(ctx, this.x + this.width - this.getStringWidth(status) - 8,
							this.y + 5, focused ? 0xFF55FF55 : 0xFF55FFFF, status);
				}
			}
			super.render(ctx, mouseX, mouseY, selected);
		}
	}
}
