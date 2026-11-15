package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.advancement.TradeAdvancements;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.development.DevelopmentService;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.clientLoaded;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.markets;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.openAt;

/**
 * Server cases of the advancements (checklist chapter 27): each is granted by what the player does in the game
 * (placing a stall, trades, requests, the bank, donations, reputation, hiring), and some survive dying and joining
 * again.
 */
final class ServerAdvancementCases {
	private ServerAdvancementCases() {
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(full("advancements_first_trades", List.of("27-2"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			done(c, player, "open_shop", "after placing a stall");
			c.openStall(player, stall);
			notYet(c, player, "first_trade");
			c.giveMoney(player, 1_000_000);
			// 64 at once: the bulk buy; prices ×10 so that one trade is 1,000c or more (the big deal).
			c.config(cfg -> cfg.price.globalMultiplier = 10);
			Identifier good = c.tradableGood(market, 64, 0);
			long total = c.trade(player, market, good, true, 64);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying 64 %s gave %s", good, c.lastTradeResult(player));
			that(total >= 1_000, "64 of %s cost only %dc", good, total);
			done(c, player, "first_trade", "after the first trade");
			done(c, player, "bulk_buy", "after buying 64 at once");
			done(c, player, "big_deal", "after a trade of " + total + "c");
		})));
		cases.add(full("advancements_selling", List.of("27-3"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			long now = MarketUpkeep.touch(c.level(), market);
			// The good with the least room left: sold up to the stock limit (the market stops buying it).
			Identifier flood = market.regularGoods().stream().filter(g -> sellable(c, player, market, g, 1))
					.min(Comparator.comparingInt(g -> room(c, player, market, g))).orElseThrow(() -> new AutotestCheck.Failed("no good to sell"));
			int room = room(c, player, market, flood);
			that(room <= 2000, "%s has room for %d", flood, room);
			ServerCases.giveItems(player, c.item(flood), room);
			notYet(c, player, "market_flood");
			var r = c.tradeRaw(player, market.id(), flood, false, room, c.previewTotal(player, market, flood, false, room), false, true);
			that(r == TradeResult.SUCCESS, "selling up to the stock limit gave %s", r);
			done(c, player, "market_flood", "after selling up to the stock limit");

			Identifier other = market.regularGoods().stream().filter(g -> !g.equals(flood) && sellable(c, player, market, g, 2)).findFirst()
					.orElseThrow(() -> new AutotestCheck.Failed("no second good to sell"));
			market.shortages().put(other, 1.5);
			ServerCases.giveItems(player, c.item(other), 1);
			c.trade(player, market, other, false, 1);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling the short good gave %s", c.lastTradeResult(player));
			done(c, player, "shortage", "after selling a good in shortage");
			market.shortages().remove(other);

			c.config(cfg -> cfg.rivals.chancePerDay = 0);
			buyingCampaign(c, market, other, now);
			notYet(c, player, "rival");
			ServerCases.giveItems(player, c.item(other), 1);
			c.trade(player, market, other, false, 1);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling the good a guild buys gave %s", c.lastTradeResult(player));
			done(c, player, "rival", "after selling a good a rival guild is buying");
		})));
		cases.add(partial("advancements_three_markets", List.of("27-4"), "3つの市場で取引すると「行商」が付くこと（ネザー・エンドの市場は見ていない）",
				ctx -> markets(ctx, new int[] {0, 300, 600}, (c, player, m) -> {
					c.giveMoney(player, 100_000);
					for (int i = 0; i < 3; i++) {
						if (i > 0) openAt(c, player, m, i);
						notYet(c, player, "peddler");
						Identifier good = c.tradableGood(m.get(i), 1, 0);
						c.trade(player, m.get(i), good, true, 1);
						that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying at market %d gave %s", i, c.lastTradeResult(player));
					}
					done(c, player, "peddler", "after trading at 3 markets");
				})));
		cases.add(partial("advancements_requests", List.of("27-5"), "依頼を納めると「頼れる人」が付くこと（★3 の「腕利き」と輸送依頼の「長い旅路」は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					Request request = market.requests().stream().filter(q -> !q.isTransport()).min(Comparator.comparingInt(Request::difficulty))
							.orElseThrow(() -> new AutotestCheck.Failed("the market has no request"));
					c.command(player, "aktradeex reputation set 1000");
					ServerCases.giveItems(player, c.item(request.goods()), request.quantity());
					notYet(c, player, "first_quest");
					QuestService.deliver(player, new QuestDeliverC2S(market.id(), request.id(), false));
					that(market.requests().stream().noneMatch(q -> q.id() == request.id()), "the request was not delivered");
					done(c, player, "first_quest", "after delivering a request");
				})));
		cases.add(partial("advancements_reputation_and_hiring", List.of("27-6"),
				"評判を 50・400・2500 にして交易画面を開くと「顔見知り」「お得意様」「名士」、運び屋を雇うと「人を使う身」が付くこと（運び屋の往復の「留守を任せて」は見ていない）",
				ctx -> markets(ctx, new int[] {0, 600}, (c, player, m) -> {
					Market a = m.get(0);
					String[][] steps = {{"50", "acquaintance"}, {"400", "valued_customer"}, {"2500", "notable"}};
					for (String[] step : steps) {
						notYet(c, player, step[1]);
						c.command(player, "aktradeex reputation set " + step[0]);
						openAt(c, player, m, 0);
						done(c, player, step[1], "after opening the screen with reputation " + step[0]);
					}
					c.giveMoney(player, 5_000);
					notYet(c, player, "employer");
					var hired = ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, m.get(1).id(), 64, 0, 0, 0, false);
					that(hired == TradeResult.SUCCESS, "hiring a carrier gave %s", hired);
					done(c, player, "employer", "after hiring a carrier");
				})));
		cases.add(partial("advancements_donation", List.of("27-7"), "「発展」タブでコインを寄付すると「村への投資」が付くこと（建物の完成の「槌音」「村おこし」は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.giveMoney(player, 1_000);
					notYet(c, player, "investor");
					DevelopmentService.handle(player, new DevelopmentRequestC2S(market.id(), DevelopmentRequestC2S.Action.DONATE_COINS, 100, "", false));
					done(c, player, "investor", "after donating 100c");
				})));
		cases.add(full("advancements_money", List.of("27-8"), ctx -> {
			ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				notYet(c, player, "wallet");
				player.getInventory().add(new ItemStack(ModItems.WALLET));
				sync(player);
				done(c, player, "wallet", "with a wallet in the inventory");
				c.giveMoney(player, 20_000);
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 5_000));
				done(c, player, "savings", "after depositing 5,000c");
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
				done(c, player, "card", "after getting a card");
				// Cash and bank together 10,000c or more (checked after deposits and trades).
				Identifier good = c.tradableGood(market, 1, 0);
				c.trade(player, market, good, true, 1);
				done(c, player, "well_off", "with " + (c.balance(player) + BankService.accounts(player).balance(player.getUUID())) + "c after a trade");
				notYet(c, player, "fireproof");
				player.getInventory().add(new ItemStack(ModItems.NETHERITE_COIN));
				sync(player);
				done(c, player, "fireproof", "with a netherite coin");
			});
		}));
		cases.add(partial("advancements_after_joining_again", List.of("27-9"), "評判が高い人が入り直すと、ログインした時に評判の進捗が付くこと（建物・所持金は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.command(player, "aktradeex reputation set 400");
					player.closeContainer();
					c.command(player, "advancement revoke @s everything");
					notYet(c, player, "valued_customer");
					ServerPlayer again = c.rejoin(player);
					done(c, again, "valued_customer", "after joining again with reputation 400");
				})));
		cases.add(partial("advancements_survive_death", List.of("27-10"), "2つの市場で取引してから死んで生き返っても、取引した市場の記録（「行商」の数）が残っていること（生き返った後の取引は見ていない）",
				ctx -> markets(ctx, new int[] {0, 300}, (c, player, m) -> {
					c.giveMoney(player, 100_000);
					for (int i = 0; i < 2; i++) {
						if (i > 0) openAt(c, player, m, i);
						c.trade(player, m.get(i), c.tradableGood(m.get(i), 1, 0), true, 1);
						that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying at market %d gave %s", i, c.lastTradeResult(player));
					}
					List<Long> before = player.getAttachedOrElse(TradeAdvancements.TRADED_MARKETS, List.of());
					that(before.size() == 2, "markets traded at before dying: %s", before);
					player.closeContainer();
					clientLoaded(player);
					player.kill(c.level());
					that(player.isDeadOrDying(), "the player did not die");
					c.later(2, cc -> {
						// (The test world sends a respawned test player far away, so it does not trade again here.)
						ServerPlayer fresh = cc.respawn(player);
						List<Long> after = fresh.getAttachedOrElse(TradeAdvancements.TRADED_MARKETS, List.of());
						that(after.equals(before), "markets traded at after dying: %s (before %s)", after, before);
					});
				})));
		return cases;
	}

	// ---- helpers

	private static AdvancementHolder advancement(ServerCtx c, String name) {
		AdvancementHolder holder = c.server().getAdvancements().get(Identifier.fromNamespaceAndPath("ak_trade_expansion", name));
		that(holder != null, "no advancement %s", name);
		return holder;
	}

	private static boolean isDone(ServerCtx c, ServerPlayer player, String name) {
		return player.getAdvancements().getOrStartProgress(advancement(c, name)).isDone();
	}

	private static void done(ServerCtx c, ServerPlayer player, String name, String when) {
		that(isDone(c, player, name), "the advancement %s is not granted %s", name, when);
	}

	private static void notYet(ServerCtx c, ServerPlayer player, String name) {
		that(!isDone(c, player, name), "the advancement %s is granted too early", name);
	}

	/** The inventory triggers (inventory_changed) run when the menus send their changes. */
	private static void sync(ServerPlayer player) {
		player.inventoryMenu.broadcastChanges();
		player.containerMenu.broadcastChanges();
	}

	private static int room(ServerCtx c, ServerPlayer player, Market market, Identifier good) {
		long now = MarketUpkeep.touch(c.level(), market);
		return PriceModel.sellCapacity(Reputation.params(market, player.getUUID(), good, now), market.good(good));
	}

	private static boolean sellable(ServerCtx c, ServerPlayer player, Market market, Identifier good, int units) {
		GoodsDefinition def = GoodsCatalog.get(good);
		if (def == null || !def.sell() || def.unit() != 1 || def.components().isPresent()) return false;
		if (BuiltInRegistries.ITEM.getValue(def.item()) == Items.AIR) return false;
		return room(c, player, market, good) >= units;
	}

	/** A rival guild buys {@code good} around {@code market} from today (as {@code rivals start} does). */
	private static void buyingCampaign(ServerCtx c, Market market, Identifier good, long now) {
		MarketRegistry registry = c.registry();
		long today = RivalService.day(now);
		Identifier guild = RivalGuilds.all().entrySet().stream().filter(e -> e.getValue().dealsIn(good)).map(Map.Entry::getKey).findFirst()
				.orElseGet(() -> RivalGuilds.all().keySet().iterator().next());
		Campaign camp = new Campaign(registry.takeCampaignId(), guild, good, market.id(), true, today, today + 3, 10);
		registry.campaigns().add(camp);
		RivalService.push(registry, market, camp, today, today, now);
		registry.setDirty();
		that(RivalService.strongest(registry, market, good, now) != null, "the campaign on %s does not reach the market", good);
	}

}
