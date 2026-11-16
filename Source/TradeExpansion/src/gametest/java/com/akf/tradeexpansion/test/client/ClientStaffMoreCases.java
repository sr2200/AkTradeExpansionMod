package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.staff.StaffService;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.akf.tradeexpansion.test.client.ClientStaffCases.*;
import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * More client cases of the staff tab (checklist chapter 26, design Q), after {@link ClientStaffCases}: "変更" of a carrier
 * (with goods left unsold and return cargo) and of a clerk, a clerk selling out, "預ける" / "引取", "解雇する" with the
 * mouse, "利益なし", and an accident (resting, then quitting and "受け取る"). The employees are hired through the server
 * (the same request as the tab's buttons; {@link ClientStaffCases} hires them with the mouse) and moved on with
 * {@code /aktradeex staff hurry}. Each case starts without employees; the last one puts the settings back.
 */
final class ClientStaffMoreCases {
	private ClientStaffMoreCases() {
	}

	private static final String NS = "ak_trade_expansion";
	private static final String LOG = "staff." + NS + ".log.";

	/** The fixed part of a translation after its last %s. */
	private static String suffix(String key) {
		String[] parts = Language.getInstance().getOrDefault(key).split("%s", -1);
		return parts[parts.length - 1];
	}

	// ---- set-up

	/** {@link ClientStaffCases#prepare} (once), the settings changed by {@code change}, and no employees of the player. */
	private static void fresh(Session s, Consumer<TradeExpansionConfig.Staff> change) {
		prepare(s);
		s.context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			config.staff.accidentBase = 0;
			config.staff.accidentPerThousandBlocks = 0;
			config.staff.quitChance = 0;
			config.staff.carrierBackhaul = false;
			config.staff.carrierMaxWaitDays = oldConfig.staff.carrierMaxWaitDays;
			change.accept(config.staff);
			ConfigManager.apply(config);
			UUID id = player(server).getUUID();
			MarketRegistry.get(server).employees().removeIf(e -> e.owner().equals(id));
			MarketRegistry.get(server).setDirty();
		});
		s.context.waitTicks(2);
	}

	private static void config(Session s, Consumer<TradeExpansionConfig.Staff> change) {
		s.world.getServer().runOnServer(server -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			change.accept(config.staff);
			ConfigManager.apply(config);
		});
	}

	/**
	 * A staff request of the player at the stall's market, sent on the server (the trade screen must be open: the server
	 * checks the menu). The answer goes to the open tab as for a button.
	 */
	private static void request(Session s, StaffRequestC2S.Action action, int id, Identifier goods, long destination, int quantity, long buyLimit,
			long sellLimit, long amount) {
		long market = s.world.getServer().computeOnServer(server -> market(server, s.stall).id());
		s.world.getServer().runOnServer(server -> StaffService.handle(player(server),
				new StaffRequestC2S(market, action, id, goods, destination, quantity, buyLimit, sellLimit, amount, false)));
		s.context.waitTicks(3);
	}

	/** The id of the player's only employee of {@code role} (0 carrier, 1 clerk), asking for the list until it shows. */
	private static int hired(ClientGameTestContext context, Object staff, int role) {
		check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().anyMatch(v -> v.role() == role), 60), "no employee was hired");
		return data(context, staff).staff().stream().filter(v -> v.role() == role).findFirst().orElseThrow().id();
	}

	private static long otherId(Session s) {
		return s.world.getServer().computeOnServer(server -> other(server, market(server, s.stall)).id());
	}

	/** Sets the pressure of a good at the stall's market and the other market (fractions of the lowest / highest pressure). */
	private static void prices(Session s, Identifier good, boolean cheapHere, boolean dearThere) {
		s.world.getServer().runOnServer(server -> {
			Market home = market(server, s.stall);
			Market other = other(server, home);
			MarketUpkeep.touch(server.overworld(), other);
			home.good(good).setPressure(cheapHere ? home.params(good).maxPressure() * 0.8 : home.params(good).minPressure() * 0.8);
			other.good(good).setPressure(dearThere ? other.params(good).minPressure() * 0.8 : other.params(good).maxPressure() * 0.8);
			MarketRegistry.get(server).setDirty();
		});
	}

	private static StaffS2C.View hurryUntil(Session s, Object staff, int id, java.util.function.Predicate<StaffS2C.View> condition, int times, String what) {
		ClientGameTestContext context = s.context;
		refresh(context, staff);
		context.waitTicks(4);
		StaffS2C.View v = view(context, staff, id);
		if (v != null && condition.test(v)) return v; // the server's own update (every second) may be first
		for (int i = 0; i < times; i++) {
			hurry(s);
			refresh(context, staff);
			context.waitTicks(4);
			v = view(context, staff, id);
			if (v != null && condition.test(v)) return v;
		}
		throw new AssertionError("the employee #" + id + " did not get " + what + " (now " + describe(v) + ")");
	}

	private static String status(ClientGameTestContext context, MarketScreen screen, Object staff, int id) {
		List<String> row = rowStrings(context, screen, staff, rowIndex(context, staff, id));
		return String.join("|", row);
	}

	/** Clicks the tab's row of a good on the pick-goods page ({@code auto}: the おまかせ row is first). */
	private static void pickGood(ClientGameTestContext context, MarketScreen screen, Object staff, Identifier good, boolean auto) {
		check(waitClient(context, c -> get(staff, auto ? "carrierHints" : "clerkHints") != null, 100), "no recommendations of the goods arrived");
		context.waitTicks(2);
		check(mode(context, staff).equals("PICK_GOODS"), "the goods are not shown (%s)", mode(context, staff));
		@SuppressWarnings("unchecked")
		List<GoodView> goods = context.computeOnClient(c -> (List<GoodView>) call(staff, "pickableGoods"));
		int index = -1;
		for (int i = 0; i < goods.size(); i++) if (goods.get(i).goods().equals(good)) index = i;
		check(index >= 0, "%s is not in the goods to choose", good);
		clickRow(context, screen, staff, index + (auto ? 1 : 0));
	}

	/** On the destinations page, clicks the other market; the setup page opens. */
	private static void pickOther(ClientGameTestContext context, MarketScreen screen, Object staff, long otherId) {
		check(mode(context, staff).equals("PICK_DESTINATION"), "the destinations are not shown (%s)", mode(context, staff));
		@SuppressWarnings("unchecked")
		boolean listed = waitClient(context, c -> !((List<StaffS2C.Destination>) call(staff, "sortedDestinations")).isEmpty(), 100);
		check(listed, "no destinations arrived");
		@SuppressWarnings("unchecked")
		List<StaffS2C.Destination> destinations = context.computeOnClient(c -> (List<StaffS2C.Destination>) call(staff, "sortedDestinations"));
		int di = -1;
		for (int i = 0; i < destinations.size(); i++) if (destinations.get(i).id() == otherId) di = i;
		check(di >= 0, "the other market is not a destination");
		clickRow(context, screen, staff, di);
		check(mode(context, staff).equals("SETUP"), "the destination opened %s, not the setup", mode(context, staff));
	}

	private static void apply(ClientGameTestContext context, Object staff) {
		clickWidget(context, text(K + "apply"));
		mouseAway(context);
		check(waitClient(context, c -> ((Enum<?>) get(staff, "mode")).name().equals("LIST"), 60), "\"%s\" did not go back to the list", text(K + "apply"));
		context.waitTicks(3);
	}

	/** A good of both markets other than {@code not} (added to the other market when they share none, as {@link ClientStaffCases#prepare}). */
	private static Identifier sharedGood(Session s, Identifier not) {
		return s.world.getServer().computeOnServer(server -> {
			Market home = market(server, s.stall);
			Market other = other(server, home);
			long now = MarketUpkeep.touch(server.overworld(), other);
			java.util.function.Predicate<Identifier> usable = g -> {
				GoodsDefinition def = GoodsCatalog.get(g);
				return def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && !g.equals(not) && !home.isSpecial(g)
						&& !other.isSpecial(g) && home.good(g).stock() >= 20;
			};
			Identifier good = home.regularGoods().stream().filter(usable).filter(g -> other.regularGoods().contains(g) && other.good(g).stock() >= 20)
					.findFirst().orElse(null);
			if (good == null) {
				good = home.regularGoods().stream().filter(usable).findFirst().orElseThrow(() -> new AssertionError("no second good to carry"));
				other.addGood(good, new GoodState(1.0, 0, GoodsCatalog.get(good).targetStock(), now));
			}
			return good;
		});
	}

	// ---- 26-23 / 26-27 / 26-28 / 26-16 a carrier changed and dismissed

	/**
	 * A carrier that could not sell at the other market (its lowest price too high, no waiting there) comes back with its
	 * goods and with return cargo it cannot sell at home: the panel says "荷 ×N個・帰り荷 ×M個" and the log has the return
	 * cargo (26-27). "変更" → おまかせ → the other market → the setup says the goods come back → "変える": every item comes
	 * back (26-28) and the row is おまかせ; "変更" again to a good with the recommendation (26-23). Then "解雇する" with
	 * the mouse: the purse comes back and the row goes (26-16).
	 */
	static void carrierChange(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		fresh(s, c -> {
			c.carrierBackhaul = true;
			c.carrierMaxWaitDays = 0;
		});
		Identifier good = carrierGood;
		Identifier back = sharedGood(s, good);
		long otherId = otherId(s);
		prices(s, good, true, true);
		prices(s, back, false, false); // dear here, cheap there: return cargo
		Object staff = ClientStaffCases.openTab(s);
		MarketScreen screen = screen(context);
		request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, good, otherId, 8, 1_000_000, 1_000_000_000L, 0);
		int id = hired(context, staff, 0);
		request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, good, otherId, 0, 0, 0, 20_000);
		StaffS2C.View v = hurryUntil(s, staff, id, x -> x.phase() == 1, 3, "on the way");
		check(v.stock() > 0, "the carrier left with nothing");
		// Room for return cargo; it stays paused when it is back.
		request(s, StaffRequestC2S.Action.CONFIGURE, id, good, otherId, 64, 1_000_000, 1_000_000_000L, 0);
		request(s, StaffRequestC2S.Action.PAUSE, id, good, otherId, 0, 0, 0, 0);
		v = hurryUntil(s, staff, id, x -> x.phase() == 3, 3, "on the way back");
		check(v.stock() > 0, "the carrier sold its goods (the lowest price is too high for that)");
		check(v.returnItems() > 0, "the carrier bought no return cargo (status %d)", v.status());
		s.world.getServer().runOnServer(server -> {
			Market home = market(server, s.stall);
			home.good(back).setPressure(home.params(back).maxPressure()); // cheap here now: the return cargo does not sell
			MarketRegistry.get(server).setDirty();
		});
		v = hurryUntil(s, staff, id, x -> x.phase() == 0, 3, "home");
		check(v.stock() > 0 && v.returnItems() > 0, "the carrier came home without its goods or return cargo (%s)", describe(v));
		select(context, staff, id);
		String cargo = squeeze(text(K + "cargo.return", v.stock() * v.unit(), v.returnItems()));
		String p = panel(context, screen);
		check(p.contains(cargo), "the panel does not say \"%s\": %s", cargo, p);
		String boughtBack = squeeze(middle(LOG + "bought_return"));
		String tip = rowTooltip(context, screen, staff, rowIndex(context, staff, id));
		check(tip.contains(boughtBack), "the tooltip's log has no return cargo (\"%s\"): %s", boughtBack, tip);
		screenshot(context, result, "26-27_return_cargo");

		// 26-23 / 26-28: to おまかせ; everything carried comes back.
		long returned = (long) v.stock() * v.unit() + v.returnItems();
		int goodBefore = serverCount(s, good);
		int backBefore = serverCount(s, back);
		int goodStock = v.stock() * v.unit();
		check(button(staff, "changeButton").active, "\"%s\" cannot be pressed at home", text(K + "change"));
		clickWidget(context, text(K + "change"));
		mouseAway(context);
		check(waitClient(context, c -> get(staff, "carrierHints") != null, 100), "no recommendations of the goods arrived");
		context.waitTicks(2);
		clickRow(context, screen, staff, 0);
		pickOther(context, screen, staff, otherId);
		String back1 = squeeze(text(K + "summary.change", returned));
		String page = all(context);
		check(page.contains(back1), "the setup page does not say \"%s\": %s", back1, page);
		screenshot(context, result, "26-23_change_setup");
		apply(context, staff);
		String done = squeeze(text(K + "done.change", returned));
		check(all(context).contains(done), "the status line does not say \"%s\"", done);
		v = view(context, staff, id);
		check(v.auto() && v.destination() == otherId && v.stock() == 0 && v.returnItems() == 0, "after the change: %s, auto %s", describe(v), v.auto());
		check(serverCount(s, good) == goodBefore + goodStock, "%d of %s carried after the change (%d + %d expected)", serverCount(s, good), good, goodBefore, goodStock);
		check(serverCount(s, back) > backBefore, "the return cargo (%s) did not come back", back);
		String autoName = text("staff." + NS + ".auto");
		String row = status(context, screen, staff, id);
		check(row.contains(autoName), "the row does not say \"%s\": %s", autoName, row);

		// Back to a good of its own, with today's recommendation.
		select(context, staff, id);
		clickWidget(context, text(K + "change"));
		mouseAway(context);
		pickGood(context, screen, staff, good, true);
		pickOther(context, screen, staff, otherId);
		page = all(context);
		check(page.contains(squeeze(prefix(K + "hint.recommend"))) || page.contains(squeeze(text(K + "hint.carrier.none"))),
				"the setup page shows no recommendation: %s", page);
		apply(context, staff);
		v = view(context, staff, id);
		check(!v.auto() && v.goods().equals(good) && v.destination() == otherId, "after the second change the carrier carries %s to #%d", v.goods(), v.destination());
		screenshot(context, result, "26-23_changed");

		// 26-16: dismissed with the mouse.
		select(context, staff, id);
		long purse = view(context, staff, id).purse();
		long before = serverMoney(s);
		clickWidget(context, text(K + "dismiss"));
		mouseAway(context);
		check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().noneMatch(x -> x.id() == id), 60), "the carrier is still listed after \"%s\"",
				text(K + "dismiss"));
		context.waitTicks(2);
		check(serverMoney(s) - before == purse, "dismissing gave back %dc, the purse was %dc", serverMoney(s) - before, purse);
		String dismissed = squeeze(text(K + "done.dismiss", coins(purse)));
		check(all(context).contains(dismissed), "the status line does not say \"%s\"", dismissed);
		check(shownWithText(context, text(K + "hire.carrier")), "the hire buttons do not show after dismissing");
	}

	// ---- 26-24 / 26-12 / 26-16 a clerk changed, sold out, given goods and dismissed

	/**
	 * 26-24: a clerk's "変更" to another good: the setup starts at every unit carried, "−" takes some off, "変える" gives the
	 * old goods back and takes the new ones. 26-12: with a lowest price of 1c it sells out: "売り切れ" and a chat line;
	 * "設定" for a high lowest price, "預ける" gives it the goods left and it waits again; "変更" to the same good offers
	 * what it holds; "引取" gives them back. 26-16: "解雇する" gives the purse back.
	 */
	static void clerkChange(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		fresh(s, c -> {
		});
		Object staff = ClientStaffCases.openTab(s);
		MarketScreen screen = screen(context);
		StaffS2C data = data(context, staff);
		List<Identifier> goods = ClientUtil.snapshot(context, screen).goods().stream()
				.filter(v -> data.clerkGoods().contains(v.goods()) && v.unit() == 1 && v.patch().isEmpty() && !v.displayStack().isDamageableItem()
						&& !v.goods().equals(carrierGood))
				.map(GoodView::goods).limit(2).toList();
		check(goods.size() == 2, "fewer than 2 goods to leave with a clerk");
		Identifier first = goods.get(0), second = goods.get(1);
		context.setScreen(() -> null);
		int firstHeld = serverCount(s, first);
		int secondHeld = serverCount(s, second);
		s.world.getServer().runOnServer(server -> {
			giveItems(player(server), item(first), 10);
			giveItems(player(server), item(second), 6);
		});
		context.waitTicks(3);
		staff = ClientStaffCases.openTab(s);
		Object tab = staff;
		screen = screen(context);
		request(s, StaffRequestC2S.Action.HIRE_CLERK, -1, first, -1, 5, 0, 1_000_000_000L, 0);
		int id = hired(context, staff, 1);
		request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, first, -1, 0, 0, 0, 1_000);
		check(serverCount(s, first) == firstHeld + 5, "the clerk did not take 5 of %s", first);
		select(context, staff, id);
		String stock = squeeze(text(K + "stock", 5));
		check(panel(context, screen).contains(stock), "the panel does not say \"%s\"", stock);

		// 26-24: another good.
		clickWidget(context, text(K + "change"));
		mouseAway(context);
		pickGood(context, screen, staff, second, false);
		check(mode(context, staff).equals("SETUP"), "the good opened %s, not the setup", mode(context, staff));
		int quantity = context.computeOnClient(c -> (Integer) get(tab, "quantity"));
		check(quantity == secondHeld + 6, "the setup starts at %d, the player carries %d", quantity, secondHeld + 6);
		Button[][] spinners = context.computeOnClient(c -> (Button[][]) get(tab, "spinners"));
		clickWidget(context, spinners[0][0]);
		clickWidget(context, spinners[0][0]);
		mouseAway(context);
		int lowered = context.computeOnClient(c -> (Integer) get(tab, "quantity"));
		check(lowered == quantity - 2, "two clicks on \"-\" gave %d, not %d", lowered, quantity - 2);
		String back = squeeze(text(K + "summary.change", 5));
		String page = all(context);
		check(page.contains(back), "the setup page does not say \"%s\": %s", back, page);
		// A lowest price it does not reach yet (the server updates every second): 1c is set after the checks.
		context.runOnClient(c -> {
			set(tab, "sellLimit", 1_000_000_000L);
			call(c.gui.screen(), "updateButtons");
		});
		screenshot(context, result, "26-24_change_setup");
		apply(context, staff);
		check(all(context).contains(squeeze(text(K + "done.change", 5))), "the status line does not say \"%s\"", text(K + "done.change", 5));
		StaffS2C.View v = view(context, staff, id);
		check(v.goods().equals(second) && v.stock() == lowered, "after the change the clerk holds %d of %s", v.stock(), v.goods());
		check(serverCount(s, first) == firstHeld + 10, "%d of %s carried after the change (the 5 should come back)", serverCount(s, first), first);
		check(serverCount(s, second) == secondHeld + 6 - lowered, "%d of %s carried after the change", serverCount(s, second), second);

		// 26-12: sold out.
		context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
		request(s, StaffRequestC2S.Action.CONFIGURE, id, second, -1, 0, 0, 1, 0);
		v = hurryUntil(s, staff, id, x -> x.status() == 8, 6, "\"売り切れ\"");
		check(v.stock() == 0, "sold out with %d left", v.stock());
		String soldOut = text(K + "status.sold_out");
		check(status(context, screen, staff, id).contains(soldOut), "the row does not say \"%s\"", soldOut);
		String start = prefix(LOG + "clerk_sold_out"), end = suffix(LOG + "clerk_sold_out");
		check(waitClient(context, c -> ClientStaffCases.chat(c).stream().map(m -> m.content().getString()).anyMatch(t -> t.contains(start) && t.endsWith(end)), 60),
				"no chat line \"%s…%s\"", start, end);
		screenshot(context, result, "26-12_sold_out");

		// A high lowest price, then the goods left: it waits again.
		select(context, staff, id);
		clickWidget(context, text(K + "configure"));
		check(mode(context, staff).equals("SETUP"), "\"%s\" opened %s", text(K + "configure"), mode(context, staff));
		context.runOnClient(c -> {
			set(tab, "sellLimit", 1_000_000_000L);
			call(c.gui.screen(), "updateButtons");
		});
		apply(context, staff);
		select(context, staff, id);
		int left = serverCount(s, second) - secondHeld;
		check(button(staff, "depositItemsButton").active, "\"%s\" cannot be pressed with %d carried", text(K + "deposit_items"), left);
		clickWidget(context, text(K + "deposit_items"));
		mouseAway(context);
		v = waitView(context, staff, id, x -> x.stock() > 0 && x.status() != 8, 200, "the goods and work again");
		check(v.stock() == left + secondHeld, "the clerk took %d, the player carried %d", v.stock(), left + secondHeld);
		check(all(context).contains(squeeze(text(K + "done.deposit_items", v.stock()))), "the status line does not say \"%s\"",
				text(K + "done.deposit_items", v.stock()));
		check(!status(context, screen, staff, id).contains(soldOut), "the row still says \"%s\"", soldOut);

		// "変更" to the same good offers what it holds and what the player carries.
		int holds = v.stock();
		clickWidget(context, text(K + "change"));
		mouseAway(context);
		pickGood(context, screen, staff, second, false);
		int again = context.computeOnClient(c -> (Integer) get(tab, "quantity"));
		check(again == holds, "changing to the same good starts at %d, the clerk holds %d (the player carries none)", again, holds);
		clickWidget(context, text(K + "back"));
		clickWidget(context, text(K + "back"));
		mouseAway(context);
		check(mode(context, staff).equals("LIST"), "\"%s\" twice shows %s", text(K + "back"), mode(context, staff));

		// "引取" (it held everything the player carried).
		select(context, staff, id);
		clickWidget(context, text(K + "withdraw_items"));
		mouseAway(context);
		waitView(context, staff, id, x -> x.stock() == 0, 60, "its goods taken back");
		check(serverCount(s, second) == holds, "%d of %s carried after \"%s\"", serverCount(s, second), second, text(K + "withdraw_items"));
		check(all(context).contains(squeeze(text(K + "done.withdraw_items", holds))), "the status line does not say \"%s\"", text(K + "done.withdraw_items", holds));

		// 26-16.
		long purse = view(context, staff, id).purse();
		long before = serverMoney(s);
		clickWidget(context, text(K + "dismiss"));
		mouseAway(context);
		check(waitClient(context, c -> ((StaffS2C) get(tab, "data")).staff().stream().noneMatch(x -> x.id() == id), 60), "the clerk is still listed after \"%s\"",
				text(K + "dismiss"));
		context.waitTicks(2);
		check(serverMoney(s) - before == purse, "dismissing gave back %dc, the purse was %dc", serverMoney(s) - before, purse);
	}

	// ---- 26-6 / 26-10 no margin, an accident, quitting

	/**
	 * 26-6: a carrier with nothing to gain at the other market says "利益なし" and buys nothing. 26-10: with an accident on
	 * every loaded trip ({@code staff.accidentBase} 1) a chat line "[雇い人] 運び屋：道中で事故に遭い…", part of the goods
	 * gone and "療養 N分"; with {@code staff.quitChance} 1 the next accident makes it quit: "辞めた", the chat line, and
	 * "受け取る" gives the purse back. Puts the settings back at the end.
	 */
	static void accident(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		try {
			fresh(s, c -> {
				c.accidentBase = 1;
				c.accidentMax = 1;
				c.carrierMaxWaitDays = 0;
			});
			Identifier good = carrierGood;
			long otherId = otherId(s);
			prices(s, good, false, false); // dear here, cheap there
			Object staff = ClientStaffCases.openTab(s);
			MarketScreen screen = screen(context);
			request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, good, otherId, 16, 1_000_000, 1, 0);
			int id = hired(context, staff, 0);
			request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, good, otherId, 0, 0, 0, 20_000);
			StaffS2C.View v = hurryUntil(s, staff, id, x -> x.status() == 12, 3, "\"利益なし\"");
			check(v.stock() == 0 && v.phase() == 0, "with no margin the carrier bought or left (%s)", describe(v));
			String noMargin = text(K + "status.no_margin");
			check(status(context, screen, staff, id).contains(noMargin), "the row does not say \"%s\"", noMargin);
			screenshot(context, result, "26-6_no_margin");

			// 26-10: an accident.
			prices(s, good, true, true);
			v = hurryUntil(s, staff, id, x -> x.phase() == 1, 3, "on the way");
			int carried = v.stock();
			check(carried > 0, "the carrier left with nothing");
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			v = hurryUntil(s, staff, id, x -> x.status() == 9, 3, "\"療養\" after an accident");
			check(v.stock() < carried && v.lost() > 0, "the accident took nothing (%d of %d left)", v.stock(), carried);
			String resting = prefix(K + "status.resting");
			String row = status(context, screen, staff, id);
			check(row.contains(resting) && row.contains(suffix(K + "status.resting")), "the row does not say \"%sN%s\": %s", resting, suffix(K + "status.resting"), row);
			String chatStart = text("staff." + NS + ".chat", net.minecraft.network.chat.Component.translatable("staff." + NS + ".role.carrier"), "");
			String accident = prefix(LOG + "accident");
			check(waitClient(context, c -> ClientStaffCases.chat(c).stream().map(m -> m.content().getString())
					.anyMatch(t -> t.startsWith(chatStart) && t.contains(accident)), 60), "no chat line \"%s%s…\"", chatStart, accident);
			screenshot(context, result, "26-10_resting");

			// Quitting after the next accident.
			config(s, c -> c.quitChance = 1);
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			prices(s, good, true, true);
			v = hurryUntil(s, staff, id, x -> x.phase() == 4, 8, "quit");
			String quit = text(K + "status.quit");
			check(status(context, screen, staff, id).contains(quit), "the row does not say \"%s\"", quit);
			String quitLine = text(LOG + "quit");
			check(waitClient(context, c -> ClientStaffCases.chat(c).stream().anyMatch(m -> m.content().getString().contains(quitLine)), 60),
					"no chat line with \"%s\"", quitLine);
			select(context, staff, id);
			String settle = text(K + "settle");
			check(shownWithText(context, settle), "the quit carrier's button does not say \"%s\"", settle);
			screenshot(context, result, "26-10_quit");
			long purse = view(context, staff, id).purse();
			long before = serverMoney(s);
			clickWidget(context, settle);
			mouseAway(context);
			check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().noneMatch(x -> x.id() == id), 60), "the carrier is still listed after \"%s\"",
					settle);
			context.waitTicks(2);
			check(serverMoney(s) - before == purse, "\"%s\" gave %dc, the purse was %dc", settle, serverMoney(s) - before, purse);
		} finally {
			context.setScreen(() -> null);
			cleanUp(s);
		}
	}
}
