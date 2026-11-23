package com.akf.tradeexpansion.staff;

import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.StaffMath;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Profit estimate of an automatic carrier (design Q-5), with and without return cargo (v0.54), and (v0.83) with several
 * destinations: always the first, or the best per day before every trip ({@code roam}). Run by
 * {@link CarrierProfitSimulation}.
 * <p>
 * The home market and its destinations have 15 goods each, 12 of them shared, built from the bundled goods data with the
 * default settings: price jitter ±15%, trends (1 popular ×1.15–1.30 and 1 unpopular ×0.80–0.90 every 5 days), shortages
 * (2 goods ×1.5–2.0 every 3 days), lazy recovery and propagation between all markets (C-2). Destination i lies at
 * distance × (1 + 0.5 i), the destinations spread around the home market. The carrier follows StaffService: wholesale
 * prices ({@link Params}), the automatic plan, selling above cost, waiting up to 1 day, accidents, the commission.
 * Nobody else trades. Consumables use the default recovery (the item tags are not loaded here).
 */
final class CarrierProfitSim {
	private static final Path GOODS = Path.of("src/main/resources/data/ak_trade_expansion/ak_trade_expansion/goods");
	static final int RUNS = 300;
	static final double DAYS = 30;
	private static final double STEP = 0.1; // StaffService.RETRY_TICKS

	// defaults of TradeExpansionConfig
	private static final double SPREAD = 0.4, TAU = 32, VMIN = 0.25, VMAX = 3.0, JITTER = 0.15, MAX_STOCK = 2.0, HALF_LIFE = 2.0;
	private static final double NEAR = 128, FAR = 1536, FALLOFF = 2.0;
	private static final int CAPACITY = 64;
	private static final double COMMISSION = 0.2, BLOCKS_PER_DAY = 2000, MAX_WAIT = 1.0;
	private static final double ACCIDENT = 0.01, LOSS_MIN = 0.1, LOSS_MAX = 0.3, REST = 0.5;

	/** The carrier's wholesale prices: spread × {@code spreadFactor}, X per unit {@code impact} (staff.carrier*). */
	record Params(double spreadFactor, double impact) {
		/** Before v0.83. */
		static final Params OLD = new Params(0.5, 0.25);
		/** v0.83. */
		static final Params NEW = new Params(0.6, 0.35);
	}

	record Def(String id, double base, double tau, double spread, double target, double maxStock, double restock, double consume,
			double halfLife, double link) {
	}

	static List<Def> loadGoods() throws IOException {
		List<Def> defs = new ArrayList<>();
		try (Stream<Path> files = Files.list(GOODS)) {
			for (Path file : files.sorted().toList()) {
				JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				if (o.has("auto_add") || o.has("buy") && !o.get("buy").getAsBoolean() || o.has("sell") && !o.get("sell").getAsBoolean()) continue;
				double t = o.get("target_stock").getAsDouble();
				double restock = o.has("restock_per_day") ? o.get("restock_per_day").getAsDouble() : t / 2;
				defs.add(new Def(file.getFileName().toString(), o.get("base_price").getAsDouble(),
						o.has("elasticity") ? o.get("elasticity").getAsDouble() : TAU, o.has("spread") ? o.get("spread").getAsDouble() : SPREAD, t,
						o.has("max_stock") ? o.get("max_stock").getAsDouble() : t * MAX_STOCK, restock,
						o.has("consume_per_day") ? o.get("consume_per_day").getAsDouble() : restock / 2,
						o.has("half_life_days") ? o.get("half_life_days").getAsDouble() : HALF_LIFE, o.has("link") ? o.get("link").getAsDouble() : 1));
			}
		}
		return defs;
	}

	/** One market: its goods (the first {@link #SHARED} shared by all markets) and their states. */
	static final class Mkt {
		final Def[] defs;
		final double[] factor;
		final GoodState[] states;
		final double[] trend;
		final double[] shortage;
		double last;

		Mkt(Def[] defs, Random r) {
			this.defs = defs;
			int n = defs.length;
			factor = new double[n];
			states = new GoodState[n];
			trend = new double[n];
			shortage = new double[n];
			for (int i = 0; i < n; i++) {
				factor[i] = 1 + (r.nextDouble() * 2 - 1) * JITTER;
				states[i] = new GoodState(factor[i], 0, defs[i].target(), 0);
			}
		}

		GoodParams params(int i) {
			Def d = defs[i];
			GoodParams p = new GoodParams(d.base() * factor[i] * trend[i] * shortage[i], d.tau(), d.spread(), d.target(), d.maxStock(), d.restock(),
					VMIN, VMAX, d.consume(), d.halfLife());
			return PriceModel.ensureExploitSafe(p);
		}

