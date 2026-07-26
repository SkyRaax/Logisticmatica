package com.skyraax.logisticmatica.mixin.litematica;

import java.nio.file.Path;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

import com.skyraax.logisticmatica.client.ISharedPlacement;

/** Assigns a server project identity without removing and recreating the rendered placement. */
@Mixin(value = SchematicPlacement.class, remap = false)
public class MixinSchematicPlacement implements ISharedPlacement {
	@Shadow @Final @Mutable private UUID hashId;
	@Shadow @Final @Mutable private Path schematicFile;

	@Override
	public void logisticmatica$bindToProject(UUID projectId, Path authoritativeFile) {
		this.hashId = projectId;
		this.schematicFile = authoritativeFile;
	}
}
