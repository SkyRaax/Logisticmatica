package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.util.StringUtils;

import com.skyraax.logisticmatica.share.ProjectStatus;

/** Localized labels and consistent colours for the shared project phase. */
public final class ProjectStatusPresentation {
	private ProjectStatusPresentation() {}

	public static String label(ProjectStatus status) {
		return StringUtils.translate(status.translationKey());
	}

	public static int color(ProjectStatus status) {
		return switch (status) {
			case PLANNING -> 0xFFAAAAAA;
			case COLLECTING -> 0xFFFFAA00;
			case READY_TO_BUILD -> 0xFF55FFFF;
			case BUILDING -> 0xFF55FF55;
			case PAUSED -> 0xFFFFFF55;
			case BLOCKED -> 0xFFFF5555;
			case COMPLETED -> 0xFFFF55FF;
		};
	}
}