		void touch(double now) {
			double dt = now - last;
			if (dt <= 0) return;
			for (int i = 0; i < defs.length; i++) PriceModel.recover(params(i), states[i], dt, 1.0);
			last = now;
		}

		void roll(Random r, boolean newTrend, boolean newShortage) {
			int n = defs.length;
			if (newTrend) {
				java.util.Arrays.fill(trend, 1.0);
				int up = r.nextInt(n);
				int down = (up + 1 + r.nextInt(n - 1)) % n;
				trend[up] = 1.15 + r.nextDouble() * 0.15;
				trend[down] = 0.80 + r.nextDouble() * 0.10;
			}
			if (newShortage) {
				java.util.Arrays.fill(shortage, 1.0);
				int a = r.nextInt(n);
				int b = (a + 1 + r.nextInt(n - 1)) % n;
				shortage[a] = 1.5 + r.nextDouble() * 0.5;
				shortage[b] = 1.5 + r.nextDouble() * 0.5;
			}
		}
	}

	static final class Line {
		final int good; // shared index
		int units;
		long cost;

		Line(int good, int units, long cost) {
			this.good = good;
			this.units = units;
			this.cost = cost;
		}
	}

	record Plan(List<Integer> goods, List<StaffMath.BuyPlan> plans, int units, long profit) {
	}

	/** Result of one 30-day run. {@code switches}: trips to another destination than the one before (roam). */
	record Result(long net, int trips, int emptyTrips, long backhaulProfit, long firstHalf, long secondHalf, int switches) {
	}

	/** Shared goods are the first 12 entries of every market (same definitions, own jitter). */
	static final int SHARED = 12;

	/** markets[0] is home; the others are the destinations. */
	final Mkt[] markets;
	/** Distance from home of each market (0 for home), and the propagation weights between all markets. */
	final double[] distance;
	final double[][] w;
	final boolean backhaul;
	final boolean roam;
	final Params params;
	final Random r;
	/** The destination of the present trip (index into {@link #markets}). */
	int dest = 1;

