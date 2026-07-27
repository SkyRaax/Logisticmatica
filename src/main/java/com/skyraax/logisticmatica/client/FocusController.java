package com.skyraax.logisticmatica.client;

import java.util.UUID;

import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.scheduler.ITask;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.skyraax.logisticmatica.client.share.ClientShareManager;

/** Applies one explicit Logisticmatica focus to Litematica's active material list. */
public final class FocusController {
	@Nullable private static ITask pendingTask;

	private FocusController() {
	}

	public static void clear() {
		clearTransient();
		ClientShareManager.getInstance().onFocusChanged(null);
	}

	/** Clears runtime-only state without erasing the remembered project of a disconnected server. */
	public static void clearTransient() {
		cancelPendingTask();
		FocusState.clear();
		DataManager.setMaterialList(null);
	}

	public static boolean focusPlacement(SchematicPlacement placement) {
		if (FocusState.getPlacement() == placement && FocusState.getProjectId() == null) return false;
		FocusState.setPlacement(placement);
		ClientShareManager.getInstance().onFocusChanged(null);
		return applyPlacement(placement);
	}

	public static boolean focusSharedPlacement(SchematicPlacement placement, UUID projectId) {
		if (FocusState.getPlacement() == placement && projectId.equals(FocusState.getProjectId())) return false;
		FocusState.setSharedPlacement(placement, projectId);
		ClientShareManager.getInstance().onFocusChanged(projectId);
		return applyPlacement(placement);
	}

	public static boolean focusSchematic(LitematicaSchematic schematic) {
		if (FocusState.getPlacement() == null && FocusState.getSchematic() == schematic) return false;
		cancelPendingTask();
		FocusState.setSchematic(schematic);
		MaterialListSchematic materialList = new MaterialListSchematic(schematic, false);
		materialList.reCreateMaterialList();
		ClientShareManager.getInstance().onFocusChanged(null);
		DataManager.setMaterialList(materialList);
		return true;
	}

	public static boolean isFocused(SchematicPlacement placement) {
		return FocusState.getPlacement() == placement;
	}

	/** Refreshes the active list with a Logisticmatica-specific progress message. */
	public static boolean refreshFocusedMaterials() {
		SchematicPlacement placement = FocusState.getPlacement();
		if (placement != null) return applyPlacement(placement);
		LitematicaSchematic schematic = FocusState.getSchematic();
		if (schematic == null) return false;
		MaterialListSchematic materialList = new MaterialListSchematic(schematic, false);
		materialList.reCreateMaterialList();
		DataManager.setMaterialList(materialList);
		InfoUtils.showGuiOrInGameMessage(MessageType.INFO,
				"logisticmatica.focus.materials_refreshed", schematic.getMetadata().getName());
		return true;
	}

	private static boolean applyPlacement(SchematicPlacement placement) {
		cancelPendingTask();
		MaterialListBase materialList = placement.getMaterialList();
		DataManager.setMaterialList(materialList);
		TaskCountBlocksPlacement task = new TaskCountBlocksPlacement(placement, materialList,
				Configs.Generic.MATERIAL_LIST_IGNORE_STATE.getBooleanValue());
		task.disableCompletionMessage();
		task.setCompletionListener(new ICompletionListener() {
			@Override
			public void onTaskCompleted() {
				if (pendingTask == task) pendingTask = null;
			}

			@Override
			public void onTaskAborted() {
				if (pendingTask == task) pendingTask = null;
			}
		});
		pendingTask = task;
		TaskScheduler.getInstanceClient().scheduleTask(task, 20);
		InfoUtils.showGuiOrInGameMessage(MessageType.INFO,
				"logisticmatica.focus.materials_refreshing", placement.getName());
		return true;
	}

	private static void cancelPendingTask() {
		ITask task = pendingTask;
		pendingTask = null;
		if (task != null) TaskScheduler.getInstanceClient().removeTask(task);
	}
}
