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
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
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
import net.minecraft.core.BlockPos;
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
		cases.add(full("advancements_requests", List.of("27-5"), ctx -> markets(ctx, new int[] {0, 600}, (c, player, m) -> {
			Market a = m.get(0), b = m.get(1);
			c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
			long now = c.now();
			// One easy and one 3-star request of a good the market deals in, first in the list (the rolled ones vary).
			Identifier good = c.tradableGood(a, 0, 0);
			Request easy = new Request(a.takeRequestId(), good, 2, 100, now + 3 * PriceModel.TICKS_PER_DAY, 1);
			Request hard = new Request(a.takeRequestId(), good, 2, 300, now + 3 * PriceModel.TICKS_PER_DAY, 3);
			a.requests().add(0, hard);
			a.requests().add(0, easy);
			notYet(c, player, "first_quest");
			ServerCases.giveItems(player, c.item(good), 2);
			QuestService.deliver(player, new QuestDeliverC2S(a.id(), easy.id(), false));
			that(a.requests().stream().noneMatch(q -> q.id() == easy.id()), "the 1-star request was not delivered");
			done(c, player, "first_quest", "after delivering a 1-star request");
			notYet(c, player, "hard_quest");
			ServerCases.giveItems(player, c.item(good), 2);
			QuestService.deliver(player, new QuestDeliverC2S(a.id(), hard.id(), false));
			that(a.requests().stream().noneMatch(q -> q.id() == hard.id()), "the 3-star request was not delivered");
			done(c, player, "hard_quest", "after delivering a 3-star request");
			// A transport request taken at A and delivered at B.
			Request transport = ServerChapterCases.transportOf(a, b);
			c.giveMoney(player, transport.transport().orElseThrow().deposit() + 1_000);
			c.forget(player);
			QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), false));
			that(ServerChapterCases.questResult(c, player) == TradeResult.SUCCESS, "taking the crate gave %s", ServerChapterCases.questResult(c, player));
			notYet(c, player, "cargo");
			openAt(c, player, m, 1);
			c.forget(player);
			QuestService.deliverCargo(player, new CargoDeliverC2S(b.id(), false));
			that(ServerChapterCases.questResult(c, player) == TradeResult.SUCCESS, "delivering the crate gave %s", ServerChapterCases.questResult(c, player));
			done(c, player, "cargo", "after delivering a transport request");
		})));
		cases.add(full("advancements_reputation_and_hiring", List.of("27-6"), ctx -> {
			ctx.config(cfg -> cfg.staff.accidentBase = 0);
			markets(ctx, new int[] {0, 600}, (c, player, m) -> {
				Market a = m.get(0), b = m.get(1);
				String[][] steps = {{"50", "acquaintance"}, {"400", "valued_customer"}, {"2500", "notable"}};
				for (String[] step : steps) {
					notYet(c, player, step[1]);
					c.command(player, "aktradeex reputation set " + step[0]);
					openAt(c, player, m, 0);
					done(c, player, step[1], "after opening the screen with reputation " + step[0]);
				}
				c.giveMoney(player, 20_000);
				notYet(c, player, "employer");
				// Every good dear at A and cheap at B: the carrier goes out empty and makes its profit on the return cargo
				// (as in carrier_return_cargo), so the round trip surely pays.
				ServerChapterCases.shared(c, a, 0, 8, b);
				for (Identifier g : a.goods().keySet()) a.good(g).setPressure(a.params(g).minPressure() * 0.8);
				for (Identifier g : b.goods().keySet()) b.good(g).setPressure(b.params(g).maxPressure() * 0.8);
				var hired = ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
				that(hired == TradeResult.SUCCESS, "hiring a carrier gave %s", hired);
				done(c, player, "employer", "after hiring a carrier");
				var e = ServerChapterCases.only(c, player, a);
				ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 10_000, false);
				notYet(c, player, "away_profit");
				c.command(player, "aktradeex staff hurry"); // out (empty, for the return cargo)
				ServerChapterCases.staff(c, player, a, StaffRequestC2S.Action.PAUSE, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false); // one trip only
				c.command(player, "aktradeex staff hurry"); // at B: buys the return cargo
				notYet(c, player, "away_profit");
				c.command(player, "aktradeex staff hurry"); // home: sells it
				e = ServerChapterCases.only(c, player, a);
				that(e.phase() == Employee.Phase.HOME.ordinal() && e.earned() > e.spent(), "the carrier did not come back with a profit (phase %s, earned %d, spent %d)",
						Employee.Phase.values()[e.phase()], e.earned(), e.spent());
				done(c, player, "away_profit", "after a round trip with a profit");
			});
		}));
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
		cases.add(full("advancements_after_joining_again", List.of("27-9"), ctx -> ServerDevelopmentCases.town(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			// A building the player helped with (a donation), a high reputation and 10,000c in the bank: what an older world
			// may hold without the advancements.
			ServerDevelopmentCases.builder(c, player, market);
			c.giveMoney(player, 1_000);
			ServerDevelopmentCases.donate(c, player, market, 100, false);
			ServerDevelopmentCases.buildAndComplete(c, player, market, com.akf.tradeexpansion.TradeExpansion.id("warehouse"));
			c.command(player, "aktradeex reputation set 400");
			BankService.accounts(player).add(player.getUUID(), 10_000);
			player.closeContainer();
			c.command(player, "advancement revoke @s everything");
			for (String name : List.of("valued_customer", "builder", "well_off")) notYet(c, player, name);
			ServerPlayer again = c.rejoin(player);
			done(c, again, "valued_customer", "after joining again with reputation 400");
			done(c, again, "builder", "after joining again with a completed building");
			done(c, again, "well_off", "after joining again with 10,000c in the bank");
		})));
		cases.add(full("advancements_survive_death", List.of("27-10"),
				ctx -> markets(ctx, new int[] {0, 300, 600}, (c, player, m) -> {
					c.giveMoney(player, 100_000);
					for (int i = 0; i < 2; i++) {
						if (i > 0) openAt(c, player, m, i);
						c.trade(player, m.get(i), c.tradableGood(m.get(i), 1, 0), true, 1);
						that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying at market %d gave %s", i, c.lastTradeResult(player));
					}
					List<Long> before = player.getAttachedOrElse(TradeAdvancements.TRADED_MARKETS, List.of());
					that(before.size() == 2, "markets traded at before dying: %s", before);
					notYet(c, player, "peddler");
					player.closeContainer();
					clientLoaded(player);
					player.kill(c.level());
					that(player.isDeadOrDying(), "the player did not die");
					c.later(2, cc -> {
						ServerPlayer fresh = cc.respawn(player);
						List<Long> after = fresh.getAttachedOrElse(TradeAdvancements.TRADED_MARKETS, List.of());
						that(after.equals(before), "markets traded at after dying: %s (before %s)", after, before);
						// The test world sends a respawned test player far away: bring it to the third market (its chunks are
						// kept loaded) and trade there.
						BlockPos third = m.get(2).stalls().iterator().next();
						// The respawn's own teleport waits for the client to accept it: accept it as a client would, then move
						// (again a few ticks later if the server sent another one meanwhile).
						MOVE_NOTES.clear();
						moveTo(cc, fresh, third);
						cc.later(2, c3 -> {
							if (!fresh.blockPosition().closerThan(third, 4)) moveTo(c3, fresh, third);
						});
						cc.later(5, c3 -> {
							that(fresh.level() == c3.level(), "the respawned player is in %s", fresh.level().dimension().identifier());
							clientLoaded(fresh);
							openAt(c3, fresh, m, 2);
							that(fresh.blockPosition().closerThan(third, 4), "the respawned player stands at %s, not at the stall %s (%s)", fresh.blockPosition().toShortString(),
									third.toShortString(), MOVE_NOTES);
							c3.giveMoney(fresh, 100_000);
							c3.trade(fresh, m.get(2), c3.tradableGood(m.get(2), 1, 0), true, 1);
							that(c3.lastTradeResult(fresh) == TradeResult.SUCCESS, "buying at the third market after respawning gave %s", c3.lastTradeResult(fresh));
							List<Long> now = fresh.getAttachedOrElse(TradeAdvancements.TRADED_MARKETS, List.of());
							that(now.size() == 3 && now.containsAll(before), "markets traded at after trading again: %s (before dying %s)", now, before);
							done(c3, fresh, "peddler", "after trading at a third market after dying");
						});
					});
				})));
		return cases;
	}

	/** Accepts every teleport sent to {@code player} (as a client does) and teleports it next to {@code stall}. */
	private static void moveTo(ServerCtx c, ServerPlayer player, BlockPos stall) {
		for (Object sent : c.sent(player)) {
			if (sent instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket pp) {
				player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(pp.id(), player.getX(),
						player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
			}
		}
		boolean ok = player.teleportTo(c.level(), stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5, java.util.Set.of(), 0, 0, true);
		if (!player.blockPosition().closerThan(stall, 4)) {
			MOVE_NOTES.add("teleportTo " + ok + " left it at " + player.blockPosition().toShortString() + " (sleeping " + player.isSleeping() + ", passenger "
					+ player.isPassenger() + ", removed " + player.isRemoved() + ")");
			player.snapTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		}
	}

	private static final List<String> MOVE_NOTES = new java.util.ArrayList<>();

	// ---- helpers

	private static AdvancementHolder advancement(ServerCtx c, String name) {
		AdvancementHolder holder = c.server().getAdvancements().get(Identifier.fromNamespaceAndPath("ak_trade_expansion", name));
		that(holder != null, "no advancement %s", name);
		return holder;
	}

	private static boolean isDone(ServerCtx c, ServerPlayer player, String name) {
		return player.getAdvancements().getOrStartProgress(advancement(c, name)).isDone();
	}

	static void done(ServerCtx c, ServerPlayer player, String name, String when) {
		that(isDone(c, player, name), "the advancement %s is not granted %s", name, when);
	}

	static void notYet(ServerCtx c, ServerPlayer player, String name) {
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