	CarrierProfitSim(Mkt[] markets, double distance, boolean backhaul, boolean roam, Params params, Random r) {
		this.markets = markets;
		this.backhaul = backhaul;
		this.roam = roam;
		this.params = params;
		this.r = r;
		int n = markets.length;
		this.distance = new double[n];
		double[] x = new double[n];
		double[] z = new double[n];
		for (int i = 1; i < n; i++) {
			this.distance[i] = distance * (1 + 0.5 * (i - 1));
			double angle = Math.PI * 2 * (i - 1) / Math.max(1, n - 1);
			x[i] = Math.cos(angle) * this.distance[i];
			z[i] = Math.sin(angle) * this.distance[i];
		}
		w = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i != j) w[i][j] = DistanceModel.weight(DistanceModel.horizontalDistance(x[i], z[i], x[j], z[j]), NEAR, FAR, FALLOFF);
			}
		}
	}

	/** The two-market estimate of before (one destination). */
	CarrierProfitSim(Mkt a, Mkt b, double distance, boolean backhaul, Random r) {
		this(new Mkt[] {a, b}, distance, backhaul, false, Params.OLD, r);
	}

	double chance() {
		return StaffMath.accidentChance(distance[dest], ACCIDENT, 0, 0.2);
	}

	double keep() {
		return 1 - chance() * (LOSS_MIN + LOSS_MAX) / 2;
	}

	GoodParams wholesale(Mkt m, int g) {
		return StaffMath.withSpread(m.params(g), params.spreadFactor());
	}

	StaffMath.BuyPlan plan(Mkt home, Mkt to, int g, int max, long funds) {
		int units = Math.min(max, (int) Math.floor(home.states[g].stock()));
		if (units <= 0) return StaffMath.BuyPlan.NONE;
		return StaffMath.planBuy(wholesale(home, g), home.states[g].pressure(), wholesale(to, g), to.states[g].pressure(), units, Long.MAX_VALUE, funds,
				params.impact(), keep());
	}

	long expected(StaffMath.BuyPlan p) {
		return StaffMath.expectedProfit(p, chance(), (LOSS_MIN + LOSS_MAX) / 2, COMMISSION);
	}

	Plan autoPlan(Mkt home, Mkt to, int capacity) {
		List<long[]> options = new ArrayList<>();
		for (int g = 0; g < SHARED; g++) {
			StaffMath.BuyPlan p = plan(home, to, g, capacity, Long.MAX_VALUE);
			if (p.units() <= 0) continue;
			long profit = expected(p);
			if (profit > 0) options.add(new long[] {g, profit});
		}
		options.sort(Comparator.comparingLong((long[] o) -> -o[1]));
		List<Integer> goods = new ArrayList<>();
		List<StaffMath.BuyPlan> plans = new ArrayList<>();
		int room = capacity;
		int units = 0;
		long profit = 0;
		for (long[] o : options) {
			if (room <= 0) break;
			StaffMath.BuyPlan p = plan(home, to, (int) o[0], room, Long.MAX_VALUE);
			if (p.units() <= 0) continue;
			goods.add((int) o[0]);
			plans.add(p);
			room -= p.units();
			units += p.units();
			profit += expected(p);
		}
		return new Plan(goods, plans, units, profit);
	}

	/** v0.83 (StaffService.roamOptions): the destination with the best expected profit per day of travel, or -1. */
	int bestDestination(int room) {
		int saved = dest;
		int best = -1;
		double bestPerDay = 0;
		for (int i = 1; i < markets.length; i++) {
			dest = i;
			long profit = Math.max(0, autoPlan(markets[0], markets[i], room).profit());
			if (backhaul) profit += Math.max(0, autoPlan(markets[i], markets[0], CAPACITY).profit());
			double days = 2.0 * StaffMath.travelTicks(distance[i], BLOCKS_PER_DAY) / PriceModel.TICKS_PER_DAY;
			double perDay = StaffMath.perDay(profit, days);
			if (profit > 0 && perDay > bestPerDay) {
				bestPerDay = perDay;
				best = i;
			}
		}
		dest = saved;
		return best;
	}

	/** A trade at market {@code mi}: X moves by {@code dx} there and by dx × w at the others (C-2). */
	void move(int mi, int g, double dx, double dStock, double now) {
		for (Mkt m : markets) m.touch(now);
		Mkt m = markets[mi];
		GoodState s = m.states[g];
		s.setPressure(m.params(g).clampPressure(s.pressure() + dx));
		s.setStock(s.stock() + dStock);
		for (int j = 0; j < markets.length; j++) {
			if (j == mi || w[mi][j] <= 0) continue;
			Mkt o = markets[j];
			GoodState os = o.states[g];
			os.setPressure(o.params(g).clampPressure(os.pressure() + dx * w[mi][j] * o.defs[g].link()));
		}
	}

	void take(int mi, Plan plan, List<Line> lines, double now) {
		for (int i = 0; i < plan.goods().size(); i++) {
			int g = plan.goods().get(i);
			StaffMath.BuyPlan p = plan.plans().get(i);
			move(mi, g, -p.units() * params.impact(), -p.units(), now);
			lines.add(new Line(g, p.units(), p.total()));
		}
	}

	/** @return {revenue, cost} */
	long[] sell(int mi, List<Line> lines, double now) {
		Mkt m = markets[mi];
		m.touch(now);
		long revenue = 0;
		long cost = 0;
		for (Line line : lines) {
			GoodParams p = m.params(line.good);
			GoodState s = m.states[line.good];
			int max = Math.min(line.units, PriceModel.sellCapacity(p, s));
			if (max <= 0) continue;
			long min = Math.floorDiv(line.cost, line.units) + 1;
			StaffMath.SellPlan plan = StaffMath.planSell(wholesale(m, line.good), s.pressure(), max, min, params.impact());
			if (plan.units() <= 0) continue;
			move(mi, line.good, plan.units() * params.impact(), plan.units(), now);
			long c = StaffMath.shareOf(line.cost, plan.units(), line.units);
			line.units -= plan.units();
			line.cost -= c;
			revenue += plan.total();
			cost += c;
		}
		lines.removeIf(l -> l.units <= 0);
		return new long[] {revenue, cost};
	}

	static int units(List<Line> lines) {
		int n = 0;
		for (Line l : lines) n += l.units;
		return n;
	}

	/** Accident on a loaded leg: loses a share of every line. @return the cost lost, or -1 without accident */
	long accident(List<Line> cargo, List<Line> back) {
		if (units(cargo) + units(back) <= 0 || r.nextDouble() >= chance()) return -1;
		double f = LOSS_MIN + (LOSS_MAX - LOSS_MIN) * r.nextDouble();
		long lost = 0;
		for (List<Line> lines : List.of(cargo, back)) {
			for (Line l : lines) {
				int n = StaffMath.lostUnits(l.units, f);
				long c = StaffMath.shareOf(l.cost, n, l.units);
				l.units -= n;
				l.cost -= c;
				lost += c;
			}
			lines.removeIf(l -> l.units <= 0);
		}
		return lost;
	}

	Result run() {
		List<Line> cargo = new ArrayList<>();
		List<Line> back = new ArrayList<>();
		long trendPeriod = -1;
		long shortagePeriod = -1;
		long net = 0; // revenue − cost − commission − lost cargo
		long half = 0;
		boolean halfSeen = false;
		long backhaulProfit = 0;
		int trips = 0;
		int empty = 0;
		int switches = 0;
		long tripRevenue = 0;
		long tripCost = 0;
		double now = 0;
		int phase = 0; // 0 home, 1 outbound, 2 away, 3 returning
		double awaySince = 0;
		Mkt home = markets[0];
		while (now < DAYS) {
			if (now >= DAYS / 2 && !halfSeen) {
				half = net;
				halfSeen = true;
			}
			long tp = (long) Math.floor(now / 5);
			long sp = (long) Math.floor(now / 3);
			if (tp != trendPeriod || sp != shortagePeriod) {
				for (Mkt m : markets) {
					m.touch(now);
					m.roll(r, tp != trendPeriod, sp != shortagePeriod);
				}
				trendPeriod = tp;
				shortagePeriod = sp;
			}
			for (Mkt m : markets) m.touch(now);
			double travel = StaffMath.travelTicks(distance[dest], BLOCKS_PER_DAY) / (double) PriceModel.TICKS_PER_DAY;
			switch (phase) {
				case 0 -> {
					if (!back.isEmpty()) {
						long[] s = sell(0, back, now);
						net += s[0] - s[1];
						backhaulProfit += s[0] - s[1];
						tripRevenue += s[0];
						tripCost += s[1];
					}
					int room = CAPACITY - units(back);
					if (roam && cargo.isEmpty() && room > 0) {
						int best = bestDestination(room);
						if (best > 0 && best != dest) {
							dest = best;
							switches++;
							travel = StaffMath.travelTicks(distance[dest], BLOCKS_PER_DAY) / (double) PriceModel.TICKS_PER_DAY;
						}
					}
					Mkt to = markets[dest];
					if (cargo.isEmpty() && room > 0) {
						Plan p = autoPlan(home, to, room);
						if (p.units() > 0) take(0, p, cargo, now);
					}
					boolean go = !cargo.isEmpty() || backhaul && back.isEmpty() && autoPlan(to, home, CAPACITY).profit() > 0;
					if (go) {
						if (cargo.isEmpty()) empty++;
						phase = 1;
						now += travel;
					} else now += STEP;
				}
				case 1 -> {
					long lost = accident(cargo, back);
					phase = 2;
					awaySince = now;
					if (lost >= 0) {
						net -= lost;
						now += REST;
						awaySince = now;
					}
				}
				case 2 -> {
					if (!cargo.isEmpty()) {
						long[] s = sell(dest, cargo, now);
						net += s[0] - s[1];
						tripRevenue += s[0];
						tripCost += s[1];
					}
					if (cargo.isEmpty() || now - awaySince >= MAX_WAIT) {
						if (backhaul && back.isEmpty()) {
							Plan p = autoPlan(markets[dest], home, CAPACITY - units(cargo));
							if (p.units() > 0) take(dest, p, back, now);
						}
						phase = 3;
						now += travel;
					} else now += STEP;
				}
				case 3 -> {
					long lost = accident(cargo, back);
					if (lost >= 0) {
						net -= lost;
						backhaulProfit -= lost; // counted against the return trip
					}
					if (!back.isEmpty()) {
						long[] s = sell(0, back, now);
						net += s[0] - s[1];
						backhaulProfit += s[0] - s[1];
						tripRevenue += s[0];
						tripCost += s[1];
					}
					trips++;
					long commission = StaffMath.commission(tripRevenue - tripCost, COMMISSION);
					net -= commission;
					tripRevenue = 0;
					tripCost = 0;
					phase = 0;
					if (lost >= 0) now += REST;
				}
				default -> {
				}
			}
		}
		// what is still carried is valued at its cost (neither profit nor loss)
		return new Result(net, trips, empty, backhaulProfit, half, net - half, switches);
	}

	/** A home market and its destination. */
	static Mkt[] markets(List<Def> defs, Random r) {
		return markets(defs, r, 1);
	}

	/** A home market and {@code destinations} destinations: the same 12 shared goods, 3 goods of their own each. */
	static Mkt[] markets(List<Def> defs, Random r, int destinations) {
		List<Def> pool = new ArrayList<>(defs);
		java.util.Collections.shuffle(pool, r);
		Mkt[] markets = new Mkt[destinations + 1];
		for (int m = 0; m <= destinations; m++) {
			Def[] d = new Def[SHARED + 3];
			for (int i = 0; i < SHARED; i++) d[i] = pool.get(i);
			for (int i = 0; i < 3; i++) d[SHARED + i] = pool.get((SHARED + m * 3 + i) % pool.size());
			markets[m] = new Mkt(d, r);
		}
		return markets;
	}
}
