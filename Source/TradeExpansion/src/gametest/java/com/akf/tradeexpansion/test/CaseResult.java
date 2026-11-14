package com.akf.tradeexpansion.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of one automated check, matched to items of docs/release/ingame-checklist.md by tools/checklist/record.js.
 *
 * @param name        short English name of the case (shown in the report)
 * @param checklist   checklist item numbers the case covers, e.g. "2-10"
 * @param partial     {@code null} when the case covers the whole item; otherwise what it covers (Japanese, shown in the
 *                    checklist), and the item is not ticked
 * @param ok          whether the case passed
 * @param message     failure reason, or {@code null}
 * @param screenshots screenshot files taken by the case (paths relative to build/autotest), for a person to look at
 */
public record CaseResult(String name, List<String> checklist, String partial, boolean ok, String message, List<String> screenshots) {
	public CaseResult {
		checklist = List.copyOf(checklist);
		screenshots = List.copyOf(screenshots);
	}

	/** Mutable builder used while a case runs. */
	public static final class Builder {
		private final String name;
		private final List<String> checklist;
		private final String partial;
		private final List<String> screenshots = new ArrayList<>();

		public Builder(String name, List<String> checklist, String partial) {
			this.name = name;
			this.checklist = checklist;
			this.partial = partial;
		}

		public String name() {
			return name;
		}

		public void screenshot(String path) {
			screenshots.add(path);
		}

		public CaseResult pass() {
			return new CaseResult(name, checklist, partial, true, null, screenshots);
		}

		public CaseResult fail(Throwable error) {
			String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
			return new CaseResult(name, checklist, partial, false, message, screenshots);
		}
	}
}
