package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialHashTest {
	private record P(int id, int x, int z) {
	}

	private static final Comparator<P> BY_ID = Comparator.comparingInt(P::id);

	private static SpatialHash<P> hash(List<P> points) {
		SpatialHash<P> hash = new SpatialHash<>(512);
		points.forEach(p -> hash.add(p, p.x(), p.z()));
		return hash;
	}

	private static double d(P p, double x, double z) {
		return DistanceModel.horizontalDistance(p.x(), p.z(), x, z);
	}

	/** The linear scan MarketRegistry used before the index. */
	private static P linearNearest(List<P> points, double x, double z, double radius) {
		return points.stream().filter(p -> d(p, x, z) <= radius)
				.min(Comparator.comparingDouble((P p) -> d(p, x, z)).thenComparing(BY_ID)).orElse(null);
	}

	@Test
	void emptyHash() {
		SpatialHash<P> hash = new SpatialHash<>(512);
		assertNull(hash.nearest(0, 0, Double.MAX_VALUE, BY_ID));
		assertTrue(hash.within(0, 0, 1000).isEmpty());
	}

	@Test
	void radiusIsInclusive() {
		P a = new P(1, 64, 0);
		SpatialHash<P> hash = hash(List.of(a));
		assertEquals(a, hash.nearest(0, 0, 64, BY_ID));
		assertNull(hash.nearest(0, 0, 63.9, BY_ID));
		assertEquals(List.of(a), hash.within(0, 0, 64));
		assertTrue(hash.within(0, 0, 63.9).isEmpty());
	}

	@Test
	void tiesGoToTheSmallerId() {
		P a = new P(2, 100, 0);
		P b = new P(1, -100, 0);
		assertEquals(b, hash(List.of(a, b)).nearest(0, 0, 500, BY_ID));
	}

	@Test
	void crossesCellAndSignBoundaries() {
		P a = new P(1, -1, -1);
		P b = new P(2, 511, 511);
		P c = new P(3, -513, 0);
		SpatialHash<P> hash = hash(List.of(a, b, c));
		assertEquals(a, hash.nearest(0, 0, 10, BY_ID));
		assertEquals(c, hash.nearest(-600, 0, 100, BY_ID));
		assertEquals(Set.of(a, c), new HashSet<>(hash.within(-256, 0, 300)));
	}

	@Test
	void removeAndMove() {
		P a = new P(1, 0, 0);
		SpatialHash<P> hash = hash(List.of(a));
		assertTrue(hash.remove(a));
		assertFalse(hash.remove(a));
		assertEquals(0, hash.size());
		hash.add(a, 5000, 5000);
		hash.add(a, 10, 10); // moved, not duplicated
		assertEquals(1, hash.size());
		assertEquals(a, hash.nearest(0, 0, 20, BY_ID));
		assertTrue(hash.within(5000, 5000, 100).isEmpty());
	}

	@Test
	void sameAnswersAsALinearScan() {
		Random random = new Random(42);
		List<P> points = new ArrayList<>();
		for (int i = 0; i < 600; i++) points.add(new P(i, random.nextInt(40000) - 20000, random.nextInt(40000) - 20000));
		// Clusters (villages close together) and two markets at the same position.
		for (int i = 600; i < 700; i++) points.add(new P(i, 3000 + random.nextInt(200), -7000 + random.nextInt(200)));
		points.add(new P(700, 3000, -7000));
		points.add(new P(701, 3000, -7000));
		SpatialHash<P> hash = hash(points);
		double[] radii = {0, 64, 300, 1536, 5000, Double.MAX_VALUE};
		for (int q = 0; q < 400; q++) {
			double x = random.nextInt(50000) - 25000 + random.nextDouble();
			double z = random.nextInt(50000) - 25000;
			if (q % 4 == 0) {
				x = 3000 + random.nextInt(300);
				z = -7000 + random.nextInt(300);
			}
			for (double r : radii) {
				assertEquals(linearNearest(points, x, z, r), hash.nearest(x, z, r, BY_ID), "nearest at " + x + "," + z + " r=" + r);
				final double fx = x, fz = z;
				Set<P> expected = new HashSet<>(points.stream().filter(p -> d(p, fx, fz) <= r).toList());
				assertEquals(expected, new HashSet<>(hash.within(x, z, r)), "within at " + x + "," + z + " r=" + r);
			}
		}
	}

	@Test
	void fewPointsFarApart() {
		// Large radius with few points: scans all points instead of visiting empty cells.
		P a = new P(1, 1_000_000, 0);
		P b = new P(2, -2_000_000, 0);
		SpatialHash<P> hash = hash(List.of(a, b));
		assertEquals(a, hash.nearest(0, 0, Double.MAX_VALUE, BY_ID));
		assertEquals(Set.of(a, b), new HashSet<>(hash.within(0, 0, 3_000_000)));
	}
}
