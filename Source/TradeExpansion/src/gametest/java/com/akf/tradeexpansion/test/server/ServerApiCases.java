package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.api.ItemValuation;
import com.akf.tradeexpansion.api.TradeExpansionApi;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.ItemValuations;
import com.akf.tradeexpansion.trade.TradeService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.akf.tradeexpansion.api.TradeExpansionEvents;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/** Checklist 46: the API for other mods (design S). */
final class ServerApiCases {
	private ServerApiCases() {
	}

	/** What the API events delivered (listeners cannot be removed, so they are registered once and cleared per case). */
	private static final List<TradeExpansionEvents.StaffTrade> STAFF_TRADES = new ArrayList<>();
	private static final List<TradeExpansionEvents.RequestCompleted> REQUESTS = new ArrayList<>();
	private static final List<Set<Long>> CHANGED = new ArrayList<>();
	private static boolean registered;

	private static void listen() {
		if (!registered) {
			registered = true;
			TradeExpansionEvents.STAFF_TRADE.register(STAFF_TRADES::add);
			TradeExpansionEvents.REQUEST_COMPLETED.register(REQUESTS::add);
			TradeExpansionEvents.MARKETS_CHANGED.register((server, ids) -> CHANGED.add(ids));
		}
		STAFF_TRADES.clear();
		REQUESTS.clear();
		CHANGED.clear();
	}

