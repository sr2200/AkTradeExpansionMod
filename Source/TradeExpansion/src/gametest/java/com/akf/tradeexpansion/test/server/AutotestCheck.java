package com.akf.tradeexpansion.test.server;

/** Checks used by the server cases; a failed check ends the case with its message. */
public final class AutotestCheck {
	private AutotestCheck() {
	}

	public static final class Failed extends RuntimeException {
		public Failed(String message) {
			super(message);
		}
	}

	public static void that(boolean condition, String message, Object... args) {
		if (!condition) throw new Failed(args.length == 0 ? message : String.format(message, args));
	}

	public static void equal(long expected, long actual, String what) {
		if (expected != actual) throw new Failed(what + ": expected " + expected + " but was " + actual);
	}
}
