package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.test.AutotestReport;
import com.akf.tradeexpansion.test.CaseResult;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Server game test (runGameTest, entrypoint "fabric-gametest"). One game test runs every {@link ServerCases} case in
 * turn, so the cases never share markets (game tests of one batch run at the same time, and markets are world-wide).
 * Each case gets a clean area and the default config; its result goes to build/autotest/server-results.json.
 * System property {@code ak.autotest.only}: run only the cases whose name or checklist number contains one of the
 * words separated by "," or "+".
 */
public class ServerChecklistTests {
	@GameTest(maxTicks = 40000, skyAccess = true)
	public void checklist(GameTestHelper helper) {
		new Runner(helper, filter(ServerCases.all())).next();
	}

	private static List<ServerCase> filter(List<ServerCase> cases) {
		String only = System.getProperty("ak.autotest.only");
		if (only == null || only.isBlank()) return cases;
		List<String> words = List.of(only.split("[,+]"));
		return cases.stream().filter(c -> words.stream().map(String::trim).filter(w -> !w.isEmpty())
				.anyMatch(w -> c.name().contains(w) || c.checklist().contains(w))).toList();
	}

	private static final class Runner {
		private final GameTestHelper helper;
		private final List<ServerCase> cases;
		private final TradeExpansionConfig defaults;
		private final List<CaseResult> results = new ArrayList<>();
		private int index;

		Runner(GameTestHelper helper, List<ServerCase> cases) {
			this.helper = helper;
			this.cases = cases;
			this.defaults = ConfigManager.copy(TradeExpansionConfig.get());
		}

		void next() {
			if (index >= cases.size()) {
				finish();
				return;
			}
			ServerCase c = cases.get(index++);
			CaseResult.Builder result = new CaseResult.Builder(c.name(), c.checklist(), c.partial());
			ServerCtx ctx;
			try {
				ctx = new ServerCtx(helper, defaults);
			} catch (Throwable t) {
				results.add(result.fail(t));
				helper.runAfterDelay(1, this::next);
				return;
			}
			ctx.steps.add(new ServerCtx.Step(1, c.body()));
			step(ctx, 0, result);
		}

		private void step(ServerCtx ctx, int i, CaseResult.Builder result) {
			ServerCtx.Step s = ctx.steps.get(i);
			helper.runAfterDelay(Math.max(1, s.delay()), () -> {
				try {
					s.action().run(ctx);
				} catch (Throwable t) {
					AutotestReport.LOGGER.error("Case {} failed", result.name(), t);
					end(ctx, result.fail(t));
					return;
				}
				if (i + 1 < ctx.steps.size()) {
					step(ctx, i + 1, result);
				} else {
					end(ctx, result.pass());
				}
			});
		}

		private void end(ServerCtx ctx, CaseResult result) {
			results.add(result);
			try {
				ctx.cleanUp();
			} catch (Throwable t) {
				AutotestReport.LOGGER.error("Clean-up after {} failed", result.name(), t);
			}
			helper.runAfterDelay(2, this::next);
		}

		private void finish() {
			AutotestReport.write("server", results);
			long failed = results.stream().filter(r -> !r.ok()).count();
			if (failed > 0) {
				helper.fail(failed + " of " + results.size() + " checklist cases failed (see build/autotest/server-results.json)");
			} else {
				helper.succeed();
			}
		}
	}
}
