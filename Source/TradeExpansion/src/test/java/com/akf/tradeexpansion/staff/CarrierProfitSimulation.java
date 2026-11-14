package com.akf.tradeexpansion.staff;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Profit estimate of an automatic carrier with and without return cargo (design Q-5). Not a regular test: it runs only
 * with the environment variable AK_SIM set, and writes build/carrier-profit.txt.
 */
class CarrierProfitSimulation {
	@Test
	void estimate() throws IOException {
		Assumptions.assumeTrue(System.getenv("AK_SIM") != null, "set AK_SIM to run the profit estimate");
		List<CarrierProfitSim.Def> defs = CarrierProfitSim.loadGoods();
		StringBuilder out = new StringBuilder();
		out.append("distance | backhaul | net/day mean | median | days1-15 | days16-30 | trips | empty trips | backhaul part/day\n");
		for (double d : new double[] {64, 128, 256, 384, 500, 1000, 2000}) {
			for (boolean bh : new boolean[] {false, true}) {
				List<Double> perDay = new ArrayList<>();
				double first = 0, second = 0, trips = 0, empty = 0, part = 0;
				for (int run = 0; run < CarrierProfitSim.RUNS; run++) {
					Random r = new Random(run * 7919L + 17); // the same markets with and without return cargo
					CarrierProfitSim.Mkt[] m = CarrierProfitSim.markets(defs, r);
					CarrierProfitSim.Result res = new CarrierProfitSim(m[0], m[1], d, bh, r).run();
					perDay.add(res.net() / CarrierProfitSim.DAYS);
					first += res.firstHalf() / (CarrierProfitSim.DAYS / 2);
					second += res.secondHalf() / (CarrierProfitSim.DAYS / 2);
					trips += res.trips();
					empty += res.emptyTrips();
					part += res.backhaulProfit() / CarrierProfitSim.DAYS;
				}
				perDay.sort(Double::compare);
				double mean = perDay.stream().mapToDouble(Double::doubleValue).average().orElse(0);
				out.append(String.format("%5.0f | %s | %6.1f | %6.1f | %6.1f | %6.1f | %5.1f | %5.1f | %6.1f%n", d, bh ? "on " : "off", mean,
						perDay.get(CarrierProfitSim.RUNS / 2), first / CarrierProfitSim.RUNS, second / CarrierProfitSim.RUNS, trips / CarrierProfitSim.RUNS, empty / CarrierProfitSim.RUNS, part / CarrierProfitSim.RUNS));
			}
		}
		System.out.println(out);
		Files.writeString(Path.of("build/carrier-profit.txt"), out.toString());
	}
}
