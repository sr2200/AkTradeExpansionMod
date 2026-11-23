package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.entity.HiredHandEntity;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.staff.HiredHands;
import com.akf.tradeexpansion.staff.StaffService;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.list;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.markets;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.only;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.shared;
import static com.akf.tradeexpansion.test.server.ServerChapterCases.staff;

/** Chapter 43 (design Q, v0.83): the staff improvements. */
final class ServerStaffCases {
	private ServerStaffCases() {
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("carrier_roams_to_the_best", List.of("43-1"),
				"「毎回選ぶ」で雇った運び屋が、出かけるたびに今いちばん儲かる行き先へ行き、儲かる行き先が変わると次の往復で行き先を変えること（行き先の一覧の「毎回選ぶ」の行は見ていない）",
				ctx -> {
					ctx.config(cfg -> {
						cfg.staff.accidentBase = 0;
						cfg.staff.carrierBackhaul = false;
					});
					markets(ctx, new int[] {0, 400, 800}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1), d = m.get(2);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 30_000);
						Identifier good = shared(c, a, 8, 0, b, d);
						// Dear only at the far market: worth the longer trip.
						for (Identifier g : a.goods().keySet()) a.good(g).setPressure(a.params(g).minPressure() * 0.8);
						for (Market o : List.of(b, d)) for (Identifier g : o.goods().keySet()) o.good(g).setPressure(o.params(g).maxPressure() * 0.8);
						a.good(good).setPressure(a.params(good).maxPressure() * 0.8);
						d.good(good).setPressure(d.params(good).minPressure() * 0.8);
						var hired = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, Employee.ROAM, 64, 0, 0, 0, false);
						that(hired == TradeResult.SUCCESS, "hiring a roaming carrier gave %s", hired);
						StaffS2C.View e = only(c, player, a);
						that(e.roam(), "the carrier does not choose its destination");
						staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 10_000, false);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(Employee.Phase.OUTBOUND.ordinal(), e.phase(), "phase after buying");
						equal(d.id(), e.destination(), "the destination where the good is dear");
						// paused, it stops when it is back (instead of going out again at once)
						that(staff(c, player, a, StaffRequestC2S.Action.PAUSE, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false) == TradeResult.SUCCESS, "pausing failed");
						c.command(player, "aktradeex staff hurry"); // sells there, starts back
						c.command(player, "aktradeex staff hurry"); // home
						e = only(c, player, a);
						equal(Employee.Phase.HOME.ordinal(), e.phase(), "phase after the round trip");
						that(staff(c, player, a, StaffRequestC2S.Action.PAUSE, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false) == TradeResult.SUCCESS, "resuming failed");
						// Now it is dear at the near market only.
						d.good(good).setPressure(d.params(good).maxPressure() * 0.8);
						b.good(good).setPressure(b.params(good).minPressure() * 0.8);
						a.good(good).setPressure(a.params(good).maxPressure() * 0.8);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(b.id(), e.destination(), "the destination after the prices changed");
						that(e.log().stream().anyMatch(l -> l.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
								&& t.getKey().equals("staff.ak_trade_expansion.log.roam_chose")), "no log line of the new destination");
					});
				}));
		cases.add(partial("fixed_carrier_told_and_made_auto", List.of("43-2", "43-3"),
				"品を決めた運び屋が1日儲からないとログとチャットで一度だけ知らせ、「おまかせ」への変更で行き先と1回に運ぶ数はそのままおまかせになること（ボタンの表示は見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.staff.carrierBackhaul = false); // no empty trip for return cargo
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 20_000);
						Identifier good = shared(c, a, 8, 0, b);
						a.good(good).setPressure(a.params(good).minPressure() * 0.8); // dear here
						b.good(good).setPressure(b.params(good).maxPressure() * 0.8); // cheap there: no profit
						var hired = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, good, b.id(), 20, 1_000_000, 1, 0, false);
						that(hired == TradeResult.SUCCESS, "hiring gave %s", hired);
						StaffS2C.View e = only(c, player, a);
						staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), good, 0, 0, 0, 0, 5_000, false);
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						equal(Employee.Status.NO_MARGIN.ordinal(), e.status(), "status where nothing pays");
						String key = "staff.ak_trade_expansion.log.no_margin_long";
						that(e.log().stream().noneMatch(l -> l.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key)),
								"told before a day had passed");
						c.command(player, "aktradeex clock advance 1.05");
						a.good(good).setPressure(a.params(good).minPressure() * 0.8);
						b.good(good).setPressure(b.params(good).maxPressure() * 0.8);
						c.forget(player);
						c.command(player, "aktradeex staff hurry");
						boolean chat = c.sent(player).stream().anyMatch(x -> x instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p
								&& p.content().getString().contains(net.minecraft.network.chat.Component.translatable("staff.ak_trade_expansion.role.carrier").getString()));
						that(chat, "no chat line after a day without profit");
						e = only(c, player, a);
						long told = e.log().stream().filter(l -> l.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key)).count();
						equal(1L, told, "notices after a day");
						c.command(player, "aktradeex staff hurry");
						e = only(c, player, a);
						told = e.log().stream().filter(l -> l.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key)).count();
						equal(1L, told, "notices after trying again (only once)");
						var changed = staff(c, player, a, StaffRequestC2S.Action.CHANGE, e.id(), Employee.AUTO, e.destination(), e.quantity(), 1, 1, 0, false);
						that(changed == TradeResult.SUCCESS, "making it automatic gave %s", changed);
						StaffS2C.View after = only(c, player, a);
						that(after.auto(), "the carrier is not automatic");
						equal(b.id(), after.destination(), "destination after the change");
						equal(20, after.quantity(), "units per trip after the change");
					});
				}));
		cases.add(full("clerk_paid_by_share_and_faster", List.of("43-4", "43-5"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.command(player, "aktradeex reputation set 150");
			c.giveMoney(player, 5_000);
			StaffService.resetDaily();
			Identifier good = c.tradableGood(market, 0, 20);
			Item item = c.item(good);
			ServerCases.giveItems(player, item, 6);
			double x0 = market.good(good).pressure();
			var hired = staff(c, player, market, StaffRequestC2S.Action.HIRE_CLERK, 0, good, 0, 6, 0, 1, 0, false);
			that(hired == TradeResult.SUCCESS, "hiring a clerk gave %s", hired);
			c.command(player, "aktradeex staff hurry");
			StaffS2C.View e = only(c, player, market);
			int sold = 6 - e.stock();
			that(sold > 0, "the clerk sold nothing");
			double rate = TradeExpansionConfig.get().staff.clerkCommission;
			equal((long) Math.floor(e.earned() * rate), e.wages(), "the clerk's pay (a share of the sales)");
			equal(e.earned() - e.wages(), e.purse(), "the purse (sales less the pay)");
			that(Math.abs(e.commission() - rate) < 1e-9, "the pay share shown: %s", e.commission());
			double moved = market.good(good).pressure() - x0;
			double impact = TradeExpansionConfig.get().staff.clerkPriceImpact;
			that(Math.abs(moved - sold * impact) < 0.05, "selling %d moved X by %.2f (expected %.2f)", sold, moved, sold * impact);
			equal(0L, StaffService.clerkWage(market), "the clerk's daily wage");
		})));
		cases.add(full("staff_market_daily_limit", List.of("43-6"), ctx -> {
			ctx.config(cfg -> cfg.staff.marketDailyUnits = 3);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				c.command(player, "aktradeex reputation set 150");
				c.giveMoney(player, 5_000);
				StaffService.resetDaily();
				Identifier good = c.tradableGood(market, 0, 20);
				ServerCases.giveItems(player, c.item(good), 8);
				var hired = staff(c, player, market, StaffRequestC2S.Action.HIRE_CLERK, 0, good, 0, 8, 0, 1, 0, false);
				that(hired == TradeResult.SUCCESS, "hiring a clerk gave %s", hired);
				c.command(player, "aktradeex staff hurry");
				c.command(player, "aktradeex staff hurry");
				StaffS2C.View e = only(c, player, market);
				equal(5, e.stock(), "goods left with a limit of 3 a day");
				equal(Employee.Status.MARKET_BUSY.ordinal(), e.status(), "status at the limit");
				c.command(player, "aktradeex clock advance 1");
				c.command(player, "aktradeex staff hurry");
				e = only(c, player, market);
				equal(2, e.stock(), "goods left the next day");
				StaffService.resetDaily();
			});
		}));
		cases.add(partial("employees_stand_at_home", List.of("43-7", "43-8"),
				"番頭と、雇った村にいる運び屋が村人として市場の近くに立ち（名前つき）、持ち主が話しかけると交易画面が雇い人タブで開き、ほかの人には開かないこと。運び屋が出かけると消え、解雇すると消えること（村人の見た目は見ていない）",
				ctx -> {
					ctx.config(cfg -> {
						cfg.staff.accidentBase = 0;
						cfg.staff.carrierBackhaul = false;
						cfg.staff.showEmployees = true;
					});
					markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						c.command(player, "aktradeex reputation set 150");
						c.giveMoney(player, 20_000);
						Identifier good = shared(c, a, 8, 0, b);
						var hired = staff(c, player, a, StaffRequestC2S.Action.HIRE_CARRIER, 0, Employee.AUTO, b.id(), 64, 0, 0, 0, false);
						that(hired == TradeResult.SUCCESS, "hiring gave %s", hired);
						StaffS2C.View e = only(c, player, a);
						HiredHands.update(c.level().getServer(), com.akf.tradeexpansion.market.MarketRegistry.get(c.level().getServer()));
						List<HiredHandEntity> hands = hands(c, a);
						equal(1, hands.size(), "employees standing at the market");
						HiredHandEntity hand = hands.getFirst();
						equal(e.id(), hand.employeeId(), "the employee standing there");
						that(hand.hasCustomName() && hand.getCustomName().getString().contains(player.getPlainTextName()), "no name: %s", hand.getCustomName());
						that(hand.isInvulnerable() && !hand.isPushable(), "the employee can be hurt or pushed");
						player.closeContainer();
						c.emptyHand(player);
						player.teleportTo(hand.getX() + 1, hand.getY(), hand.getZ());
						player.interactOn(hand, InteractionHand.MAIN_HAND, Vec3.ZERO);
						that(player.containerMenu instanceof MarketMenu menu && menu.marketId() == a.id() && menu.initialTab().equals("staff:" + e.id()),
								"talking to the employee did not open the staff tab");
						that(player.containerMenu.stillValid(player), "the screen opened by the employee is not valid");
						player.closeContainer();
						ServerPlayer other = c.player(hand.blockPosition().east());
						other.interactOn(hand, InteractionHand.MAIN_HAND, Vec3.ZERO);
						that(!(other.containerMenu instanceof MarketMenu), "someone else's employee opened a trade screen");
						ServerChapterCases.openAt(c, player, m, 0);
						// It leaves on a trip: dear at B.
						a.good(good).setPressure(a.params(good).maxPressure() * 0.8);
						b.good(good).setPressure(b.params(good).minPressure() * 0.8);
						staff(c, player, a, StaffRequestC2S.Action.DEPOSIT_MONEY, e.id(), Employee.AUTO, 0, 0, 0, 0, 5_000, false);
						c.command(player, "aktradeex staff hurry");
						equal(Employee.Phase.OUTBOUND.ordinal(), only(c, player, a).phase(), "phase after buying");
						HiredHands.update(c.level().getServer(), com.akf.tradeexpansion.market.MarketRegistry.get(c.level().getServer()));
						that(hand.isRemoved(), "the carrier still stands at home while on a trip");
						c.command(player, "aktradeex staff hurry");
						c.command(player, "aktradeex staff hurry");
						equal(Employee.Phase.HOME.ordinal(), only(c, player, a).phase(), "phase after the trip");
						HiredHands.update(c.level().getServer(), com.akf.tradeexpansion.market.MarketRegistry.get(c.level().getServer()));
						equal(1, hands(c, a).size(), "employees standing after the trip");
						var dismissed = staff(c, player, a, StaffRequestC2S.Action.DISMISS, e.id(), Employee.AUTO, 0, 0, 0, 0, 0, false);
						that(dismissed == TradeResult.SUCCESS, "dismissing gave %s", dismissed);
						HiredHands.update(c.level().getServer(), com.akf.tradeexpansion.market.MarketRegistry.get(c.level().getServer()));
						equal(0, hands(c, a).size(), "employees standing after the dismissal");
					});
				}));
		return cases;
	}

	private static List<HiredHandEntity> hands(ServerCtx c, Market market) {
		return c.levelOf(market).getEntitiesOfClass(HiredHandEntity.class, new AABB(market.center()).inflate(16), h -> !h.isRemoved());
	}
}
