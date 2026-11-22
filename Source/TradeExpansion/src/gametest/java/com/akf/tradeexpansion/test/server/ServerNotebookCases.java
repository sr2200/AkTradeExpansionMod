package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.network.NotebookRequestC2S;
import com.akf.tradeexpansion.network.NotebookS2C;
import com.akf.tradeexpansion.notebook.LedgerService;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/** Checklist 40: the merchant's notebook and the morning report (design T, v0.81). */
final class ServerNotebookCases {
	private static final long DAY = PriceModel.TICKS_PER_DAY;
	/** Ticks for the once-a-second check of the morning report to run. */
	private static final int REPORT_WAIT = 25;
	private static final String HEADER = "notebook.ak_trade_expansion.report.header";
	private static final String HEADER_DAYS = "notebook.ak_trade_expansion.report.header_days";
	private static final String HINT = "notebook.ak_trade_expansion.report.hint";

	private ServerNotebookCases() {
	}

	/** The notebook the server sends {@code player} now. */
	static NotebookS2C notebook(ServerCtx c, ServerPlayer player) {
		c.forget(player);
		LedgerService.send(player, false);
		List<NotebookS2C> list = c.payloads(player, NotebookS2C.class);
		that(!list.isEmpty(), "no notebook was sent");
		return list.getLast();
	}

