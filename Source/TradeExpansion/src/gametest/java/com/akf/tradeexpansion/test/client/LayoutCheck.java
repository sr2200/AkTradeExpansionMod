package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.test.client.TextRecorder.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds layout problems in the texts of one frame: two texts of the same layer that overlap (by at least 2 x 2 GUI
 * pixels), texts that leave the screen or the screen's panel, and untranslated texts (a translation key of this mod or
 * a format placeholder such as %s drawn as it is). Tooltips are another layer and are not compared with the screen
 * below (the tests move the mouse away anyway).
 */
public final class LayoutCheck {
	private LayoutCheck() {
	}

	/** Panel of a container screen in GUI pixels, or {@code null} for none. */
	public record Panel(int x0, int y0, int x1, int y1) {
	}

	private static final int MIN_OVERLAP = 2;

	/** A translation key of this mod (missing in both language files) or a placeholder left in a translated text. */
	private static final java.util.regex.Pattern UNTRANSLATED = java.util.regex.Pattern.compile(
			"(?:^|[^A-Za-z0-9_:])[a-z]+\\.ak_trade_expansion\\.[a-z0-9_.]+|%(?:\\d+\\$)?[sd]");

	/** The untranslated part of a text, or {@code null}. */
	public static String untranslated(String text) {
		java.util.regex.Matcher m = UNTRANSLATED.matcher(text);
		return m.find() ? m.group().trim() : null;
	}

	public static List<String> check(String where, List<Text> texts, int guiWidth, int guiHeight, Panel panel) {
		List<String> problems = new ArrayList<>();
		int base = TextRecorder.baseStratum(texts);
		for (Text t : texts) {
			String raw = untranslated(t.text());
			if (raw != null) problems.add(where + ": " + t + " is not translated (" + raw + ")");
			if (t.x0() < 0 || t.y0() < 0 || t.x1() > guiWidth || t.y1() > guiHeight) {
				problems.add(where + ": " + t + " leaves the screen (" + guiWidth + "x" + guiHeight + ")");
			} else if (panel != null && t.stratum() == base
					&& (t.x0() < panel.x0() || t.y0() < panel.y0() || t.x1() > panel.x1() || t.y1() > panel.y1())) {
				problems.add(where + ": " + t + " leaves the panel " + panel.x0() + "," + panel.y0() + "-" + panel.x1() + "," + panel.y1());
			}
		}
		for (int i = 0; i < texts.size(); i++) {
			Text a = texts.get(i);
			for (int j = i + 1; j < texts.size(); j++) {
				Text b = texts.get(j);
				if (a.stratum() != b.stratum()) continue;
				int w = Math.min(a.x1(), b.x1()) - Math.max(a.x0(), b.x0());
				int h = Math.min(a.y1(), b.y1()) - Math.max(a.y0(), b.y0());
				if (w < MIN_OVERLAP || h < MIN_OVERLAP) continue;
				// The same text drawn again up to 2 pixels off: an outline or shadow effect (e.g. item counts), not two texts.
				if (a.text().equals(b.text()) && Math.abs(a.x0() - b.x0()) <= 2 && Math.abs(a.y0() - b.y0()) <= 2) continue;
				problems.add(where + ": " + a + " overlaps " + b);
			}
		}
		return problems;
	}
}
