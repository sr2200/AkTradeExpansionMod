package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceModelTest {
	private static double w(double d) {
		return DistanceModel.weight(d, 128, 1536, 2.0);
	}

	@Test
	void weightTable() {
		assertEquals(1.0, w(0));
		assertEquals(1.0, w(64));
		assertEquals(1.0, w(128));
		assertEquals(0.771, w(300), 1e-3);
		assertEquals(0.541, w(500), 1e-3);
		assertEquals(0.145, w(1000), 1e-3);
		assertEquals(0.0, w(1536));
		assertEquals(0.0, w(5000));
	}

	@Test
	void weightBoundaries() {
		assertEquals(1.0, w(128.0001), 1e-5);
		assertEquals(0.0, w(1535.9999), 1e-5);
		assertEquals(0.5, DistanceModel.weight(832, 128, 1536, 1.0), 1e-12);
	}

	@Test
	void swordPropagationTable() {
		GoodParams sword = new GoodParams(900, 16, 0.4, 8, 24, 4, 0.25, 3.0);
		assertEquals(444, PriceModel.sellPrice(sword, 10 * w(300)));
		assertEquals(513, PriceModel.sellPrice(sword, 10 * w(500)));
		assertEquals(657, PriceModel.sellPrice(sword, 10 * w(1000)));
	}

	@Test
	void initialPressure() {
		assertEquals(0.0, DistanceModel.initialPressure(List.of(), 128, 1536, 2));
		// Markets within Rnear win: plain average of them.
		assertEquals(5.0, DistanceModel.initialPressure(List.of(
				new DistanceModel.Neighbor(50, 4),
				new DistanceModel.Neighbor(100, 6),
				new DistanceModel.Neighbor(500, 100)), 128, 1536, 2), 1e-12);
		// Otherwise weighted by w(d).
		double w300 = w(300);
		double w1000 = w(1000);
		double expected = (w300 * 10 + w1000 * -2) / (w300 + w1000);
		assertEquals(expected, DistanceModel.initialPressure(List.of(
				new DistanceModel.Neighbor(300, 10),
				new DistanceModel.Neighbor(1000, -2),
				new DistanceModel.Neighbor(2000, 999)), 128, 1536, 2), 1e-12);
		// Only markets beyond Rfar: 0.
		assertEquals(0.0, DistanceModel.initialPressure(List.of(new DistanceModel.Neighbor(2000, 7)), 128, 1536, 2));
	}
}
