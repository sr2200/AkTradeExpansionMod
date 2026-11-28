package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.NotebookScreen;
import com.akf.tradeexpansion.network.NotebookS2C;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.LedgerMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.notebook.Ledger;
import com.akf.tradeexpansion.notebook.LedgerService;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/** Client cases of the merchant's notebook (checklist 40, design T v0.81). */
final class ClientNotebookCases {
	private ClientNotebookCases() {
	}

	private static final String S = "screen.ak_trade_expansion.notebook.";
	private static final String[] TABS = {"today", "yesterday", "days", "prices"};
	private static final int[][] SIZES = {{854, 480, 0}, {1920, 1080, 2}, {1920, 1080, 4}, {1920, 1080, 0}};

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static void resize(ClientGameTestContext context, int width, int height, int guiScale) {
		context.getInput().resizeWindow(width, height);
		context.runOnClient(client -> {
			client.options.guiScale().set(guiScale);
			client.resizeGui();
		});
		context.waitTicks(3);
	}

	/**
	 * 40-2 / 40-12: a notebook filled with a week of takings (the longest goods names) and the price notes of the session's
	 * market opens with the use key; on each page the figures are shown and no text overlaps another, covers a button or
	 * leaves the page (Japanese and English, every size).
	 */
	static void screen(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		long[] sales = new long[3]; // today's sales, yesterday's, today
		s.world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Market market = market(server, s.stall);
			LedgerService.notePrices(player, market, MarketUpkeep.touch(player.level(), market));
			Ledger ledger = Ledger.get(server);
			Ledger.Book book = ledger.book(player.getUUID());
			long today = LedgerService.today(server);
			int keep = TradeExpansionConfig.get().notebook.keepDays;
			// The goods with the longest names, so that cutting and shrinking are seen.
			List<Identifier> goods = new ArrayList<>(GoodsCatalog.all().keySet());
			goods.sort((a, b) -> Integer.compare(name(b).length(), name(a).length()));
			for (long d = today - keep; d <= today; d++) { // oldest first: a page drops the days after it
				LedgerMath.Day day = book.day(d, keep);
				day.bought(123_456);
				for (int i = 0; i < 6; i++) day.sold(goods.get(i).toString(), 64 + i, 98_765 + i * 1000L, 12_345);
				day.quest(4_321, 0);
				day.staff(d % 2 == 0 ? 7_654 : -321);
				if (d == today - 1) {
					day.sold(goods.get(6).toString(), 1, 5_555, 0); // yesterday differs from today
					sales[1] = day.sales(); // not book.day(today - 1, …) later: it drops the days after it
				}
			}
			sales[0] = book.day(today, keep).sales();
			sales[2] = today;
			ledger.setDirty();
			player.getInventory().setItem(8, new ItemStack(ModItems.MERCHANT_NOTEBOOK));
		});
		float[] rot = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot()});
		List<String> problems = new ArrayList<>();
		try {
			context.setScreen(() -> null);
			context.waitTicks(3);
			context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
			context.getInput().lookAt(rot[0], -90); // the sky: no block in reach
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(NotebookScreen.class);
			context.waitTicks(3);
			mouseAway(context);
			List<String> shown = strings(TextRecorder.capture(context));
			String salesText = CoinItem.formatCopper(sales[0]);
			check(shown.contains(salesText), "the page of today does not show the sales %s: %s", salesText, shown);
			screenshot(context, result, "40-2_notebook_today");
			// 40-15: the page of yesterday shows yesterday's figures (on the first day: that there is no yesterday yet),
			// without the morning report switch.
			clickWidget(context, text(S + "tab.yesterday"));
			context.waitTicks(2);
			mouseAway(context);
			List<String> yesterday = strings(TextRecorder.capture(context));
			if (sales[2] >= 1) {
				String yesterdaySales = CoinItem.formatCopper(sales[1]);
				check(yesterday.contains(yesterdaySales), "the page of yesterday does not show the sales %s: %s", yesterdaySales, yesterday);
			} else {
				String none = text(S + "yesterday.none");
				check(String.join("", yesterday).contains(none.substring(0, 6)), "the page of yesterday on the first day does not say there is none: %s", yesterday);
			}
			check(!yesterday.contains(text(S + "report_on")) && !yesterday.contains(text(S + "report_off")),
					"the page of yesterday shows the morning report switch: %s", yesterday);
			// The same page one day later (the world's clock is shared by the cases, so the notebook is shifted instead):
			// today's figures are yesterday's.
			NotebookS2C original = context.computeOnClient(c -> (NotebookS2C) get(c.gui.screen(), "data"));
			NotebookS2C nextDay = new NotebookS2C(false, original.reportOn(), original.reportEnabled(),
					original.today() + 1, original.days(), original.notes(), original.staff(), original.lectern());
			context.runOnClient(c -> ((NotebookScreen) c.gui.screen()).update(nextDay));
			context.waitTicks(2);
			List<String> shifted = strings(TextRecorder.capture(context));
			check(shifted.contains(salesText), "the page of yesterday (one day later) does not show the sales %s: %s", salesText, shifted);
			screenshot(context, result, "40-15_notebook_yesterday_next_day");
			context.runOnClient(c -> ((NotebookScreen) c.gui.screen()).update(original));
			context.waitTicks(2);
			screenshot(context, result, "40-15_notebook_yesterday");

			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					resize(context, size[0], size[1], size[2]);
					for (String tab : TABS) {
						String label = text(S + "tab." + tab);
						boolean current = context.computeOnClient(c -> String.valueOf(get(c.gui.screen(), "tab")).equalsIgnoreCase(tab));
						if (!current) clickWidget(context, label);
						// The page of yesterday is looked at one day later, full of figures and long names (on the first day it
						// is one line only).
						if (tab.equals("yesterday")) context.runOnClient(c -> ((NotebookScreen) c.gui.screen()).update(nextDay));
						context.waitTicks(2);
						mouseAway(context);
						String where = String.format("notebook %s %s %dx%d GUI %s", tab, language, size[0], size[1], size[2] == 0 ? "auto" : size[2]);
						List<TextRecorder.Text> now = TextRecorder.capture(context);
						List<String> found = context.computeOnClient(c -> ClientChecklistTests.layoutProblems(c, where, now, panel(c.gui.screen())));
						if (!found.isEmpty() || size[0] == 854) screenshot(context, result, "layout_" + where);
						problems.addAll(found);
						if (tab.equals("yesterday")) context.runOnClient(c -> ((NotebookScreen) c.gui.screen()).update(original));
					}
				}
			}
			check(context.computeOnClient(c -> c.gui.screen()) instanceof NotebookScreen, "the notebook closed while resizing");
		} finally {
			resize(context, 854, 480, 0);
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
			context.setScreen(() -> null);
			context.getInput().lookAt(rot[0], rot[1]);
			s.world.getServer().runOnServer(server -> player(server).getInventory().setItem(8, ItemStack.EMPTY));
			context.waitTicks(2);
		}
		check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
	}

	private static String name(Identifier goods) {
		var def = GoodsCatalog.get(goods);
		return def == null ? "" : new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(def.item())).getHoverName().getString();
	}

	private static LayoutCheck.Panel panel(Object screen) {
		int left = (int) get(screen, "left");
		int top = (int) get(screen, "top");
		return new LayoutCheck.Panel(left, top, left + (int) get(screen, "panelW"), top + (int) get(screen, "panelH"));
	}
}
