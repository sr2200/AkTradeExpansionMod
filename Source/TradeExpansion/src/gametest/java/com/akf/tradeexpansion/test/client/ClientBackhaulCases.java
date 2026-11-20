package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.AlsoFailed;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientStaffCases.*;
import static com.akf.tradeexpansion.test.client.ClientStaffMoreCases.*;
import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of return cargo (v0.54) and of the carriers' trades in the trade screen: the destinations' tooltip and
 * expected profit with return cargo (26-26), the same with {@code staff.carrierBackhaul} off (26-30), a carrier that sets
 * out with nothing for its return cargo (26-29), and the price a carrier's purchase moves in the open screen (26-22).
 * They hire their own carriers on the server and let them go again.
 */
final class ClientBackhaulCases {
	private ClientBackhaulCases() {
	}

	/**
	 * 26-26: with return cargo worth buying at the other market, its destination row says "見込み +○c" with the return
	 * cargo added, its tooltip "帰り荷：今なら 約○個（○種類）・見込み +○c", and the setup page explains return cargo.
	 * 26-30 (also): with {@code staff.carrierBackhaul} off neither shows, and the row has the goods' profit only.
	 */
	static void hint(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		fresh(s, c -> c.carrierBackhaul = true);
		Identifier good = carrierGood;
		Identifier back = sharedGood(s, good);
		long otherId = otherId(s);
		prices(s, good, true, true);
		prices(s, back, false, false); // dear here, cheap there: return cargo
		Object staff = openTab(s);
		MarketScreen screen = screen(context);
		StaffS2C.Destination d = destinationOf(context, screen, staff, good, otherId);
		check(d.units() > 0 && d.profit() > 0, "no profit carrying %s to the other market: %s", good, d);
		check(d.returnItems() > 0 && d.returnProfit() > 0, "no return cargo from the other market (%s cheap there, dear here): %s", back, d);
		int row = destinationRow(context, staff, otherId);
		String total = text(K + "hint.profit", coins(d.profit() + d.returnProfit()));
		List<String> texts = rowStrings(context, screen, staff, row);
		check(texts.contains(total), "the destination row does not say \"%s\" (goods %dc + return cargo %dc): %s", total, d.profit(), d.returnProfit(), texts);
		String returnLine = squeeze(text(K + "hint.return", d.returnItems(), d.returnKinds(), coins(d.returnProfit())));
		String tip = rowTooltip(context, screen, staff, row);
		check(tip.contains(returnLine), "the destination's tooltip does not say \"%s\": %s", returnLine, tip);
		int y = showRow(context, screen, staff, row);
		moveTo(context, listX(context, screen) + 60, y + 8);
		context.waitTicks(2);
		screenshot(context, result, "26-26_return_cargo_tooltip");
		mouseAway(context);
		clickRow(context, screen, staff, row);
		check(mode(context, staff).equals("SETUP"), "the destination opened %s, not the setup", mode(context, staff));
		String page = all(context);
		// The explanation is long: the page may cut it at the bottom of the list; its beginning shows.
		String explain = squeeze(text(K + "summary.return")).substring(0, 20);
		check(page.contains(returnLine), "the setup page does not show the return cargo \"%s\": %s", returnLine, page);
		check(page.contains(explain), "the setup page does not explain the return cargo (\"%s…\"): %s", explain, page);
		screenshot(context, result, "26-26_return_cargo_setup");

		// 26-30: return cargo off.
		try {
			fresh(s, c -> c.carrierBackhaul = false);
			prices(s, good, true, true);
			prices(s, back, false, false);
			staff = openTab(s);
			screen = screen(context);
			StaffS2C.Destination off = destinationOf(context, screen, staff, good, otherId);
			check(off.returnItems() <= 0 || off.returnProfit() <= 0, "the server still offers return cargo with carrierBackhaul off: %s", off);
			row = destinationRow(context, staff, otherId);
			String goodsOnly = text(K + "hint.profit", coins(off.profit()));
			texts = rowStrings(context, screen, staff, row);
			check(texts.contains(goodsOnly), "with carrierBackhaul off the destination row does not say \"%s\": %s", goodsOnly, texts);
			String returnPrefix = squeeze(prefix(K + "hint.return"));
			tip = rowTooltip(context, screen, staff, row);
			check(!tip.contains(returnPrefix), "with carrierBackhaul off the tooltip still has \"%s\": %s", returnPrefix, tip);
			clickRow(context, screen, staff, row);
			page = all(context);
			check(!page.contains(explain), "with carrierBackhaul off the setup page still explains the return cargo");
		} catch (Throwable t) {
			throw new AlsoFailed(t);
		} finally {
			fresh(s, c -> {
			});
		}
	}

