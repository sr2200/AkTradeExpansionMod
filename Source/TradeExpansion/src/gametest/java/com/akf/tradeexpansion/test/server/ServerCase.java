package com.akf.tradeexpansion.test.server;

import java.util.List;

/**
 * One server-side automated check of docs/release/ingame-checklist.md.
 *
 * @param name      short English name
 * @param checklist checklist item numbers it covers
 * @param partial   {@code null} if it covers the whole item, otherwise what it covers (Japanese)
 * @param body      the check; it may queue later steps with {@link ServerCtx#later}
 */
public record ServerCase(String name, List<String> checklist, String partial, Body body) {
	@FunctionalInterface
	public interface Body {
		void run(ServerCtx ctx) throws Exception;
	}

	public static ServerCase full(String name, List<String> checklist, Body body) {
		return new ServerCase(name, checklist, null, body);
	}

	public static ServerCase partial(String name, List<String> checklist, String covers, Body body) {
		return new ServerCase(name, checklist, covers, body);
	}
}