	private static long buyPrice(MinecraftServer server, Market market, Identifier good) {
		return TradeExpansionApi.quote(server, market.id(), good).orElseThrow(() -> new AutotestCheck.Failed("no quote of " + good)).buyPrice();
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("api_staff_and_request_events", List.of("46-1"),
				"番頭が品を売ると STAFF_TRADE が来て（持ち主・番頭・売り・市場・品、合計が番頭の売上と同じ）、依頼を納品すると REQUEST_COMPLETED（GOODS・市場・品・数・報酬・現金）が来ること"
						+ "（運び屋と荷箱の配達は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					listen();
					MinecraftServer server = c.level().getServer();
					c.openStall(player, stall);
					c.command(player, "aktradeex reputation set 1000"); // clerks and 3-star requests need a reputation
					c.giveMoney(player, 5_000);
					Identifier good = c.tradableGood(market, 0, 20);
					Item item = c.item(good);
					ServerCases.giveItems(player, item, 4);
					var hired = ServerChapterCases.staff(c, player, market, StaffRequestC2S.Action.HIRE_CLERK, 0, good, 0, 4, 0, 1, 0, false);
					that(hired == TradeResult.SUCCESS, "hiring a clerk gave %s", hired);
					c.command(player, "aktradeex staff hurry");
					StaffS2C.View clerk = ServerChapterCases.only(c, player, market);
					that(clerk.earned() > 0, "the clerk sold nothing");
					that(!STAFF_TRADES.isEmpty(), "no STAFF_TRADE event");
					long total = 0;
					for (TradeExpansionEvents.StaffTrade t : STAFF_TRADES) {
						that(t.owner().equals(player.getUUID()), "STAFF_TRADE owner %s", t.owner());
						that(t.role() == TradeExpansionEvents.StaffRole.CLERK && !t.buy(), "STAFF_TRADE %s buy=%s", t.role(), t.buy());
						equal(market.id(), t.marketId(), "STAFF_TRADE market");
						that(t.goods().equals(good), "STAFF_TRADE goods %s", t.goods());
						equal(t.units(), t.itemCount(), "STAFF_TRADE items of a good traded one by one");
						that(t.server() == server, "STAFF_TRADE server");
						total += t.total();
					}
					equal(clerk.earned(), total, "STAFF_TRADE totals against what the clerk earned");

					Request request = market.requests().stream().filter(r -> !r.isTransport()).min(Comparator.comparingInt(Request::difficulty))
							.orElseThrow(() -> new AutotestCheck.Failed("the market has no request"));
					Item wanted = c.item(request.goods());
					ServerCases.giveItems(player, wanted, request.quantity());
					long now = MarketUpkeep.touch(c.level(), market);
					long reward = Reputation.reward(market, player.getUUID(), request, now);
					QuestService.deliver(player, new QuestDeliverC2S(market.id(), request.id(), false));
					equal(1, REQUESTS.size(), "REQUEST_COMPLETED events");
					TradeExpansionEvents.RequestCompleted done = REQUESTS.getFirst();
					that(done.player() == player && done.kind() == TradeExpansionEvents.RequestKind.GOODS, "REQUEST_COMPLETED %s %s", done.player(), done.kind());
					equal(market.id(), done.marketId(), "REQUEST_COMPLETED market");
					equal(market.id(), done.sourceMarketId(), "REQUEST_COMPLETED source market");
					that(done.goods().equals(request.goods()), "REQUEST_COMPLETED goods %s", done.goods());
					equal(request.quantity(), done.quantity(), "REQUEST_COMPLETED quantity");
					equal(reward, done.reward(), "REQUEST_COMPLETED reward");
					that(!done.card(), "REQUEST_COMPLETED paid by card");
				})));
		cases.add(partial("api_external_trade", List.of("46-2"),
				"recordExternalTrade で買ったことにすると値段が上がって在庫が減り、売ったことにすると値段が下がって在庫が増え、MARKETS_CHANGED にその市場が入ること。"
						+ "在庫より多くは買えず（在庫の数だけ）、扱っていない品・無い市場は 0、数が範囲の外なら例外になること（周りの市場への波及は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					listen();
					MinecraftServer server = c.level().getServer();
					Identifier good = c.tradableGood(market, 3, 3);
					GoodState state = market.good(good);
					MarketUpkeep.touch(c.level(), market);
					long price0 = buyPrice(server, market, good);
					double stock0 = state.stock();
					equal(3, TradeExpansionApi.recordExternalTrade(server, market.id(), good, true, 3), "units bought");
					that(buyPrice(server, market, good) > price0, "buying did not raise the price (%d)", price0);
					that(Math.abs(state.stock() - (stock0 - 3)) < 1e-6, "stock after buying 3: %s (was %s)", state.stock(), stock0);
					that(CHANGED.stream().anyMatch(ids -> ids.contains(market.id())), "no MARKETS_CHANGED with the market: %s", CHANGED);
					long price1 = buyPrice(server, market, good);
					equal(3, TradeExpansionApi.recordExternalTrade(server, market.id(), good, false, 3), "units sold");
					that(buyPrice(server, market, good) < price1, "selling did not lower the price (%d)", price1);
					// Buying more than the stock: only the stock.
					int stock = (int) Math.floor(state.stock());
					int all = TradeExpansionApi.recordExternalTrade(server, market.id(), good, true, TradeExpansionApi.MAX_EXTERNAL_UNITS);
					equal(stock, all, "units bought when asking for more than the stock");
					equal(0, TradeExpansionApi.recordExternalTrade(server, market.id(), good, true, 1), "units bought with no stock");
					Identifier missing = com.akf.tradeexpansion.data.GoodsCatalog.all().keySet().stream().filter(id -> market.good(id) == null)
							.findFirst().orElseThrow();
					equal(0, TradeExpansionApi.recordExternalTrade(server, market.id(), missing, false, 1), "units of goods the market does not deal in");
					equal(0, TradeExpansionApi.recordExternalTrade(server, -12345L, good, false, 1), "units at an unknown market");
					boolean thrown = false;
					try {
						TradeExpansionApi.recordExternalTrade(server, market.id(), good, false, 0);
					} catch (IllegalArgumentException e) {
						thrown = true;
					}
					that(thrown, "0 units did not throw");
				})));
		cases.add(partial("api_item_valuation", List.of("46-5"),
				"値付け（ItemValuation）を登録した商品で、ぴったりの品は1倍、値付けの品は倍率どおりの値段で、倍率の高い品から売れること。"
						+ "市場のほかの商品にぴったりの品は値付けの商品で数えないこと（まとめて売る・売却スロット・クライアントのプレビューは見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					long now = MarketUpkeep.touch(c.level(), market);
					Identifier good = c.tradableGood(market, 0, 4);
					Item item = BuiltInRegistries.ITEM.getValue(GoodsCatalog.get(good).item());
					Identifier otherGood = market.goods().keySet().stream().filter(id -> {
						GoodsDefinition def = GoodsCatalog.get(id);
						return def != null && def.sell() && def.unit() == 1 && def.components().isEmpty() && !def.item().equals(GoodsCatalog.get(good).item())
								&& Reputation.canSee(market, player.getUUID(), id, now);
					}).findFirst().orElseThrow(() -> new AutotestCheck.Failed("no second plain goods in the market"));
					Item other = BuiltInRegistries.ITEM.getValue(GoodsCatalog.get(otherGood).item());
					// Named stacks of the good's item are worth 3x; the other goods' plain item would be 2x but belongs to that goods.
					ItemValuation.register(good, stack -> stack.has(DataComponents.CUSTOM_NAME) ? 3.0 : stack.is(other) ? 2.0 : -1);
					c.atCleanUp(() -> ItemValuations.unregister(good));
					c.emptyInventory(player);
					ServerCases.giveItems(player, item, 2);
					ItemStack named = new ItemStack(item, 2);
					named.set(DataComponents.CUSTOM_NAME, Component.literal("valued"));
					player.getInventory().add(named);
					ServerCases.giveItems(player, other, 1);
					InventoryUtil.Match match = TradeService.sellMatch(market, player.getUUID(), good, now);
					equal(4, InventoryUtil.count(player, match, null), "items counted as the valued goods (2 plain + 2 named, not the other goods' item)");
					double[] ratios = InventoryUtil.sellRatios(player, match, null, 4);
					that(ratios != null && ratios[0] == 3.0 && ratios[1] == 3.0 && ratios[2] == 1.0 && ratios[3] == 1.0,
							"selling order and ratios: %s", java.util.Arrays.toString(ratios));
					var params = Reputation.params(market, player.getUUID(), good, now);
					long expected = PriceModel.sumSell(params, market.good(good).pressure(), ratios);
					that(expected > PriceModel.sumSell(params, market.good(good).pressure(), 4), "the valued items are not worth more (%d)", expected);
					TradeResult sold = c.tradeRaw(player, market.id(), good, false, 4, expected, false, false);
					that(sold == TradeResult.SUCCESS, "selling 4 of the valued goods gave %s", sold);
					equal(0, player.getInventory().countItem(item), "items of the good left (plain and named)");
					equal(1, InventoryUtil.countPlain(player, other), "the other goods' item left");
				})));
		cases.add(partial("api_money", List.of("46-3"),
				"deposit で口座が増え、withdraw は残高まで引けて、足りなければ false で変わらず、giveCash・takeCash で財布と硬貨が増減し、足りなければ false で変わらないこと。"
						+ "オフラインのプレイヤーの口座にも入れられること（利息の扱いは見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					MinecraftServer server = c.level().getServer();
					UUID id = player.getUUID();
					long bank0 = TradeExpansionApi.bankBalance(server, id);
					TradeExpansionApi.deposit(server, id, 1_000);
					equal(bank0 + 1_000, TradeExpansionApi.bankBalance(server, id), "bank after depositing 1000");
					that(!TradeExpansionApi.withdraw(server, id, bank0 + 1_001), "withdrawing more than the balance succeeded");
					equal(bank0 + 1_000, TradeExpansionApi.bankBalance(server, id), "bank after a refused withdrawal");
					that(TradeExpansionApi.withdraw(server, id, 400), "withdrawing 400 failed");
					equal(bank0 + 600, TradeExpansionApi.bankBalance(server, id), "bank after withdrawing 400");
					UUID offline = UUID.randomUUID();
					TradeExpansionApi.deposit(server, offline, 250);
					equal(250, TradeExpansionApi.bankBalance(server, offline), "bank of an offline player");

					long cash0 = TradeExpansionApi.cash(player);
					TradeExpansionApi.giveCash(player, 1_234);
					equal(cash0 + 1_234, TradeExpansionApi.cash(player), "cash after giving 1234");
					that(!TradeExpansionApi.takeCash(player, cash0 + 1_235), "taking more cash than carried succeeded");
					equal(cash0 + 1_234, TradeExpansionApi.cash(player), "cash after a refused payment");
					that(TradeExpansionApi.takeCash(player, 234), "taking 234 failed");
					equal(cash0 + 1_000, TradeExpansionApi.cash(player), "cash after paying 234");
				})));
		return cases;
	}
}
