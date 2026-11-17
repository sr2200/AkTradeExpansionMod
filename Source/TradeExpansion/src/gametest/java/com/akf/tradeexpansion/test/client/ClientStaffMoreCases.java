package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.StaffMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.development.FacilityKind;
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
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	/** A good of both markets, none of {@code not} (added to the other market when they share none, as {@link ClientStaffCases#prepare}). */
	private static Identifier sharedGood(Session s, Identifier... not) {
		return s.world.getServer().computeOnServer(server -> {
			Market home = market(server, s.stall);
			Market other = other(server, home);
			long now = MarketUpkeep.touch(server.overworld(), other);
			java.util.function.Predicate<Identifier> usable = g -> {
				GoodsDefinition def = GoodsCatalog.get(g);
				return def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && !List.of(not).contains(g) && !home.isSpecial(g)
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

	/**
	 * Puts {@code stock} units of its own good and return cargo ({@code items} by good) on the player's carrier at home,
	 * paused (so it does not set out), on the server. @return the items it holds by good
	 */
	private static Map<Identifier, Integer> load(Session s, int id, int stock, Map<Identifier, Integer> items) {
		return s.world.getServer().computeOnServer(server -> {
			Employee e = MarketRegistry.get(server).employees().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
			check(e.phase() == Employee.Phase.HOME, "the carrier is not at home (%s)", e.phase());
			set(e, "stock", stock);
			set(e, "paused", true);
			@SuppressWarnings("unchecked")
			List<Object> lines = (List<Object>) get(e, Employee.class, "returnCargo");
			lines.clear();
			Map<Identifier, Integer> held = new LinkedHashMap<>();
			held.put(e.goods(), stock * Math.max(1, GoodsCatalog.get(e.goods()).unit()));
			try {
				var make = Employee.CargoLine.class.getDeclaredConstructor(Identifier.class, int.class, long.class);
				make.setAccessible(true);
				for (var entry : items.entrySet()) {
					int unit = Math.max(1, GoodsCatalog.get(entry.getKey()).unit());
					check(entry.getValue() % unit == 0, "%d of %s is not whole units", entry.getValue(), entry.getKey());
					lines.add(make.newInstance(entry.getKey(), entry.getValue() / unit, 0L));
					held.merge(entry.getKey(), entry.getValue(), Integer::sum);
				}
			} catch (ReflectiveOperationException ex) {
				throw new AssertionError("cannot make a cargo line", ex);
			}
			MarketRegistry.get(server).setDirty();
			return held;
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

		// 26-28 / 26-16: dismissed with the mouse, holding goods and return cargo of two kinds (put on the server: a third
		// trip is slow); everything comes back by kind.
		Identifier third = sharedGood(s, good, back);
		Map<Identifier, Integer> held = load(s, id, 3, Map.of(back, 5, third, 4));
		Map<Identifier, Integer> counts = new LinkedHashMap<>();
		for (Identifier g : held.keySet()) counts.put(g, serverCount(s, g));
		v = waitView(context, staff, id, x -> x.stock() == 3 && x.returnItems() == 9, 100, "the goods put on the server");
		select(context, staff, id);
		cargo = squeeze(text(K + "cargo.return", v.stock() * v.unit(), v.returnItems()));
		p = panel(context, screen);
		check(p.contains(cargo), "the panel does not say \"%s\": %s", cargo, p);
		long purse = view(context, staff, id).purse();
		long before = serverMoney(s);
		clickWidget(context, text(K + "dismiss"));
		mouseAway(context);
		check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().noneMatch(x -> x.id() == id), 60), "the carrier is still listed after \"%s\"",
				text(K + "dismiss"));
		context.waitTicks(2);
		check(serverMoney(s) - before == purse, "dismissing gave back %dc, the purse was %dc", serverMoney(s) - before, purse);
		for (var entry : held.entrySet()) {
			int now = serverCount(s, entry.getKey());
			check(now - counts.get(entry.getKey()) == entry.getValue(), "dismissing gave %d of %s, the carrier held %d", now - counts.get(entry.getKey()),
					entry.getKey(), entry.getValue());
		}
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

	// ---- 26-8 / 26-27 / 26-28 an automatic carrier with return cargo

	/**
	 * The first log line (newest first) matching the translation of {@code key} (each %s any text): the line, then its
	 * arguments; null when none matches.
	 */
	private static List<String> logLine(StaffS2C.View v, String key) {
		String[] parts = Language.getInstance().getOrDefault(key).split("%s", -1);
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) regex.append("(.+?)");
			regex.append(Pattern.quote(parts[i]));
		}
		Pattern pattern = Pattern.compile(regex.toString());
		for (Component line : v.log()) {
			Matcher m = pattern.matcher(line.getString());
			if (!m.matches()) continue;
			List<String> found = new ArrayList<>();
			found.add(line.getString());
			for (int i = 1; i <= m.groupCount(); i++) found.add(m.group(i));
			return found;
		}
		return null;
	}

	private static String logs(StaffS2C.View v) {
		return v.log().stream().map(Component::getString).toList().toString();
	}

	/** Items of the employee's cargo ({@code "cargo"}) or return cargo ({@code "returnCargo"}) by good, read on the server. */
	private static Map<Identifier, Integer> lines(Session s, int id, String field) {
		return s.world.getServer().computeOnServer(server -> {
			Employee e = MarketRegistry.get(server).employees().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
			Map<Identifier, Integer> items = new LinkedHashMap<>();
			for (Object line : (List<?>) get(e, Employee.class, field)) {
				Identifier good = (Identifier) get(line, "goods");
				items.merge(good, (Integer) get(line, "units") * Math.max(1, GoodsCatalog.get(good).unit()), Integer::sum);
			}
			return items;
		});
	}

	/** Makes every good of the employee's {@code field} cheap ({@code cheap}) or dear at the stall's market or the other one. */
	private static void pressureOf(Session s, int id, String field, boolean home, boolean cheap) {
		Set<Identifier> goods = lines(s, id, field).keySet();
		s.world.getServer().runOnServer(server -> {
			Market here = market(server, s.stall);
			Market m = home ? here : other(server, here);
			MarketUpkeep.touch(server.overworld(), m);
			for (Identifier g : goods) {
				if (!m.regularGoods().contains(g)) continue;
				m.good(g).setPressure(cheap ? m.params(g).maxPressure() : m.params(g).minPressure() * 0.8);
			}
			MarketRegistry.get(server).setDirty();
		});
	}

	/**
	 * 26-8: an automatic carrier (hired through the server) buys several goods: on the way the row's tooltip says "品は
	 * おまかせ（今の荷 N種類）" and the log "N種類 ×M を ○c で仕入れ、「…」へ出発"; it sells them there ("「…」でN種類 ×M を
	 * ○c で売った"). 26-27: it buys return cargo there ("「…」で帰り荷 …") and sells it at home ("「（雇った村）」で…売った").
	 * The second trip it cannot sell (cheap there) and its return cargo does not sell at home: "引取" gives every good
	 * back by kind (26-8 / 26-28). Then "出金" gives the purse back and cannot be pressed with an empty purse.
	 */
	static void autoCarrier(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		fresh(s, c -> {
			c.carrierBackhaul = true;
			c.carrierMaxWaitDays = 0;
		});
		Identifier good = carrierGood;
		Identifier second = sharedGood(s, good);
		long otherId = otherId(s);
		prices(s, good, true, true);
		prices(s, second, true, true);
		Object staff = ClientStaffCases.openTab(s);
		MarketScreen screen = screen(context);
		request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, Employee.AUTO, otherId, 8, 1, 1, 0);
		int id = hired(context, staff, 0);
		check(view(context, staff, id).auto(), "the carrier is not おまかせ");
		request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, Employee.AUTO, otherId, 0, 0, 0, 20_000);
		StaffS2C.View v = hurryUntil(s, staff, id, x -> x.phase() == 1, 3, "on the way");
		request(s, StaffRequestC2S.Action.PAUSE, id, Employee.AUTO, otherId, 0, 0, 0, 0); // stays home when it is back
		refresh(context, staff);
		context.waitTicks(4);
		v = view(context, staff, id);
		check(v.kinds() > 0 && v.stock() > 0, "the carrier left with nothing (%s)", describe(v));
		String destination = v.destinationName().getString();
		String route = squeeze(text(K + "tooltip.route.auto", v.destinationName(), v.distance(), v.quantity(), v.kinds()));
		String tip = rowTooltip(context, screen, staff, rowIndex(context, staff, id));
		check(tip.contains(route), "the tooltip does not say \"%s\": %s", route, tip);
		List<String> bought = logLine(v, LOG + "bought_mix");
		check(bought != null, "no log line \"%s\": %s", Language.getInstance().getOrDefault(LOG + "bought_mix"), logs(v));
		check(bought.get(1).equals(String.valueOf(v.kinds())) && bought.get(2).equals(String.valueOf(v.stock())) && bought.get(4).equals(destination),
				"the log says \"%s\", the carrier has %d kinds, %d items for %s", bought.get(0), v.kinds(), v.stock(), destination);
		check(tip.contains(squeeze(bought.get(0))), "the tooltip's log has no \"%s\": %s", bought.get(0), tip);
		screenshot(context, result, "26-8_auto_outbound");

		// Sold there, return cargo bought there and sold at home.
		Identifier back = sharedGood(s, good, second);
		prices(s, back, false, false);
		v = hurryUntil(s, staff, id, x -> x.phase() == 3, 3, "on the way back");
		List<String> soldThere = logLine(v, LOG + "sold_mix");
		check(soldThere != null && soldThere.get(1).equals(destination), "no log line \"「%s」で…売った\": %s", destination, logs(v));
		check(v.returnItems() > 0, "the carrier bought no return cargo: %s", logs(v));
		List<String> backBought = logLine(v, LOG + "bought_return");
		check(backBought != null && backBought.get(1).equals(destination) && backBought.get(3).equals(String.valueOf(v.returnItems())),
				"no log line \"「%s」で帰り荷 …×%d…\": %s", destination, v.returnItems(), logs(v));
		v = hurryUntil(s, staff, id, x -> x.phase() == 0, 3, "home");
		String home = v.homeName().getString();
		List<String> soldHome = logLine(v, LOG + "sold_mix");
		check(soldHome != null && soldHome.get(1).equals(home), "no log line \"「%s」で…売った\" (the return cargo at home): %s", home, logs(v));
		check(logLine(v, LOG + "returned") != null, "no log line of the round trip: %s", logs(v));
		tip = rowTooltip(context, screen, staff, rowIndex(context, staff, id));
		for (String line : List.of(soldThere.get(0), backBought.get(0), soldHome.get(0))) {
			check(tip.contains(squeeze(line)), "the tooltip's log has no \"%s\": %s", line, tip);
		}
		screenshot(context, result, "26-27_auto_back");

		// The second trip: nothing sells there, the return cargo does not sell at home.
		prices(s, good, true, true);
		prices(s, second, true, true);
		prices(s, back, false, false);
		request(s, StaffRequestC2S.Action.PAUSE, id, Employee.AUTO, otherId, 0, 0, 0, 0);
		hurryUntil(s, staff, id, x -> x.phase() == 1, 3, "on the way again");
		request(s, StaffRequestC2S.Action.PAUSE, id, Employee.AUTO, otherId, 0, 0, 0, 0);
		request(s, StaffRequestC2S.Action.CONFIGURE, id, Employee.AUTO, otherId, 40, 1, 1, 0); // room for return cargo
		pressureOf(s, id, "cargo", false, true);
		v = hurryUntil(s, staff, id, x -> x.phase() == 3, 3, "on the way back again");
		check(v.stock() > 0 && v.returnItems() > 0, "the carrier comes back without goods or return cargo (%s, return %d): %s", describe(v), v.returnItems(),
				logs(v));
		pressureOf(s, id, "returnCargo", true, true);
		v = hurryUntil(s, staff, id, x -> x.phase() == 0, 3, "home again");
		Map<Identifier, Integer> expected = new LinkedHashMap<>(lines(s, id, "cargo"));
		lines(s, id, "returnCargo").forEach((g, n) -> expected.merge(g, n, Integer::sum));
		check(expected.size() >= 2, "the carrier holds only %s (two kinds or more expected)", expected);
		long total = expected.values().stream().mapToLong(Integer::longValue).sum();
		check(total == (long) v.stock() + v.returnItems(), "the server holds %d items, the tab shows %d + %d", total, v.stock(), v.returnItems());
		Map<Identifier, Integer> before = new LinkedHashMap<>();
		for (Identifier g : expected.keySet()) before.put(g, serverCount(s, g));
		select(context, staff, id);
		String cargo = squeeze(text(K + "cargo.return", v.stock(), v.returnItems()));
		String p = panel(context, screen);
		check(p.contains(cargo), "the panel does not say \"%s\": %s", cargo, p);
		screenshot(context, result, "26-28_before_withdraw");
		check(button(staff, "withdrawItemsButton").active, "\"%s\" cannot be pressed with goods", text(K + "withdraw_items"));
		clickWidget(context, text(K + "withdraw_items"));
		mouseAway(context);
		waitView(context, staff, id, x -> x.stock() == 0 && x.returnItems() == 0, 60, "its goods taken back");
		for (var entry : expected.entrySet()) {
			int now = serverCount(s, entry.getKey());
			check(now - before.get(entry.getKey()) == entry.getValue(), "\"%s\" gave %d of %s, the carrier held %d", text(K + "withdraw_items"),
					now - before.get(entry.getKey()), entry.getKey(), entry.getValue());
		}
		String done = squeeze(text(K + "done.withdraw_items", total));
		check(all(context).contains(done), "the status line does not say \"%s\"", done);
		check(!button(staff, "withdrawItemsButton").active, "\"%s\" can still be pressed with nothing held", text(K + "withdraw_items"));

		// "出金".
		long purse = view(context, staff, id).purse();
		check(purse > 0 && button(staff, "withdrawButton").active, "\"%s\" cannot be pressed with %dc", text(K + "withdraw"), purse);
		long money = serverMoney(s);
		clickWidget(context, text(K + "withdraw"));
		mouseAway(context);
		waitView(context, staff, id, x -> x.purse() == 0, 60, "an empty purse");
		check(serverMoney(s) - money == purse, "\"%s\" gave %dc, the purse was %dc", text(K + "withdraw"), serverMoney(s) - money, purse);
		check(!button(staff, "withdrawButton").active, "\"%s\" can be pressed with an empty purse", text(K + "withdraw"));
		screenshot(context, result, "26-12_withdrawn");
	}

	// ---- 13-10 the market clock with the screen open

	/** The number in a squeezed text where {@code key}'s %s is (the first match), or -1. */
	private static int number(String squeezed, String key) {
		String[] parts = Language.getInstance().getOrDefault(key).split("%s", -1);
		Matcher m = Pattern.compile(Pattern.quote(squeeze(parts[0])) + "(\\d+)" + Pattern.quote(squeeze(parts[1]))).matcher(squeezed);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	/** {@code /aktradeex clock advance <days>}; waits until the client has the new offset. */
	private static void advance(Session s, String days) {
		long old = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).clockOffset());
		s.world.getServer().runCommand("aktradeex clock advance " + days);
		long now = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).clockOffset());
		check(now > old, "\"clock advance %s\" did not move the clock", days);
		check(waitClient(s.context, c -> com.akf.tradeexpansion.market.MarketClock.offset(c.level) == now, 100), "the client did not get the new clock offset");
		s.context.waitTicks(3);
	}

	/**
	 * 13-10: {@code /aktradeex clock advance 0.1} (2 minutes of the shown times) while the trade screen is open: the
	 * carrier's "→運送 N分" on the staff tab and "建設中 約N分" on the development tab go down by 2 at once, without
	 * opening the screen again. The building is made on the server without its blocks (as {@link ClientTownCases}).
	 */
	static void clockAdvance(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		BlockPos stall = s.stall;
		fresh(s, c -> {
		});
		try {
			Identifier good = carrierGood;
			long otherId = otherId(s);
			prices(s, good, true, true);
			s.world.getServer().runOnServer(server -> ClientTownCases.facility(server, stall, "inn", FacilityKind.INN, ClientTownCases.State.CONSTRUCTING,
					stall.above(5)));
			Object staff = ClientStaffCases.openTab(s);
			MarketScreen screen = screen(context);
			request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, good, otherId, 8, 1_000_000, 1, 0);
			int id = hired(context, staff, 0);
			request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, good, otherId, 0, 0, 0, 20_000);
			hurryUntil(s, staff, id, x -> x.phase() == 1, 3, "on the way");
			String key = K + "phase.outbound";
			int m0 = number(squeeze(status(context, screen, staff, id)), key);
			check(m0 >= 4, "the carrier's row says %d minutes on the way (\"%s\", 4 or more needed): %s", m0, text(key, "N"), status(context, screen, staff, id));

			advance(s, "0.1");
			StaffS2C.View v = view(context, staff, id);
			check(v.phase() == 1, "the carrier is no longer on the way after the clock moved (%s)", describe(v));
			int m1 = number(squeeze(status(context, screen, staff, id)), key);
			check(m0 - m1 == 2, "the carrier's row went from %d to %d minutes (2 less expected)", m0, m1);
			screenshot(context, result, "13-10_staff_after_clock_advance");

			// The development tab of the same screen.
			context.clickScreenButton("screen." + NS + ".tab.town");
			context.waitTicks(3);
			mouseAway(context);
			MarketScreen town = screen;
			long due = ClientTownCases.view(context, town, "inn").due();
			int c0 = context.computeOnClient(c -> (Integer) call(town, "minutesUntil", due));
			String t0 = text("screen." + NS + ".town.state.constructing", c0);
			check(c0 >= 4 && ClientTownCases.row(context, town, "inn").contains(t0), "the inn's row does not say \"%s\": %s", t0,
					ClientTownCases.row(context, town, "inn"));
			advance(s, "0.1");
			check(context.computeOnClient(c -> c.gui.screen() == town), "the trade screen closed");
			int c1 = context.computeOnClient(c -> (Integer) call(town, "minutesUntil", due));
			String t1 = text("screen." + NS + ".town.state.constructing", c1);
			check(c0 - c1 == 2, "the inn is due in %d minutes after %d (2 less expected)", c1, c0);
			check(ClientTownCases.row(context, town, "inn").contains(t1), "after the clock moved the inn's row does not say \"%s\": %s", t1,
					ClientTownCases.row(context, town, "inn"));
			screenshot(context, result, "13-10_town_after_clock_advance");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				market(server, stall).facilities().remove(ClientTownCases.id("inn"));
				UUID owner = player(server).getUUID();
				MarketRegistry.get(server).employees().removeIf(e -> e.owner().equals(owner));
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	// ---- 35-3 a harbor and a watchtower

	/** Opens the destinations of {@code good} for a new carrier; the other market's entry. */
	private static StaffS2C.Destination destinationOf(ClientGameTestContext context, MarketScreen screen, Object staff, Identifier good, long otherId) {
		select(context, staff, -1);
		clickWidget(context, text(K + "hire.carrier"));
		mouseAway(context);
		pickGood(context, screen, staff, good, true);
		check(mode(context, staff).equals("PICK_DESTINATION"), "the good opened %s, not the destinations", mode(context, staff));
		@SuppressWarnings("unchecked")
		boolean listed = waitClient(context, c -> !((List<StaffS2C.Destination>) call(staff, "sortedDestinations")).isEmpty(), 100);
		check(listed, "no destinations arrived");
		return destinations(context, staff).stream().filter(d -> d.id() == otherId).findFirst()
				.orElseThrow(() -> new AssertionError("the other market is not a destination"));
	}

	@SuppressWarnings("unchecked")
	private static List<StaffS2C.Destination> destinations(ClientGameTestContext context, Object staff) {
		return context.computeOnClient(c -> (List<StaffS2C.Destination>) call(staff, "sortedDestinations"));
	}

	private static int destinationRow(ClientGameTestContext context, Object staff, long otherId) {
		List<StaffS2C.Destination> list = destinations(context, staff);
		for (int i = 0; i < list.size(); i++) if (list.get(i).id() == otherId) return i;
		throw new AssertionError("the other market is not a destination");
	}

	/** The tab's own "片道 約N分・事故の確率 N%" line. */
	private static String travel(ClientGameTestContext context, Object staff, int distance, double travel, double accident) {
		return squeeze(context.computeOnClient(c -> ((Component) call(staff, "travelLine", distance, travel, accident)).getString()));
	}

	/**
	 * 35-3: with a harbor at the stall's market the other market's tooltip and the setup page say "片道 約N分" for the
	 * shorter trip ({@code development.harborTravelMultiplier}), and a carrier hired there really takes that long; with a
	 * watchtower instead the chance of an accident ({@code staff.accidentBase} 0.1 here) is halved
	 * ({@code watchtowerAccidentMultiplier}). The buildings are made on the server without their blocks (as
	 * {@link ClientTownCases}).
	 */
	static void harborWatchtower(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		BlockPos stall = s.stall;
		BlockPos core = stall.above(6);
		fresh(s, c -> {
			c.accidentBase = 0.1;
			c.accidentMax = 1;
		});
		try {
			Identifier good = carrierGood;
			long otherId = otherId(s);
			prices(s, good, true, true);
			TradeExpansionConfig.Development dev = TradeExpansionConfig.get().development;
			double harbor = dev.harborTravelMultiplier, watchtower = dev.watchtowerAccidentMultiplier;

			Object staff = ClientStaffCases.openTab(s);
			MarketScreen screen = screen(context);
			StaffS2C.Destination plain = destinationOf(context, screen, staff, good, otherId);
			check(plain.travelMultiplier() == 1.0 && plain.accidentMultiplier() == 1.0, "without buildings the route has the multipliers %s / %s",
					plain.travelMultiplier(), plain.accidentMultiplier());
			String plainLine = travel(context, staff, plain.distance(), 1.0, 1.0);
			String tip = rowTooltip(context, screen, staff, destinationRow(context, staff, otherId));
			check(tip.contains(plainLine), "the destination's tooltip does not say \"%s\": %s", plainLine, tip);
			double blocksPerDay = data(context, staff).info().blocksPerDay();

			// A harbor.
			s.world.getServer().runOnServer(server -> ClientTownCases.facility(server, stall, "harbor", FacilityKind.HARBOR, ClientTownCases.State.COMPLETE, core));
			staff = ClientStaffCases.openTab(s);
			Object tab = staff;
			screen = screen(context);
			StaffS2C.Destination d = destinationOf(context, screen, staff, good, otherId);
			check(d.travelMultiplier() == harbor && d.accidentMultiplier() == 1.0, "with a harbor the route has the multipliers %s / %s (%s / 1 expected)",
					d.travelMultiplier(), d.accidentMultiplier(), harbor);
			check(StaffMath.travelTicks(d.distance(), blocksPerDay, harbor) < StaffMath.travelTicks(d.distance(), blocksPerDay),
					"a harbor does not make the trip of %dm shorter", d.distance());
			String harborLine = travel(context, staff, d.distance(), harbor, 1.0);
			int row = destinationRow(context, staff, otherId);
			tip = rowTooltip(context, screen, staff, row);
			check(tip.contains(harborLine), "with a harbor the destination's tooltip does not say \"%s\": %s", harborLine, tip);
			screenshot(context, result, "35-3_harbor_destination");
			clickRow(context, screen, staff, row);
			check(mode(context, staff).equals("SETUP"), "the destination opened %s, not the setup", mode(context, staff));
			String page = all(context);
			check(page.contains(harborLine), "with a harbor the setup page does not say \"%s\": %s", harborLine, page);
			long fee = data(context, staff).info().carrierFee();
			clickWidget(context, text(K + "confirm", coins(fee)));
			mouseAway(context);
			check(waitClient(context, c -> ((StaffS2C) get(tab, "data")).staff().stream().anyMatch(v -> v.role() == 0), 60), "the carrier is not listed");
			int id = hired(context, staff, 0);
			request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, id, good, otherId, 0, 0, 0, 20_000);
			StaffS2C.View v = waitView(context, staff, id, x -> x.phase() == 1, 200, "on the way");
			long[] ticks = s.world.getServer().computeOnServer(server -> {
				Market home = market(server, stall);
				double distance = MarketRegistry.linkedDistance(home, other(server, home));
				double perDay = TradeExpansionConfig.get().staff.carrierBlocksPerDay;
				return new long[] {StaffMath.travelTicks(distance, perDay, harbor), StaffMath.travelTicks(distance, perDay)};
			});
			check(v.nextTime() - v.phaseStart() == ticks[0], "the carrier takes %d ticks with a harbor, not %d (%d without)", v.nextTime() - v.phaseStart(),
					ticks[0], ticks[1]);

			// A watchtower instead.
			s.world.getServer().runOnServer(server -> {
				market(server, stall).facilities().remove(ClientTownCases.id("harbor"));
				ClientTownCases.facility(server, stall, "watchtower", FacilityKind.WATCHTOWER, ClientTownCases.State.COMPLETE, core);
			});
			staff = ClientStaffCases.openTab(s);
			screen = screen(context);
			d = destinationOf(context, screen, staff, good, otherId);
			check(d.travelMultiplier() == 1.0 && d.accidentMultiplier() == watchtower, "with a watchtower the route has the multipliers %s / %s (1 / %s expected)",
					d.travelMultiplier(), d.accidentMultiplier(), watchtower);
			String towerLine = travel(context, staff, d.distance(), 1.0, watchtower);
			check(!towerLine.equals(plainLine), "a watchtower does not change \"%s\"", plainLine);
			tip = rowTooltip(context, screen, staff, destinationRow(context, staff, otherId));
			check(tip.contains(towerLine), "with a watchtower the destination's tooltip does not say \"%s\": %s", towerLine, tip);
			screenshot(context, result, "35-3_watchtower_destination");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				Market home = market(server, stall);
				home.facilities().remove(ClientTownCases.id("harbor"));
				home.facilities().remove(ClientTownCases.id("watchtower"));
				server.overworld().setBlock(core, Blocks.AIR.defaultBlockState(), 2);
				UUID id = player(server).getUUID();
				MarketRegistry.get(server).employees().removeIf(e -> e.owner().equals(id));
				MarketRegistry.get(server).setDirty();
			});
		}
	}
}
