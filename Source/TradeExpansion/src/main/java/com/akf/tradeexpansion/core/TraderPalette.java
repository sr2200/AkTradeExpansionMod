package com.akf.tradeexpansion.core;

/**
 * Robe colours of the trader NPC (design A-5, v0.60). The client recolours the wandering trader's texture: only the
 * blue robe pixels change, so skin, trim and resource-pack details stay as drawn. Variant 0 is the original blue.
 */
public final class TraderPalette {
	/** Hue of the original robe (degrees). */
	static final float SOURCE_HUE = 218f;
	/** Robe pixels: hue within this distance of {@link #SOURCE_HUE} and saturation at least {@link #MIN_SATURATION}. */
	static final float HUE_RANGE = 30f;
	static final float MIN_SATURATION = 0.15f;

	/** {target hue (NaN = keep), saturation factor, brightness factor} per variant. */
	private static final float[][] VARIANTS = {
			{Float.NaN, 1f, 1f},       // 0 blue (original)
			{356f, 1.15f, 1.2f},       // 1 red
			{125f, 0.9f, 1.05f},       // 2 green
			{275f, 1f, 1.15f},         // 3 purple
			{28f, 1.05f, 1.3f},        // 4 brown
			{178f, 1f, 1.05f},         // 5 teal
			{Float.NaN, 0.1f, 1.25f},  // 6 grey
			{Float.NaN, 0.2f, 0.6f},   // 7 black
	};
	public static final int VARIANT_COUNT = VARIANTS.length;

	private TraderPalette() {
	}

	/** Out-of-range variants (old saves, bad data) fall back to the original colour. */
	public static int clamp(int variant) {
		return variant >= 0 && variant < VARIANT_COUNT ? variant : 0;
	}

	/** Recolours one ARGB pixel for {@code variant}. Non-robe and transparent pixels are returned unchanged. */
	public static int recolor(int argb, int variant) {
		variant = clamp(variant);
		if (variant == 0 || (argb >>> 24) == 0) return argb;
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		float[] hsv = toHsv(r, g, b);
		if (!isRobe(hsv)) return argb;
		float[] v = VARIANTS[variant];
		float hue = Float.isNaN(v[0]) ? hsv[0] : v[0] + (hsv[0] - SOURCE_HUE);
		float sat = clamp01(hsv[1] * v[1]);
		float val = clamp01(hsv[2] * v[2]);
		return (argb & 0xFF000000) | toRgb(hue, sat, val);
	}

	static boolean isRobe(float[] hsv) {
		float d = Math.abs(hsv[0] - SOURCE_HUE);
		return Math.min(d, 360f - d) <= HUE_RANGE && hsv[1] >= MIN_SATURATION;
	}

	/** RGB (0–255) to {hue 0–360, saturation 0–1, value 0–1}. */
	static float[] toHsv(int r, int g, int b) {
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue;
		if (delta == 0) hue = 0;
		else if (max == r) hue = 60f * (((g - b) / delta) % 6f);
		else if (max == g) hue = 60f * ((b - r) / delta + 2f);
		else hue = 60f * ((r - g) / delta + 4f);
		if (hue < 0) hue += 360f;
		return new float[] {hue, max == 0 ? 0 : delta / max, max / 255f};
	}

	/** {hue, saturation, value} to RGB packed as 0xRRGGBB. */
	static int toRgb(float hue, float sat, float val) {
		hue = ((hue % 360f) + 360f) % 360f;
		float c = val * sat;
		float x = c * (1 - Math.abs((hue / 60f) % 2f - 1));
		float m = val - c;
		float r, g, b;
		if (hue < 60) { r = c; g = x; b = 0; }
		else if (hue < 120) { r = x; g = c; b = 0; }
		else if (hue < 180) { r = 0; g = c; b = x; }
		else if (hue < 240) { r = 0; g = x; b = c; }
		else if (hue < 300) { r = x; g = 0; b = c; }
		else { r = c; g = 0; b = x; }
		return (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
	}

	private static int channel(float v) {
		return Math.round(clamp01(v) * 255f);
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