	/**
	 * 26-29: a carrier whose own good does not pay at the other market sets out with nothing when the return cargo pays;
	 * on the way back its right panel says "荷 ×0個・帰り荷 ×○個" and its log has the return cargo bought; at home it
	 * sells it here.
	 */
	static void emptyTrip(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		fresh(s, c -> {
			c.carrierBackhaul = true;
			c.carrierMaxWaitDays = 0;
		});
		Identifier good = carrierGood;
		Identifier back = sharedGood(s, good);
		long otherId = otherId(s);
		// Money for the fee and the purse (earlier cases left theirs with the employees they let go).
		s.world.getServer().runOnServer(server -> com.akf.tradeexpansion.currency.WalletService.give(player(server), 100_000));
		prices(s, good, false, false); // dear here, cheap there: nothing to gain going
		prices(s, back, false, false); // but return cargo pays
		try {
			Object staff = openTab(s);
			MarketScreen screen = screen(context);
			request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, good, otherId, 8, 1_000_000, 1, 0);
			int id = hired(context, staff, 0);
			request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, good, otherId, 0, 0, 0, 20_000);
			StaffS2C.View v = hurryUntil(s, staff, id, x -> x.phase() == Employee.Phase.OUTBOUND.ordinal(), 3, "on the way with nothing");
			check(v.stock() == 0, "the carrier took %d of %s that does not pay there", v.stock(), good);
			v = hurryUntil(s, staff, id, x -> x.phase() == Employee.Phase.RETURNING.ordinal(), 3, "on the way back");
			check(v.returnItems() > 0, "the carrier bought no return cargo (status %d)", v.status());
			select(context, staff, id);
			String cargo = squeeze(text(K + "cargo.return", 0, v.returnItems()));
			String p = panel(context, screen);
			check(p.contains(cargo), "the panel does not say \"%s\": %s", cargo, p);
			String phase = squeeze(prefix(K + "phase.returning"));
			check(String.join("", rowStrings(context, screen, staff, rowIndex(context, staff, id))).replaceAll("\\s", "").contains(phase),
					"the carrier's row does not say \"%s\"", phase);
			String boughtBack = squeeze(middle(LOG + "bought_return"));
			String tip = rowTooltip(context, screen, staff, rowIndex(context, staff, id));
			check(tip.contains(boughtBack), "the tooltip's log has no return cargo (\"%s\"): %s", boughtBack, tip);
			screenshot(context, result, "26-29_empty_trip_return_cargo");
			// Home, it sells the return cargo and (waiting no longer) sets out again.
			v = hurryUntil(s, staff, id, x -> x.trips() >= 1, 3, "back from its first round trip");
			check(v.earned() > 0, "the return cargo was not sold at home (earned %d)", v.earned());
			String returned = squeeze(middle(LOG + "returned"));
			tip = rowTooltip(context, screen, staff, rowIndex(context, staff, id));
			check(tip.contains(returned), "the tooltip's log has no round trip (\"%s\"): %s", returned, tip);
		} finally {
			fresh(s, c -> {
			});
		}
	}

	/**
	 * 26-22: a carrier buying here moves the price in the open trade screen (the row shows the new price), by
	 * {@code staff.carrierPriceImpact} (0.25) of what the player buying as many would (a cheap good may not move by a whole coin).
	 */
	static void carrierMovesPrice(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		fresh(s, c -> c.carrierMaxWaitDays = 0);
		Identifier good = dearGood(s);
		long otherId = otherId(s);
		// Money for the fee and the purse (earlier cases left theirs with the employees they let go).
		s.world.getServer().runOnServer(server -> com.akf.tradeexpansion.currency.WalletService.give(player(server), 100_000));
		prices(s, good, true, true);
		try {
			// Hired from the staff tab, so the server's answer shows; then the buy tab.
			Object staff = openTab(s);
			MarketScreen screen = screen(context);
			request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, good, otherId, 64, 1_000_000, 1, 0);
			StaffS2C answer = data(context, staff);
			String hireResult = String.valueOf(answer == null ? null : answer.result().orElse(null));
			context.clickScreenButton("screen.ak_trade_expansion.tab.buy");
			context.waitTicks(2);
			MarketSnapshotS2C before = snapshot(context, screen);
			GoodView was = before.goods().stream().filter(v -> v.goods().equals(good)).findFirst().orElseThrow();
			long oldPrice;
			int id = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).employees().stream()
					.filter(e -> e.owner().equals(player(server).getUUID())).findFirst()
					.orElseThrow(() -> new AssertionError("the carrier for " + good + " was not hired: " + hireResult)).id());
			double impact = s.world.getServer().computeOnServer(server -> TradeExpansionConfig.get().staff.carrierPriceImpact);
			long marketId = s.world.getServer().computeOnServer(server -> market(server, s.stall).id());
			// X just before and after the carrier buys, in one server tick (the purse makes it buy at once, v0.70; a
			// hurry if it waited).
			double[] x = s.world.getServer().computeOnServer(server -> {
				var home = market(server, s.stall);
				com.akf.tradeexpansion.market.MarketUpkeep.touch(server.overworld(), home);
				double x0 = home.good(good).pressure();
				com.akf.tradeexpansion.staff.StaffService.handle(player(server),
						new StaffRequestC2S(marketId, StaffRequestC2S.Action.DEPOSIT_MONEY, id, good, otherId, 0, 0, 0, 50_000, false));
				if (stock(server, id) == 0) com.akf.tradeexpansion.staff.StaffService.hurry(server);
				return new double[] {x0, home.good(good).pressure(), stock(server, id)};
			});
			int bought = (int) x[2];
			check(bought > 0, "the carrier bought no %s here", good);
			check(Math.abs((x[0] - x[1]) - bought * impact) < 0.05, "buying %d moved X by %.2f on the server (expected %.2f, a player %d)", bought, x[0] - x[1],
					bought * impact, bought);
			MarketSnapshotS2C after = newSnapshot(context, screen, before);
			GoodView now = after.goods().stream().filter(v -> v.goods().equals(good)).findFirst().orElseThrow();
			// The open screen got the new X (the snapshot's own value, before the client's recovery since).
			check(Math.abs(now.pressure() - x[1]) < 0.3, "the open screen got X %.2f, the server has %.2f after the carrier bought", now.pressure(), x[1]);
			long newPrice = PriceModel.buyPrice(now.params(), now.state().pressure());
			long asPlayer = PriceModel.buyPrice(now.params(), x[0] - bought);
			oldPrice = PriceModel.buyPrice(now.params(), x[0]);
			// A cheap good may not move by a whole coin; the move of X above is the check then.
			check(newPrice >= oldPrice, "the buy price went down after the carrier bought %d (%dc -> %dc)", bought, oldPrice, newPrice);
			if (asPlayer - oldPrice >= 4) {
				check(newPrice > oldPrice, "the buy price did not go up after the carrier bought %d (%dc -> %dc, a player %dc)", bought, oldPrice, newPrice, asPlayer);
				check(newPrice - oldPrice < asPlayer - oldPrice, "the carrier moved the price as much as a player would (%dc -> %dc, a player %dc)", oldPrice, newPrice,
						asPlayer);
			}
			int rowY = showRow(context, screen, good);
			mouseAway(context);
			List<TextRecorder.Text> row = rowTexts(TextRecorder.capture(context), rowY);
			check(strings(row).contains(coins(newPrice)), "the row of %s does not show the new price %s: %s", good, coins(newPrice), strings(row));
			screenshot(context, result, "26-22_carrier_moved_price");
		} finally {
			fresh(s, c -> {
			});
		}
	}

	private static int stock(net.minecraft.server.MinecraftServer server, int id) {
		return MarketRegistry.get(server).employees().stream().filter(e -> e.id() == id).findFirst().orElseThrow().stock();
	}

	/**
	 * The dearest plain good of the stall's market with stock to buy that a carrier may be hired for (the player can see
	 * it; the other market deals in it, and gets it if no such good is shared).
	 */
	private static Identifier dearGood(Session s) {
		return s.world.getServer().computeOnServer(server -> {
			var home = market(server, s.stall);
			var other = other(server, home);
			long now = com.akf.tradeexpansion.market.MarketUpkeep.touch(server.overworld(), other);
			java.util.UUID owner = player(server).getUUID();
			java.util.function.Predicate<Identifier> usable = g -> {
				var def = com.akf.tradeexpansion.data.GoodsCatalog.get(g);
				return def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && !home.isSpecial(g) && !other.isSpecial(g)
						&& home.good(g).stock() >= 32 && com.akf.tradeexpansion.market.Reputation.canSee(home, owner, g, now);
			};
			java.util.Comparator<Identifier> byPrice = java.util.Comparator.comparingDouble(g -> com.akf.tradeexpansion.data.GoodsCatalog.get(g).basePrice());
			Identifier good = home.regularGoods().stream().filter(usable).filter(other.regularGoods()::contains).max(byPrice).orElse(null);
			if (good == null) {
				good = home.regularGoods().stream().filter(usable).max(byPrice).orElseThrow(() -> new AssertionError("no good with stock to buy"));
				other.addGood(good, new com.akf.tradeexpansion.core.GoodState(1.0, 0, com.akf.tradeexpansion.data.GoodsCatalog.get(good).targetStock(), now));
			}
			MarketRegistry.get(server).setDirty();
			return good;
		});
	}
}
