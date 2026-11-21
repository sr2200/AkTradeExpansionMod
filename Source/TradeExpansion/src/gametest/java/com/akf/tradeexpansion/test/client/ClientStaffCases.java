package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.FittedButton;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.GoodView;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the staff tab (checklist chapter 26, design Q): hiring a carrier and a clerk through the tab's lists
 * and setup page, the rows' status while a carrier travels (moved on with {@code /aktradeex staff hurry}), the panel's
 * buttons, the limit of employees, the tab at another market and the layout with employees. The cases run in order
 * and share the employees; the last one lets them all go and puts the settings back.
 */
final class ClientStaffCases {
	private ClientStaffCases() {
	}

	static final String NS = "ak_trade_expansion";
	static final String K = "screen." + NS + ".staff.";

	/** Set up once by {@link #prepare}: the settings and reputation to put back, the goods of the cases. */
	static TradeExpansionConfig oldConfig;
	static Market.Standing oldStanding;
	static boolean prepared;
	static Identifier carrierGood;

	static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/** The fixed part of a translation between its first and second %s. */
	static String middle(String key) {
		String[] parts = Language.getInstance().getOrDefault(key).split("%s", -1);
		return parts.length > 1 ? parts[1] : parts[0];
	}

	static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	static String squeeze(String s) {
		return s.replaceAll("\\s", "").replace("…", "");
	}

	static String coins(long amount) {
		return CoinItem.formatCopper(amount);
	}

	// ---- set-up

	static Market other(MinecraftServer server, Market home) {
		return MarketRegistry.get(server).all().stream().filter(m -> m.id() != home.id() && m.dimension().equals(home.dimension()))
				.min(Comparator.comparingDouble(m -> m.center().distSqr(home.center())))
				.orElseThrow(() -> new AssertionError("no other market (the case placement_hint founds one: run it too)"));
	}

	/**
	 * Once for the cases: no accidents and no return cargo (the trips go as planned), the reputation "常連" (300 points: 150 would decay below it at once) at the stall's
	 * market, plenty of money, and one good cheap here and dear at the other market (worth carrying).
	 */
	static void prepare(Session s) {
		if (prepared) return;
		BlockPos stall = s.stall;
		carrierGood = s.world.getServer().computeOnServer(server -> {
			oldConfig = ConfigManager.copy(TradeExpansionConfig.get());
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			config.staff.accidentBase = 0;
			config.staff.carrierBackhaul = false;
			ConfigManager.apply(config);
			ServerPlayer player = player(server);
			Market home = market(server, stall);
			Market other = other(server, home);
			long now = MarketUpkeep.touch(server.overworld(), other);
			oldStanding = home.standing(player.getUUID());
			home.setStanding(new Market.Standing(player.getUUID(), 300, MarketClock.now(server.overworld())));
			WalletService.give(player, 100_000);
			Predicate<Identifier> usable = g -> {
				GoodsDefinition def = GoodsCatalog.get(g);
				return def != null && def.buy() && def.sell() && def.unit() == 1 && def.components().isEmpty() && !home.isSpecial(g)
						&& home.good(g).stock() >= 20;
			};
			Identifier good = home.regularGoods().stream().filter(usable).filter(g -> other.regularGoods().contains(g))
					.max(Comparator.comparingDouble(g -> GoodsCatalog.get(g).basePrice())).orElse(null);
			if (good == null) {
				// Markets of different kinds may share no good: give the other one (a test set-up only).
				good = home.regularGoods().stream().filter(usable).findFirst().orElseThrow(() -> new AssertionError("the market has no good to carry"));
				other.addGood(good, new GoodState(1.0, 0, GoodsCatalog.get(good).targetStock(), now));
			}
			home.good(good).setPressure(home.params(good).maxPressure() * 0.8); // cheap here
			other.good(good).setPressure(other.params(good).minPressure() * 0.8); // dear there
			MarketRegistry.get(server).setDirty();
			return good;
		});
		prepared = true;
	}

