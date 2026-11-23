package com.akf.tradeexpansion.staff;

import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.StaffMath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Profit estimates of the staff (design Q-5). Not a regular test: it runs only with the environment variable AK_SIM set,
 * and writes build/carrier-profit.txt.
 * <ul>
 * <li>an automatic carrier with and without return cargo, the wholesale prices before v0.83 and now</li>
 * <li>v0.83: a carrier with four destinations, always the nearest or the best before every trip</li>
 * <li>v0.83: clerks, the daily wage and price impact before and the share of the sales and price impact now</li>
 * </ul>
 */
class CarrierProfitSimulation {
	@Test
	void estimate() throws IOException {
		Assumptions.assumeTrue(System.getenv("AK_SIM") != null, "set AK_SIM to run the profit estimate");
		List<CarrierProfitSim.Def> defs = CarrierProfitSim.loadGoods();
		StringBuilder out = new StringBuilder();
		for (CarrierProfitSim.Params params : new CarrierProfitSim.Params[] {CarrierProfitSim.Params.OLD, CarrierProfitSim.Params.NEW}) {
			out.append(String.format("== one destination, spread x%.2f, impact %.2f ==%n", params.spreadFactor(), params.impact()));
			out.append("distance | backhaul | net/day mean | median | days1-15 | days16-30 | trips | empty trips | backhaul part/day\n");
			for (double d : new double[] {64, 128, 256, 500, 1000, 2000}) {
				for (boolean bh : new boolean[] {false, true}) {
					out.append(row(defs, d, 1, bh, false, params));
				}
			}
		}
		out.append("== four destinations at distance x1, x1.5, x2, x2.5 (v0.83 prices, return cargo) ==\n");
		out.append("distance | roam | net/day mean | median | days1-15 | days16-30 | trips | empty trips | backhaul part/day | switches\n");
		for (double d : new double[] {128, 256, 500, 1000}) {
			for (boolean roam : new boolean[] {false, true}) out.append(row(defs, d, 4, true, roam, CarrierProfitSim.Params.NEW));
		}
		out.append(clerks());
		System.out.println(out);
		Files.writeString(Path.of("build/carrier-profit.txt"), out.toString());
	}

	private static String row(List<CarrierProfitSim.Def> defs, double d, int destinations, boolean backhaul, boolean roam,
			CarrierProfitSim.Params params) {
		List<Double> perDay = new ArrayList<>();
		double first = 0, second = 0, trips = 0, empty = 0, part = 0, switches = 0;
		for (int run = 0; run < CarrierProfitSim.RUNS; run++) {
			Random r = new Random(run * 7919L + 17); // the same markets in every variant
			CarrierProfitSim.Mkt[] m = CarrierProfitSim.markets(defs, r, destinations);
			CarrierProfitSim.Result res = new CarrierProfitSim(m, d, backhaul, roam, params, r).run();
			perDay.add(res.net() / CarrierProfitSim.DAYS);
			first += res.firstHalf() / (CarrierProfitSim.DAYS / 2);
			second += res.secondHalf() / (CarrierProfitSim.DAYS / 2);
			trips += res.trips();
			empty += res.emptyTrips();
			part += res.backhaulProfit() / CarrierProfitSim.DAYS;
			switches += res.switches();
		}
		perDay.sort(Double::compare);
		double mean = perDay.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		int n = CarrierProfitSim.RUNS;
		String flag = destinations > 1 ? roam ? "roam" : "near" : backhaul ? "on " : "off";
		return String.format("%5.0f | %s | %6.1f | %6.1f | %6.1f | %6.1f | %5.1f | %5.1f | %6.1f%s%n", d, flag, mean, perDay.get(n / 2), first / n,
				second / n, trips / n, empty / n, part / n, destinations > 1 ? String.format(" | %5.1f", switches / n) : "");
	}

	/**
	 * Clerks with four times the target stock of a good at a market where nobody else trades: the lowest price 85% of the
	 * sell price at the start, a look every 0.05 days, the market recovering in between (B-3). Before v0.83: X +1 per unit
	 * and 30c a day while it holds goods; now: X +0.5 per unit and 10% of the sales.
	 */
	private static String clerks() {
		StringBuilder out = new StringBuilder("== clerks (4 x target stock) ==\n");
		out.append("goods | variant | sold day 1 | days to sell out | sales | pay | sales - pay | per item\n");
		// base, tau, target, max stock, restock, consume per day, half-life (bread and wheat are consumables: c = T, H = 1)
		Object[][] goods = {
				{"bread", 20.0, 32.0, 16.0, 32.0, 8.0, 16.0, 1.0},
				{"iron_ingot", 60.0, 32.0, 16.0, 32.0, 8.0, 4.0, 2.0},
				{"diamond_sword", 900.0, 16.0, 8.0, 24.0, 4.0, 2.0, 2.0},
				{"wheat", 7.0, 128.0, 64.0, 128.0, 32.0, 64.0, 1.0}};
		for (Object[] g : goods) {
			GoodParams p = PriceModel.ensureExploitSafe(new GoodParams((double) g[1], (double) g[2], 0.4, (double) g[3], (double) g[4], (double) g[5], 0.25,
					3.0, (double) g[6], (double) g[7]));
			int held = (int) (4 * (double) g[3]);
			for (int variant = 0; variant < 2; variant++) {
				boolean now = variant == 1;
				double impact = now ? 0.5 : 1.0;
				GoodState s = new GoodState(1.0, 0, p.targetStock(), 0);
				long limit = (long) Math.floor(PriceModel.sellPrice(p, 0) * 0.85);
				int left = held;
				int day1 = 0;
				long sales = 0;
				double soldOut = -1;
				double step = 0.05;
				int days = 0;
				for (double t = 0; t < 120 && left > 0; t += step) {
					StaffMath.SellPlan plan = StaffMath.planSell(p, s.pressure(), Math.min(left, PriceModel.sellCapacity(p, s)), limit, impact);
					if (plan.units() > 0) {
						s.setPressure(p.clampPressure(s.pressure() + plan.units() * impact));
						s.setStock(s.stock() + plan.units());
						left -= plan.units();
						sales += plan.total();
						if (t < 1) day1 += plan.units();
						if (left <= 0) soldOut = t + step;
					}
					PriceModel.recover(p, s, step, 1.0);
					days = (int) Math.ceil(t + step);
				}
				long pay = now ? StaffMath.commission(sales, 0.10) : 30L * days;
				out.append(String.format("%s %d | %s | %d | %s | %d | %d | %d | %.1f%n", g[0], held, now ? "v0.83" : "before", day1,
						soldOut < 0 ? ">120" : String.format("%.1f", soldOut), sales, pay, sales - pay, (sales - pay) / (double) Math.max(1, held - left)));
			}
		}
		return out.toString();
	}
}
