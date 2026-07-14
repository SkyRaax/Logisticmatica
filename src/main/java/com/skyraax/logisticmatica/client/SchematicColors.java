package com.skyraax.logisticmatica.client;

import fi.dy.masa.malilib.util.data.Color4f;

/**
 * Assigns each schematic a stable, distinct colour derived from its key, so tracked containers can be
 * colour-coded per schematic in the world and it stays obvious which container belongs to which one.
 */
public final class SchematicColors {
	private SchematicColors() {
	}

	/** A saturated colour deterministically derived from the schematic key. */
	public static Color4f forKey(String key) {
		float hue = Math.floorMod(key.hashCode(), 360) / 360.0f;
		return hsvToColor(hue, 0.65f, 1.0f);
	}

	/** The same colour as a packed ARGB int (opaque), for text/GUI use. */
	public static int argb(String key) {
		Color4f c = forKey(key);
		return (0xFF << 24)
				| ((int) (c.r * 255.0f) << 16)
				| ((int) (c.g * 255.0f) << 8)
				| (int) (c.b * 255.0f);
	}

	private static Color4f hsvToColor(float h, float s, float v) {
		int i = (int) (h * 6.0f);
		float f = h * 6.0f - i;
		float p = v * (1.0f - s);
		float q = v * (1.0f - f * s);
		float t = v * (1.0f - (1.0f - f) * s);

		return switch (i % 6) {
			case 0 -> new Color4f(v, t, p, 1.0f);
			case 1 -> new Color4f(q, v, p, 1.0f);
			case 2 -> new Color4f(p, v, t, 1.0f);
			case 3 -> new Color4f(p, q, v, 1.0f);
			case 4 -> new Color4f(t, p, v, 1.0f);
			default -> new Color4f(v, p, q, 1.0f);
		};
	}
}
