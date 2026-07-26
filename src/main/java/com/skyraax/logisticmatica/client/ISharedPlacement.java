package com.skyraax.logisticmatica.client;

import java.nio.file.Path;
import java.util.UUID;

/** Lets the sharing bridge preserve a live placement while assigning its persistent server identity. */
public interface ISharedPlacement {
	void logisticmatica$bindToProject(UUID projectId, Path authoritativeFile);
}