	/** Lets the player's employees go, puts the settings and the reputation back. */
	static void cleanUp(Session s) {
		if (!prepared) return;
		prepared = false;
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> {
			UUID id = player(server).getUUID();
			MarketRegistry registry = MarketRegistry.get(server);
			registry.employees().removeIf(e -> e.owner().equals(id));
			Market home = market(server, stall);
			home.setStanding(oldStanding != null ? oldStanding : new Market.Standing(id, 0, MarketClock.now(server.overworld())));
			if (oldConfig != null) ConfigManager.apply(oldConfig);
			registry.setDirty();
		});
	}

	// ---- the tab

	/** Opens the stall again and the staff tab; waits for the tab's first list. @return the tab */
	static Object openTab(Session s) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		context.waitTicks(2);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		return staffTabOf(context, screen);
	}

	static Object staffTabOf(ClientGameTestContext context, MarketScreen screen) {
		context.clickScreenButton("screen." + NS + ".tab.staff");
		Object staff = context.computeOnClient(c -> get(screen, "staffTab"));
		check(waitClient(context, c -> get(staff, "data") != null, 100), "the staff tab got no list");
		context.waitTicks(2);
		return staff;
	}

	static StaffS2C data(ClientGameTestContext context, Object staff) {
		return context.computeOnClient(c -> (StaffS2C) get(staff, "data"));
	}

	static String mode(ClientGameTestContext context, Object staff) {
		return context.computeOnClient(c -> ((Enum<?>) get(staff, "mode")).name());
	}

	static void refresh(ClientGameTestContext context, Object staff) {
		context.runOnClient(c -> {
			call(staff, "requestList");
			call(c.gui.screen(), "updateButtons");
		});
	}

	/** Waits (asking for the list every second, at most {@code ticks}) until the employee matches. */
	static StaffS2C.View waitView(ClientGameTestContext context, Object staff, int id, Predicate<StaffS2C.View> condition, int ticks, String what) {
		StaffS2C.View view = null;
		for (int i = 0; i <= ticks; i++) {
			view = view(context, staff, id);
			if (view != null && condition.test(view)) {
				context.waitTicks(2);
				return view(context, staff, id);
			}
			if (i % 20 == 0) refresh(context, staff);
			context.waitTick();
		}
		throw new AssertionError("the employee #" + id + " did not get " + what + " (now " + describe(view) + ")");
	}

	static String describe(StaffS2C.View v) {
		if (v == null) return "not listed";
		return "phase " + v.phase() + ", status " + v.status() + ", paused " + v.paused() + ", purse " + v.purse() + ", stock " + v.stock();
	}

	static StaffS2C.View view(ClientGameTestContext context, Object staff, int id) {
		StaffS2C data = data(context, staff);
		return data == null ? null : data.staff().stream().filter(v -> v.id() == id).findFirst().orElse(null);
	}

	static int rowIndex(ClientGameTestContext context, Object staff, int id) {
		List<StaffS2C.View> list = data(context, staff).staff();
		for (int i = 0; i < list.size(); i++) if (list.get(i).id() == id) return i;
		throw new AssertionError("the employee #" + id + " is not listed");
	}

	static void select(ClientGameTestContext context, Object staff, int id) {
		context.runOnClient(c -> {
			set(staff, "selected", id);
			call(c.gui.screen(), "updateButtons");
		});
		context.waitTicks(2);
	}

	/** Scrolls the tab's list so row {@code index} shows; returns the top of the row in GUI pixels. */
	static int showRow(ClientGameTestContext context, MarketScreen screen, Object staff, int index) {
		int total = context.computeOnClient(c -> (Integer) call(staff, "rowCount"));
		int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
		check(index < total, "row %d of %d rows", index, total);
		int scroll = Math.max(0, Math.min(index, total - rows));
		context.runOnClient(c -> set(screen, "scroll", scroll));
		context.waitTicks(2);
		return context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y") + (index - scroll) * constant(MarketScreen.class, "ROW_H");
	}

	static void clickRow(ClientGameTestContext context, MarketScreen screen, Object staff, int index) {
		int y = showRow(context, screen, staff, index);
		clickAt(context, listX(context, screen) + 60, y + 8);
		mouseAway(context);
	}

	/** The texts of the row (with the mouse away). */
	static List<String> rowStrings(ClientGameTestContext context, MarketScreen screen, Object staff, int index) {
		int y = showRow(context, screen, staff, index);
		mouseAway(context);
		return strings(rowTexts(TextRecorder.capture(context), y));
	}

	/** The tooltip over the row, every line inside the screen, joined without spaces. */
	static String rowTooltip(ClientGameTestContext context, MarketScreen screen, Object staff, int index) {
		int y = showRow(context, screen, staff, index);
		moveTo(context, listX(context, screen) + 60, y + 8);
		context.waitTicks(2);
		List<TextRecorder.Text> tip = tooltip(TextRecorder.capture(context));
		int[] size = context.computeOnClient(c -> new int[] {c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight()});
		List<TextRecorder.Text> outside = tip.stream().filter(t -> t.x0() < 0 || t.y0() < 0 || t.x1() > size[0] || t.y1() > size[1]).toList();
		check(outside.isEmpty(), "tooltip lines run off the screen: %s", outside);
		mouseAway(context);
		return squeeze(String.join("", strings(tip)));
	}

	/** The texts of the right panel. */
	static String panel(ClientGameTestContext context, MarketScreen screen) {
		mouseAway(context);
		int px = context.computeOnClient(c -> leftPos(screen) + (Integer) get(screen, "panelX"));
		return squeeze(String.join("", strings(TextRecorder.capture(context).stream().filter(t -> t.x0() >= px).toList())));
	}

	/** All texts of the screen in drawing order, joined without spaces. */
	static String all(ClientGameTestContext context) {
		mouseAway(context);
		return squeeze(String.join("", strings(TextRecorder.capture(context))));
	}

	static Button button(Object staff, String field) {
		return (Button) get(staff, field);
	}

	static boolean shownWithText(ClientGameTestContext context, String label) {
		return context.computeOnClient(c -> c.gui.screen().children().stream()
				.anyMatch(ch -> ch instanceof AbstractWidget w && w.visible && w.getMessage().getString().equals(label)));
	}

	static void hurry(Session s) {
		s.world.getServer().runCommand("aktradeex staff hurry");
		s.context.waitTicks(2);
	}

	static List<GuiMessage> chat(Minecraft client) {
		@SuppressWarnings("unchecked")
		List<GuiMessage> messages = (List<GuiMessage>) get(client.gui.hud.getChat(), "allMessages");
		return new ArrayList<>(messages);
	}

	// ---- 26-3 / 26-4 hiring a carrier

	/**
	 * 26-3: "運び屋を雇う" lists "おまかせ" first, then the goods by expected profit, each with "見込み +○c/往復" or "今は
	 * 利益なし"; the tooltip names the best destination. 26-4: a good's destinations show the name and distance, the
	 * tooltip the wholesale prices, the recommendation and the trip; choosing one sets the limits to the recommendation,
	 * and "雇う（300c）" takes the fee and lists the carrier.
	 */
	static void hireCarrier(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		prepare(s);
		Object staff = openTab(s);
		MarketScreen screen = screen(context);
		long otherId = s.world.getServer().computeOnServer(server -> other(server, market(server, s.stall)).id());
		clickWidget(context, text(K + "hire.carrier"));
		check(waitClient(context, c -> get(staff, "carrierHints") != null, 100), "no recommendations of the goods arrived");
		context.waitTicks(2);
		check(mode(context, staff).equals("PICK_GOODS"), "\"%s\" opened %s, not the goods", text(K + "hire.carrier"), mode(context, staff));

		@SuppressWarnings("unchecked")
		List<StaffS2C.CarrierHint> hints = context.computeOnClient(c -> (List<StaffS2C.CarrierHint>) get(staff, "carrierHints"));
		@SuppressWarnings("unchecked")
		List<GoodView> goods = context.computeOnClient(c -> (List<GoodView>) call(staff, "pickableGoods"));
		check(!goods.isEmpty(), "no goods to hire a carrier for");
		long previous = Long.MAX_VALUE;
		for (GoodView v : goods) {
			long profit = hint(hints, v.goods()).profit();
			check(profit <= previous, "the goods are not by expected profit (%s: %d after %d)", v.goods(), profit, previous);
			previous = profit;
		}
		// The first row: おまかせ.
		List<String> first = rowStrings(context, screen, staff, 0);
		String autoRow = text(K + "auto.row");
		check(first.stream().anyMatch(t -> t.length() >= 3 && autoRow.startsWith(t.replace("…", ""))), "the first row is not \"%s\": %s", autoRow, first);
		StaffS2C.CarrierHint auto = hint(hints, com.akf.tradeexpansion.staff.Employee.AUTO);
		String autoRight = auto.profit() > 0 ? text(K + "hint.profit", coins(auto.profit())) : text(K + "hint.none");
		check(first.contains(autoRight), "the おまかせ row does not say \"%s\": %s", autoRight, first);
		// Every good row says what a round trip should bring now.
		int rows = context.computeOnClient(c -> (Integer) get(screen, "rows"));
		for (int i = 0; i < Math.min(goods.size(), rows - 1); i++) {
			StaffS2C.CarrierHint h = hint(hints, goods.get(i).goods());
			String right = h.units() > 0 && h.profit() > 0 ? text(K + "hint.profit", coins(h.profit())) : text(K + "hint.none");
			List<String> row = rowStrings(context, screen, staff, i + 1);
			check(row.contains(right), "the row of %s does not say \"%s\": %s", goods.get(i).goods(), right, row);
		}
		showRow(context, screen, staff, 0);
		screenshot(context, result, "26-3_carrier_goods");

		int index = -1;
		for (int i = 0; i < goods.size(); i++) if (goods.get(i).goods().equals(carrierGood)) index = i + 1;
		check(index > 0, "%s (cheap here, dear at the other market) is not in the list", carrierGood);
		StaffS2C.CarrierHint h = hint(hints, carrierGood);
		check(h.units() > 0 && h.profit() > 0, "%s shows no profit (%s)", carrierGood, h);
		String tip = rowTooltip(context, screen, staff, index);
		String expected = squeeze(text(K + "hint.carrier", h.destinationName(), h.units(), coins(h.profit())));
		check(tip.contains(expected), "the tooltip of %s does not say \"%s\": %s", carrierGood, expected, tip);

		// The destinations.
		clickRow(context, screen, staff, index);
		check(mode(context, staff).equals("PICK_DESTINATION"), "a click on the good opened %s, not the destinations", mode(context, staff));
		@SuppressWarnings("unchecked")
		boolean listed = waitClient(context, c -> !((List<StaffS2C.Destination>) call(staff, "sortedDestinations")).isEmpty(), 100);
		check(listed, "no destinations arrived");
		@SuppressWarnings("unchecked")
		List<StaffS2C.Destination> destinations = context.computeOnClient(c -> (List<StaffS2C.Destination>) call(staff, "sortedDestinations"));
		previous = Long.MAX_VALUE;
		for (StaffS2C.Destination d : destinations) {
			long total = (d.units() > 0 ? Math.max(0, d.profit()) : 0) + (d.returnItems() > 0 ? Math.max(0, d.returnProfit()) : 0);
			check(total <= previous, "the destinations are not by expected profit");
			previous = total;
		}
		int di = -1;
		for (int i = 0; i < destinations.size(); i++) if (destinations.get(i).id() == otherId) di = i;
		check(di >= 0, "the other market is not a destination: %s", destinations.stream().map(d -> d.name().getString()).toList());
		StaffS2C.Destination d = destinations.get(di);
		List<String> row = rowStrings(context, screen, staff, di);
		String name = d.name().getString();
		check(row.stream().anyMatch(t -> t.length() >= 3 && name.startsWith(t.replace("…", ""))), "the destination row has no name \"%s\": %s", name, row);
		check(row.stream().anyMatch(t -> t.endsWith(" " + d.distance() + "m")), "the destination row has no distance %dm: %s", d.distance(), row);
		check(row.contains(text(K + "hint.profit", coins(d.profit()))), "the destination row has no \"%s\": %s", text(K + "hint.profit", coins(d.profit())), row);
		tip = rowTooltip(context, screen, staff, di);
		long buyLimit = Math.max(1, (long) Math.ceil(d.buyLimit() * 1.05));
		String recommend = squeeze(text(K + "hint.recommend", coins(buyLimit), coins(d.sellLimit()), d.units(), coins(d.profit())));
		for (String part : List.of(squeeze(text(K + "destination.tooltip", coins(Math.max(0, d.buyPrice())), coins(d.sellPrice()))), recommend,
				squeeze(prefix(K + "travel")))) {
			check(tip.contains(part), "the destination's tooltip does not say \"%s\": %s", part, tip);
		}
		screenshot(context, result, "26-4_destinations");

		// The setup page: the recommendation as the first values.
		clickRow(context, screen, staff, di);
		check(mode(context, staff).equals("SETUP"), "a click on the destination opened %s, not the setup", mode(context, staff));
		long setBuy = context.computeOnClient(c -> (Long) get(staff, "buyLimit"));
		long setSell = context.computeOnClient(c -> (Long) get(staff, "sellLimit"));
		check(setBuy == buyLimit && setSell == Math.max(1, d.sellLimit()), "the limits start at %d / %d, the recommendation is %d / %d", setBuy, setSell,
				buyLimit, d.sellLimit());
		String page = all(context);
		check(page.contains(recommend), "the setup page does not say \"%s\": %s", recommend, page);
		long fee = data(context, staff).info().carrierFee();
		String confirm = text(K + "confirm", coins(fee));
		screenshot(context, result, "26-4_setup");
		long before = serverMoney(s);
		clickWidget(context, confirm);
		mouseAway(context);
		check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().anyMatch(v -> v.goods().equals(carrierGood)), 60),
				"the carrier is not listed after \"%s\"", confirm);
		context.waitTicks(2);
		check(before - serverMoney(s) == fee, "hiring took %dc, the fee is %dc", before - serverMoney(s), fee);
		check(mode(context, staff).equals("LIST"), "after hiring the tab shows %s, not the list", mode(context, staff));
		String status = squeeze(text(K + "done.hired", coins(fee)));
		check(all(context).contains(status), "the status line does not say \"%s\"", status);
		screenshot(context, result, "26-4_hired");
	}

	static StaffS2C.CarrierHint hint(List<StaffS2C.CarrierHint> hints, Identifier good) {
		return hints.stream().filter(h -> h.goods().equals(good)).findFirst()
				.orElse(new StaffS2C.CarrierHint(good, -1, Component.empty(), 0, 0));
	}

	static int carrierId(ClientGameTestContext context, Object staff) {
		return data(context, staff).staff().stream().filter(v -> v.role() == 0 && v.goods().equals(carrierGood)).findFirst()
				.orElseThrow(() -> new AssertionError("no carrier of " + carrierGood + " (the case staff_hire_carrier hires it)")).id();
	}

	// ---- 26-6 / 26-7 / 26-9 / 26-14 the carrier's trip

	/**
	 * The carrier hired by {@link #hireCarrier}: "資金不足" with an empty purse; with Shift the money buttons say +1,000c /
	 * +10,000c and put in ten times as much; it buys and the row says "→運送 N分" (dismissing and changing cannot be
	 * pressed); "休止" becomes "再開"; "←帰路 N分" on the way back, then "休み" at home with the round trip in the log of
	 * the tooltip. "設定" shows today's recommendation; a highest buy price below the price now gives "値下がり待ち".
	 */
	static void carrierTrip(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		prepare(s);
		Object staff = openTab(s);
		MarketScreen screen = screen(context);
		int id = carrierId(context, staff);
		select(context, staff, id);
		waitView(context, staff, id, v -> v.status() == 3, 200, "\"資金不足\"");
		String funds = text(K + "status.wait_funds");
		check(rowStrings(context, screen, staff, rowIndex(context, staff, id)).contains(funds), "the row does not say \"%s\"", funds);
		String purse = squeeze(text(K + "purse", coins(0)));
		check(panel(context, screen).contains(purse), "the panel does not say \"%s\": %s", purse, panel(context, screen));
		screenshot(context, result, "26-6_wait_funds");

		// Shift: ten times as much.
		long before = serverMoney(s);
		context.getInput().holdShift();
		try {
			context.waitTicks(3);
			check(shownWithText(context, "+1,000c") && shownWithText(context, "+10,000c"), "with Shift the money buttons do not say +1,000c / +10,000c");
			clickWidget(context, "+10,000c");
		} finally {
			context.getInput().releaseShift();
		}
		mouseAway(context);
		for (int i = 0; i < 40 && serverMoney(s) == before; i++) context.waitTick();
		check(before - serverMoney(s) == 10_000, "\"+10,000c\" with Shift took %dc", before - serverMoney(s));
		check(shownWithText(context, "+100c") && shownWithText(context, "+1,000c"), "without Shift the money buttons do not say +100c / +1,000c");

		StaffS2C.View v = waitView(context, staff, id, x -> x.phase() == 1, 200, "on the way (→運送)");
		String outbound = prefix(K + "phase.outbound");
		List<String> row = rowStrings(context, screen, staff, rowIndex(context, staff, id));
		check(row.stream().anyMatch(t -> t.startsWith(outbound) && t.endsWith(middle(K + "phase.outbound"))), "the row does not say \"%s N分\": %s", outbound, row);
		check(v.stock() > 0, "the carrier left with nothing");
		boolean[] away = context.computeOnClient(c -> new boolean[] {button(staff, "dismissButton").active, button(staff, "changeButton").active});
		check(!away[0] && !away[1], "\"%s\" / \"%s\" can be pressed while the carrier is away", text(K + "dismiss"), text(K + "change"));
		screenshot(context, result, "26-6_outbound");

		clickWidget(context, text(K + "pause"));
		mouseAway(context);
		waitView(context, staff, id, StaffS2C.View::paused, 60, "paused");
		check(shownWithText(context, text(K + "resume")), "the pause button does not say \"%s\" while paused", text(K + "resume"));

		boolean back = false;
		for (int i = 0; i < 4 && view(context, staff, id).phase() != 0; i++) {
			hurry(s);
			refresh(context, staff);
			context.waitTicks(3);
			v = view(context, staff, id);
			if (v.phase() == 3) {
				back = true;
				String returning = prefix(K + "phase.returning");
				List<String> r = rowStrings(context, screen, staff, rowIndex(context, staff, id));
				check(r.stream().anyMatch(t -> t.startsWith(returning)), "on the way back the row does not say \"%s N分\": %s", returning, r);
				screenshot(context, result, "26-7_returning");
			}
		}
		check(back, "the carrier was never seen on the way back");
		v = waitView(context, staff, id, x -> x.phase() == 0 && x.status() == 7, 200, "home and \"休み\"");
		String paused = text(K + "status.paused");
		check(rowStrings(context, screen, staff, rowIndex(context, staff, id)).contains(paused), "the paused carrier's row does not say \"%s\"", paused);
		String trip = squeeze("1" + middle("staff." + NS + ".log.returned"));
		String tip = rowTooltip(context, screen, staff, rowIndex(context, staff, id));
		check(tip.contains(trip), "the tooltip's log has no \"%s\": %s", trip, tip);
		check(v.trips() == 1 && v.earned() > v.spent(), "the round trip made no profit (earned %d, spent %d)", v.earned(), v.spent());
		screenshot(context, result, "26-7_back_paused");

		// 26-9: "設定" with today's recommendation; a highest buy price of 1c waits for the price to fall.
		clickWidget(context, text(K + "configure"));
		check(mode(context, staff).equals("SETUP"), "\"%s\" opened %s", text(K + "configure"), mode(context, staff));
		String recommendPrefix = squeeze(prefix(K + "hint.recommend"));
		String noProfit = squeeze(text(K + "hint.carrier.none"));
		check(waitClient(context, c -> get(staff, "destinations") != null, 100), "the setup got no recommendation");
		context.waitTicks(2);
		String page = all(context);
		check(page.contains(recommendPrefix) || page.contains(noProfit), "the setup page shows no recommendation (\"%s\"): %s", recommendPrefix, page);
		// Lower the highest buy price with the setup's "-" button (Shift: the large step) down to 1c.
		long limitBefore = context.computeOnClient(c -> (Long) get(staff, "buyLimit"));
		Button minus = context.computeOnClient(c -> ((Button[][]) get(staff, "spinners"))[1][0]);
		check(context.computeOnClient(c -> minus.visible && minus.active), "the \"-\" of the highest buy price is not shown in the setup");
		context.getInput().holdShift();
		try {
			for (int i = 0; i < 80 && context.computeOnClient(c -> (Long) get(staff, "buyLimit")) > 1; i++) {
				clickWidget(context, minus);
				context.waitTick();
			}
		} finally {
			context.getInput().releaseShift();
		}
		long limitAfter = context.computeOnClient(c -> (Long) get(staff, "buyLimit"));
		check(limitAfter == 1 && limitAfter < limitBefore, "the \"-\" button took the highest buy price from %dc to %dc, not 1c", limitBefore, limitAfter);
		check(all(context).contains(squeeze(coins(1))), "the setup does not show the new highest buy price %s: %s", coins(1), all(context));
		clickWidget(context, text(K + "apply"));
		mouseAway(context);
		check(waitClient(context, c -> ((Enum<?>) get(staff, "mode")).name().equals("LIST"), 60), "\"%s\" did not go back to the list", text(K + "apply"));
		waitView(context, staff, id, x -> x.buyLimit() == 1, 60, "the new limit");
		// The good was made cheap here (a cheap good may cost 1c then): dear again, so the price is above the limit.
		BlockPos stall = s.stall;
		Identifier good = carrierGood;
		s.world.getServer().runOnServer(server -> {
			Market home = market(server, stall);
			home.good(good).setPressure(home.params(good).minPressure());
			MarketRegistry.get(server).setDirty();
		});
		clickWidget(context, text(K + "resume"));
		mouseAway(context);
		waitView(context, staff, id, x -> !x.paused() && x.status() == 1, 200, "\"値下がり待ち\"");
		String waitPrice = text(K + "status.wait_price");
		check(rowStrings(context, screen, staff, rowIndex(context, staff, id)).contains(waitPrice), "the row does not say \"%s\"", waitPrice);
		screenshot(context, result, "26-9_wait_price");
	}

	// ---- 26-11 / 26-13 the clerk

	/**
	 * 26-11: "番頭を雇う" lists the goods carried first with "1日 約○c・手持ち○"; the tooltip has the recommendation; the
	 * setup starts at the recommended lowest price (85%) and every unit carried, and hiring takes the goods. 26-13: with
	 * an empty purse the next day's wage is not paid: "給金不足" and a chat line; money in, it goes on.
	 */
	static void clerk(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		prepare(s);
		Object staff = openTab(s);
		MarketScreen screen = screen(context);
		StaffS2C data = data(context, staff);
		Identifier good = snapshot(context, screen).goods().stream()
				.filter(v -> data.clerkGoods().contains(v.goods()) && v.unit() == 1 && v.patch().isEmpty() && !v.displayStack().isDamageableItem()
						&& !v.goods().equals(carrierGood))
				.map(GoodView::goods).findFirst().orElseThrow(() -> new AssertionError("no good to leave with a clerk"));
		context.setScreen(() -> null);
		s.world.getServer().runOnServer(server -> giveItems(player(server), item(good), 20));
		context.waitTicks(3);
		staff = openTab(s);
		Object tab = staff;
		screen = screen(context);
		select(context, staff, -1);
		clickWidget(context, text(K + "hire.clerk"));
		check(waitClient(context, c -> get(tab, "clerkHints") != null, 100), "no recommendations of the goods arrived");
		context.waitTicks(2);
		@SuppressWarnings("unchecked")
		List<GoodView> goods = context.computeOnClient(c -> (List<GoodView>) call(tab, "pickableGoods"));
		@SuppressWarnings("unchecked")
		List<StaffS2C.ClerkHint> hints = context.computeOnClient(c -> (List<StaffS2C.ClerkHint>) get(tab, "clerkHints"));
		boolean empty = false;
		int index = -1;
		for (int i = 0; i < goods.size(); i++) {
			GoodView v = goods.get(i);
			int held = context.computeOnClient(c -> (Integer) call(tab, "heldUnits", v.goods()));
			check(!(empty && held > 0), "%s carried comes after a good not carried", v.goods());
			empty |= held <= 0;
			if (v.goods().equals(good)) index = i;
		}
		check(index >= 0, "%s is not in the clerk's goods", good);
		StaffS2C.ClerkHint h = hints.stream().filter(x -> x.goods().equals(good)).findFirst().orElseThrow(() -> new AssertionError("no recommendation of " + good));
		// 20 given, and maybe more left by earlier cases.
		int held = serverCount(s, good);
		String right = text(K + "hint.clerk.row", coins(h.revenue()), held);
		List<String> row = rowStrings(context, screen, staff, index);
		check(row.contains(right), "the row of %s does not say \"%s\": %s", good, right, row);
		String tip = rowTooltip(context, screen, staff, index);
		String recommend = squeeze(text(K + "hint.clerk", coins(h.limit()), h.items(), coins(h.revenue())));
		check(tip.contains(recommend), "the tooltip does not say \"%s\": %s", recommend, tip);
		screenshot(context, result, "26-11_clerk_goods");
		clickRow(context, screen, staff, index);
		check(mode(context, staff).equals("SETUP"), "a click on the good opened %s", mode(context, staff));
		long sellLimit = context.computeOnClient(c -> (Long) get(tab, "sellLimit"));
		int quantity = context.computeOnClient(c -> (Integer) get(tab, "quantity"));
		check(sellLimit == h.limit(), "the lowest price starts at %d, the recommendation is %d", sellLimit, h.limit());
		int expected = Math.min(held, data(context, staff).info().clerkCapacity());
		check(quantity == expected, "the clerk would take %d, not %d (the player carries %d)", quantity, expected, held);
		// A lowest price it never reaches: the clerk keeps the goods (and owes its wage every day).
		context.runOnClient(c -> {
			set(tab, "sellLimit", 1_000_000_000L);
			call(c.gui.screen(), "updateButtons");
		});
		long fee = data(context, staff).info().clerkFee();
		long before = serverMoney(s);
		clickWidget(context, text(K + "confirm", coins(fee)));
		mouseAway(context);
		check(waitClient(context, c -> ((StaffS2C) get(tab, "data")).staff().stream().anyMatch(v -> v.role() == 1), 60), "the clerk is not listed");
		context.waitTicks(2);
		check(before - serverMoney(s) == fee, "hiring the clerk took %dc", before - serverMoney(s));
		check(serverCount(s, good) == held - expected, "%d of the goods are carried after hiring (%d expected)", serverCount(s, good), held - expected);
		int id = data(context, staff).staff().stream().filter(v -> v.role() == 1).findFirst().orElseThrow().id();

		select(context, staff, id);
			waitView(context, staff, id, v -> v.status() == 4, 200, "\"値上がり待ち\"");
			check(!button(staff, "withdrawButton").active, "\"%s\" can be pressed with an empty purse", text(K + "withdraw"));
			context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
			s.world.getServer().runCommand("aktradeex clock advance 1.05");
			String chatStart = text("staff." + NS + ".chat", Component.translatable("staff." + NS + ".role.clerk"), "");
			String unpaid = prefix("staff." + NS + ".log.unpaid");
			check(waitClient(context, c -> chat(c).stream().anyMatch(m -> m.content().getString().startsWith(chatStart)
					&& m.content().getString().contains(unpaid)), 100), "no chat line \"%s%s…\"", chatStart, unpaid);
			waitView(context, staff, id, v -> v.status() == 6, 200, "\"給金不足\"");
			String unpaidRow = text(K + "status.unpaid");
			check(rowStrings(context, screen, staff, rowIndex(context, staff, id)).contains(unpaidRow), "the row does not say \"%s\"", unpaidRow);
			screenshot(context, result, "26-13_unpaid");
			clickWidget(context, "+100c");
			mouseAway(context);
		waitView(context, staff, id, v -> v.status() != 6, 200, "back to work after the money");
	}

	// ---- 26-5 / 26-15 / 26-17 the limit and another market

	/**
	 * 26-5: "おまかせ" asks only for the units per trip and hires. 26-17: with 3 employees neither hire button can be
	 * pressed and the panel says why. 26-15: at the other market every employee is listed with no money, goods or setting
	 * buttons; only "休止" works.
	 */
	static void limitAndOtherMarket(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		prepare(s);
		Object staff = openTab(s);
		MarketScreen screen = screen(context);
		long otherId = s.world.getServer().computeOnServer(server -> other(server, market(server, s.stall)).id());
		select(context, staff, -1);
		clickWidget(context, text(K + "hire.carrier"));
		check(waitClient(context, c -> get(staff, "carrierHints") != null, 100), "no recommendations of the goods arrived");
		clickRow(context, screen, staff, 0);
		@SuppressWarnings("unchecked")
		boolean listed = waitClient(context, c -> !((List<StaffS2C.Destination>) call(staff, "sortedDestinations")).isEmpty(), 100);
		check(listed, "no destinations for おまかせ");
		@SuppressWarnings("unchecked")
		List<StaffS2C.Destination> destinations = context.computeOnClient(c -> (List<StaffS2C.Destination>) call(staff, "sortedDestinations"));
		int di = -1;
		for (int i = 0; i < destinations.size(); i++) if (destinations.get(i).id() == otherId) di = i;
		check(di >= 0, "the other market is not a destination of おまかせ");
		clickRow(context, screen, staff, di);
		check(mode(context, staff).equals("SETUP"), "the destination opened %s", mode(context, staff));
		Button[][] spinners = context.computeOnClient(c -> (Button[][]) get(staff, "spinners"));
		boolean[] shown = context.computeOnClient(c -> new boolean[] {spinners[0][0].visible, spinners[1][0].visible, spinners[2][0].visible});
		check(shown[0] && !shown[1] && !shown[2], "おまかせ's setup shows the rows %s (only the units per trip expected)", java.util.Arrays.toString(shown));
		String autoSummary = squeeze(prefix(K + "summary.auto"));
		check(all(context).contains(autoSummary), "the setup page does not say \"%s\"", autoSummary);
		screenshot(context, result, "26-5_auto_setup");
		long fee = data(context, staff).info().carrierFee();
		clickWidget(context, text(K + "confirm", coins(fee)));
		mouseAway(context);
		check(waitClient(context, c -> ((StaffS2C) get(staff, "data")).staff().stream().anyMatch(StaffS2C.View::auto), 60), "the automatic carrier is not listed");
		context.waitTicks(2);

		// 26-17: three employees.
		StaffS2C data = data(context, staff);
		check(data.staff().size() == data.info().maxPerPlayer(), "%d employees, the limit is %d (the cases before hire the others)", data.staff().size(),
				data.info().maxPerPlayer());
		select(context, staff, -1);
		check(!button(staff, "hireCarrierButton").active && !button(staff, "hireClerkButton").active, "a hire button can be pressed at the limit");
		String limit = squeeze(text("trade." + NS + ".result.staff_limit"));
		String count = squeeze(text(K + "count", data.info().maxPerPlayer(), data.info().maxPerPlayer()));
		String p = panel(context, screen);
		check(p.contains(count) && p.contains(limit), "the panel does not say \"%s\" and \"%s\": %s", count, limit, p);
		screenshot(context, result, "26-17_limit");

		// 26-15: the other market.
		Vec3 home = s.world.getServer().computeOnServer(server -> player(server).position());
		BlockPos stall = s.world.getServer().computeOnServer(server -> MarketRegistry.get(server).get(otherId).stalls().iterator().next());
		String homeName = data.staff().getFirst().homeName().getString();
		try {
			context.setScreen(() -> null);
			teleport(s, stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
			context.getInput().lookAt(stall);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MarketScreen.class);
			context.waitTicks(10);
			MarketScreen there = screen(context);
			long opened = context.computeOnClient(c -> (Long) call(there, "marketId"));
			check(opened == otherId, "the stall opened market #%d, not #%d", opened, otherId);
			Object tab = staffTabOf(context, there);
			StaffS2C thereData = data(context, tab);
			check(thereData.staff().size() == data.staff().size(), "the other market lists %d employees, not %d", thereData.staff().size(), data.staff().size());
			check(thereData.staff().stream().noneMatch(StaffS2C.View::atHome), "an employee counts as at home at the other market");
			for (int i = 0; i < thereData.staff().size(); i++) {
				List<String> row = rowStrings(context, there, tab, i);
				check(row.stream().anyMatch(t -> t.contains("＠") || t.endsWith("…")), "the row %d has no \"＠%s\": %s", i, homeName, row);
			}
			StaffS2C.View clerk = thereData.staff().stream().filter(v -> v.role() == 1).findFirst().orElseThrow(() -> new AssertionError("no clerk"));
			select(context, tab, clerk.id());
			List<String> visible = context.computeOnClient(c -> {
				List<String> list = new ArrayList<>();
				for (String f : List.of("depositSmallButton", "depositLargeButton", "withdrawButton", "configureButton", "depositItemsButton",
						"withdrawItemsButton", "pauseButton", "changeButton", "dismissButton")) {
					if (button(tab, f).visible) list.add(f);
				}
				return list;
			});
			check(visible.equals(List.of("pauseButton")), "at the other market the buttons %s show (only the pause button expected)", visible);
			String notHome = squeeze(text(K + "not_home", Component.literal(homeName)));
			String pt = panel(context, there);
			check(pt.contains(notHome), "the panel does not say \"%s\": %s", notHome, pt);
			screenshot(context, result, "26-15_other_market");
			boolean wasPaused = clerk.paused();
			clickWidget(context, text(K + (wasPaused ? "resume" : "pause")));
			mouseAway(context);
			waitView(context, tab, clerk.id(), v -> v.paused() != wasPaused, 60, "paused at the other market");
		} finally {
			context.setScreen(() -> null);
			teleport(s, home.x, home.y, home.z);
		}
	}

	// ---- 26-25 the layout with employees

	static final int[][] SIZES = {{854, 480, 0}, {854, 480, 1}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 0}};

	/**
	 * 26-25: the staff tab with every employee selected in turn (and none): no texts on top of each other or outside the
	 * screen and panel, and the labels of the panel's buttons ("休止／再開", "変更", "解雇する", …) fit their buttons. In
	 * Japanese and English, at 854×480 (GUI auto and 1) and 1920×1080 (GUI 1 to 3 and auto, i.e. 4). Lets the employees go at the end.
	 */
	static void layout(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		try {
			prepare(s);
			List<String> problems = new ArrayList<>();
			for (String language : List.of(ClientChecklistTests.LANGUAGE, "en_us")) {
				ClientChecklistTests.setLanguage(context, language);
				for (int[] size : SIZES) {
					context.getInput().resizeWindow(size[0], size[1]);
					context.runOnClient(c -> {
						c.options.guiScale().set(size[2]);
						c.resizeGui();
					});
					context.waitTicks(3);
					Object staff = openTab(s);
					MarketScreen screen = screen(context);
					List<Integer> ids = new ArrayList<>();
					ids.add(-1);
					data(context, staff).staff().forEach(v -> ids.add(v.id()));
					check(ids.size() > 1, "no employees (the cases before hire them)");
					for (int id : ids) {
						select(context, staff, id);
						String where = String.format("%s %dx%d GUI %s staff #%d", language, size[0], size[1], size[2] == 0 ? "auto" : size[2], id);
						mouseAway(context);
						context.runOnClient(c -> c.gui.toastManager().clear());
						context.waitTicks(3);
						List<TextRecorder.Text> texts = TextRecorder.capture(context);
						List<String> found = context.computeOnClient(c -> ClientChecklistTests.layoutProblems(c, where, texts, ClientChecklistTests.panel(c.gui.screen())));
						found.addAll(context.computeOnClient(c -> {
							List<String> cut = new ArrayList<>();
							for (var child : screen.children()) {
								if (child instanceof AbstractWidget w && w.visible && w.getWidth() < 100) {
									// The label area is the width less 2 pixels on each side; a wider label scrolls (only part of it
									// shows) unless the button shrinks it to fit (FittedButton, down to its smallest scale).
									int width = c.font.width(w.getMessage());
									int room = w.getWidth() - 4;
									boolean fits = width <= room || w instanceof FittedButton && width * FittedButton.MIN_SCALE <= room;
									if (!fits) cut.add(where + ": the button \"" + w.getMessage().getString() + "\" (" + width + " px) is cut in " + w.getWidth() + " px");
								}
							}
							return cut;
						}));
						if (!found.isEmpty() || size == SIZES[0]) screenshot(context, result, "26-25_" + where);
						problems.addAll(found);
					}
				}
			}
			problems.forEach(p -> com.akf.tradeexpansion.test.AutotestReport.LOGGER.info("LAYOUT {}", p));
			check(problems.isEmpty(), "%d layout problems, e.g. %s", problems.size(), String.join(" | ", problems.subList(0, Math.min(8, problems.size()))));
		} finally {
			context.setScreen(() -> null);
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(c -> {
				c.options.guiScale().set(0);
				c.resizeGui();
			});
			ClientChecklistTests.setLanguage(context, ClientChecklistTests.LANGUAGE);
			cleanUp(s);
		}
	}
}
