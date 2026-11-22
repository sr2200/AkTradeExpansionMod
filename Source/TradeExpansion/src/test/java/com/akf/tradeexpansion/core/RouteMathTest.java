package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteMathTest {
	private static RouteMath.Point post(double x, double z) {
		return new RouteMath.Point(0, x, z);
	}

	private static RouteMath.Point market(long id, double x, double z) {
		return new RouteMath.Point(id, x, z);
	}

	/** Signposts every {@code step} blocks from x0 to x1 along z = 0. */
	private static List<RouteMath.Point> line(double x0, double x1, double step) {
		List<RouteMath.Point> list = new ArrayList<>();
		for (double x = x0; x <= x1 + 1e-9; x += step) list.add(post(x, 0));
		return list;
	}

	@Test
	void aChainOfSignpostsConnectsTwoMarkets() {
		var r = RouteMath.routes(line(40, 1960, 60), List.of(market(1, 0, 0), market(2, 2000, 0)), 64, 64, 4);
		assertEquals(List.of(new RouteMath.Route(1, 2)), r.routes());
		assertEquals(List.of(2L), r.partners(1));
		assertEquals(List.of(1L), r.partners(2));
		assertEquals(1, r.markets().size()); // one road
	}

	@Test
	void aGapLongerThanTheSpacingBreaksTheRoad() {
		List<RouteMath.Point> posts = new ArrayList<>(line(40, 880, 60));
		posts.addAll(line(1000, 1960, 60)); // 880 -> 1000: 120 blocks
		var r = RouteMath.routes(posts, List.of(market(1, 0, 0), market(2, 2000, 0)), 64, 64, 4);
		assertTrue(r.routes().isEmpty());
		assertEquals(2, r.markets().size());
		assertEquals(List.of(1L), r.markets().get(r.road()[0]));
		assertEquals(List.of(2L), r.markets().get(r.road()[posts.size() - 1]));
	}

	@Test
	void bothEndsMustReachAMarket() {
		// The last signpost stands 100 blocks from market 2's center: outside its range (64).
		var r = RouteMath.routes(line(40, 1900, 60), List.of(market(1, 0, 0), market(2, 2000, 0)), 64, 64, 4);
		assertTrue(r.routes().isEmpty());
	}

	@Test
	void distanceIsHorizontal() {
		var r = RouteMath.routes(List.of(post(30, 0), post(90, 0)), List.of(market(1, 0, 0), market(2, 120, 0)), 64, 64, 4);
		assertEquals(1, r.routes().size());
	}

	@Test
	void aRoadThroughThreeMarketsConnectsEveryPair() {
		var r = RouteMath.routes(line(0, 2000, 50), List.of(market(1, 0, 0), market(2, 1000, 0), market(3, 2000, 0)), 64, 64, 4);
		assertEquals(3, r.routes().size());
		assertEquals(List.of(1L, 2L, 3L), r.markets().get(0));
	}

	@Test
	void theLimitKeepsTheShortestRoutes() {
		// A star of roads from market 1 to four others at different distances; the limit is 2.
		List<RouteMath.Point> posts = new ArrayList<>();
		List<RouteMath.Point> markets = new ArrayList<>(List.of(market(1, 0, 0)));
		double[][] ends = {{600, 0}, {0, 300}, {-900, 0}, {0, -450}};
		for (int i = 0; i < ends.length; i++) {
			double ex = ends[i][0], ez = ends[i][1];
			double len = Math.hypot(ex, ez);
			for (double t = 40; t <= len - 40 + 1e-9; t += 50) posts.add(post(ex / len * t, ez / len * t));
			markets.add(market(i + 2, ex, ez));
		}
		// The roads meet near market 1 (within 64 of each other there), so it is one road touching all five markets.
		var r = RouteMath.routes(posts, markets, 64, 64, 2);
		for (long m = 1; m <= 5; m++) assertTrue(r.partners(m).size() <= 2, "market " + m + " has " + r.partners(m));
		assertEquals(List.of(3L, 5L), r.partners(1).stream().sorted().toList()); // 300 and 450 blocks
		assertTrue(r.blocked().contains(new RouteMath.Route(1, 2)));
	}

	@Test
	void noLimitMeansNoRoutes() {
		var r = RouteMath.routes(line(40, 1960, 60), List.of(market(1, 0, 0), market(2, 2000, 0)), 64, 64, 0);
		assertTrue(r.routes().isEmpty());
		assertEquals(List.of(new RouteMath.Route(1, 2)), r.blocked());
	}

	@Test
	void noSignposts() {
		assertTrue(RouteMath.routes(List.of(), List.of(market(1, 0, 0)), 64, 64, 4).routes().isEmpty());
	}

	@Test
	void routeWeightIsAtLeastTheRoadWeight() {
		assertEquals(0.5, RouteMath.routeWeight(0.0, 0.5), 1e-9);
		assertEquals(0.8, RouteMath.routeWeight(0.8, 0.5), 1e-9);
		assertEquals(1.0, RouteMath.routeWeight(0.2, 3.0), 1e-9);
		assertEquals(0.2, RouteMath.routeWeight(0.2, -1), 1e-9);
	}
}
