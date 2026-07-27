package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.UUID;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;

/** Lets the sharing bridge preserve a live placement while assigning its persistent server identity. */
public interface ISharedPlacement {
	void logisticmatica$bindToProject(UUID projectId, Path authoritativeFile);

	void logisticmatica$detachToLocal(UUID placementId, Path localFile, LitematicaSchematic schematic);
}