	static NotebookS2C.DayView today(NotebookS2C book) {
		return book.days().stream().filter(d -> d.day() == book.today()).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("no page for today (day " + book.today() + "): " + book.days()));
	}

	/** Buys {@code qty} and checks it went through. */
	private static long buy(ServerCtx c, ServerPlayer player, Market market, Identifier good, int qty) {
		long total = c.trade(player, market, good, true, qty);
		that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying %d %s gave %s", qty, good, c.lastTradeResult(player));
		return total;
	}

	private static long sell(ServerCtx c, ServerPlayer player, Market market, Identifier good, int qty) {
		long total = c.trade(player, market, good, false, qty);
		that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling %d %s gave %s", qty, good, c.lastTradeResult(player));
		return total;
	}

	private static void addDays(ServerCtx c, ServerPlayer player, int days) {
		c.command(player, "time add " + days * DAY);
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(full("notebook_recipe", List.of("40-1"), ctx -> {
			ItemStack out = ServerCases.craft(ctx, Items.BOOK, ModItems.COPPER_COIN, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR);
			that(out.is(ModItems.MERCHANT_NOTEBOOK), "a book and a copper coin gave %s", out);
		}));
		cases.add(partial("notebook_opens", List.of("40-2"), "手帳を使うと、サーバーから「画面を開く」の印のついた手帳の中身が届くこと（画面とタブの表示は見ていない）",
				ctx -> {
					ServerPlayer player = ctx.player(ctx.center());
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MERCHANT_NOTEBOOK));
					ctx.forget(player);
					ModItems.MERCHANT_NOTEBOOK.use(ctx.level(), player, InteractionHand.MAIN_HAND);
					List<NotebookS2C> sent = ctx.payloads(player, NotebookS2C.class);
					that(!sent.isEmpty() && sent.getLast().open(), "using the notebook sent %s", sent);
				}));
		cases.add(partial("notebook_profit", List.of("40-3"),
				"市場で4個買って、自分で集めた2個と合わせて6個売ると、手帳の「今日」の売上・仕入れが取引の額になり、利益が「売った額 − 買った4個の値段」"
						+ "（集めた2個は仕入れ0）になり、よく儲かった品にその品が6個で出ること（画面の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Identifier good = c.tradableGood(market, 4, 6);
					c.giveMoney(player, 100_000);
					long paid = buy(c, player, market, good, 4);
					ServerCases.giveItems(player, c.item(good), 2);
					long got = sell(c, player, market, good, 6);
					NotebookS2C.DayView today = today(notebook(c, player));
					equal(got, today.sales(), "sales");
					equal(paid, today.purchases(), "purchases");
					equal(got - paid, today.totalProfit(), "profit (sold 6 for " + got + "c, 4 of them bought for " + paid + "c)");
					equal(2, today.trades(), "trades");
					if (got - paid > 0) {
						that(!today.top().isEmpty() && today.top().getFirst().items() == 6 && today.top().getFirst().profit() == got - paid,
								"most profitable goods: %s", today.top());
					}
				})));
		cases.add(partial("morning_report", List.of("40-6", "40-7", "40-8"),
				"売り買いした次の朝（/time add で1日進めた）にチャットに日報（見出し・売上と利益・手帳の作り方の案内）が出て、何もしなかった日の次の朝は出ないこと。"
						+ "手帳の「受け取らない」の要求の後は売っても次の朝に出ず、「受け取る」に戻して3日進めると「この3日間の商い」でまとめて出ること"
						+ "（日が昇った時・寝て起きた時の見え方と、設定 OFF の時のボタンは見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Identifier good = c.tradableGood(market, 0, 4);
					ServerCases.giveItems(player, c.item(good), 4);
					c.later(REPORT_WAIT, c2 -> { // the first check notes today as seen
						sell(c2, player, market, good, 1);
						c2.forget(player);
						addDays(c2, player, 1);
						c2.later(REPORT_WAIT, c3 -> {
							that(c3.gotMessage(player, HEADER), "no morning report after a day of trading: %s", c3.messages(player));
							that(c3.gotMessage(player, "notebook.ak_trade_expansion.report.totals"), "no totals line: %s", c3.messages(player));
							that(c3.gotMessage(player, HINT), "no hint about the notebook (the player has none): %s", c3.messages(player));
							c3.forget(player);
							addDays(c3, player, 1);
							c3.later(REPORT_WAIT, c4 -> {
								that(!c4.gotMessage(player, HEADER) && !c4.gotMessage(player, HEADER_DAYS), "a report after a day without trading: %s",
										c4.messages(player));
								// Turned off in the notebook.
								player.getInventory().add(new ItemStack(ModItems.MERCHANT_NOTEBOOK));
								LedgerService.handle(player, new NotebookRequestC2S(NotebookRequestC2S.REPORT_OFF));
								that(!notebook(c4, player).reportOn(), "the switch did not turn off");
								sell(c4, player, market, good, 1);
								c4.forget(player);
								addDays(c4, player, 1);
								c4.later(REPORT_WAIT, c5 -> {
									that(!c5.gotMessage(player, HEADER), "a report although turned off: %s", c5.messages(player));
									LedgerService.handle(player, new NotebookRequestC2S(NotebookRequestC2S.REPORT_ON));
									that(notebook(c5, player).reportOn(), "the switch did not turn on");
									sell(c5, player, market, good, 1);
									c5.forget(player);
									addDays(c5, player, 3); // away: one report for the three days
									c5.later(REPORT_WAIT, c6 -> {
										that(c6.gotMessage(player, HEADER_DAYS), "no report of several days: %s", c6.messages(player));
										that(!c6.gotMessage(player, HINT), "the hint although the player has a notebook: %s", c6.messages(player));
									});
								});
							});
						});
					});
				})));
		cases.add(partial("price_notes", List.of("40-9", "40-10"),
				"交易画面を開くと、その市場の買値・売値（評判の効果込み、取引画面と同じ値）が今日の日付で手帳の価格メモの先頭に入り、"
						+ "同じ市場をもう一度開いても1件のままで、notebook.priceNotes を 0 にすると書き留めないこと（画面の表示・色・方角は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Identifier good = c.tradableGood(market, 1, 1);
					NotebookS2C book = notebook(c, player);
					that(!book.notes().isEmpty() && book.notes().getFirst().market() == market.id(), "price notes: %s", book.notes());
					NotebookS2C.NoteView note = book.notes().getFirst();
					equal(book.today(), note.day(), "day of the note");
					NotebookS2C.PriceView price = note.prices().stream().filter(p -> p.goods().equals(good)).findFirst()
							.orElseThrow(() -> new AutotestCheck.Failed(good + " is not on the note"));
					equal(c.buyPrice(player, market, good), price.buy(), "buy price on the note");
					equal(c.sellPrice(player, market, good), price.sell(), "sell price on the note");
					player.closeContainer();
					c.openStall(player, stall);
					equal(1, (int) notebook(c, player).notes().stream().filter(n -> n.market() == market.id()).count(), "notes of the market");
					c.config(cfg -> cfg.notebook.priceNotes = 0);
					ServerPlayer other = c.player(stall.south(3));
					c.openStall(other, stall);
					that(notebook(c, other).notes().isEmpty(), "noted with notebook.priceNotes = 0");
				})));
		return cases;
	}
}
