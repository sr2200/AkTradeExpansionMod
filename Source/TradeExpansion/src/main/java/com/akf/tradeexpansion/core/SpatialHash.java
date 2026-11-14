package com.akf.tradeexpansion.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horizontal spatial hash of points (design C-2 {@code MarketIndex}): items are bucketed into square cells so range and
 * nearest queries only look at nearby cells. Distances are the plain horizontal distance of
 * {@link DistanceModel#horizontalDistance}. Minecraft-independent; not thread-safe.
 *
 * @param <T> the item (compared by identity)
 */
public final class SpatialHash<T> {
	private record Point(double x, double z, long cell) {
	}

	private final double cellSize;
	private final Map<Long, List<T>> cells = new HashMap<>();
	private final Map<T, Point> points = new IdentityHashMap<>();

	public SpatialHash(double cellSize) {
		if (!(cellSize > 0)) throw new IllegalArgumentException("cellSize must be positive: " + cellSize);
		this.cellSize = cellSize;
	}

	public int size() {
		return points.size();
	}

	/** Adds {@code item} at (x, z); an item already present is moved there. */
	public void add(T item, double x, double z) {
		remove(item);
		long cell = key(cell(x), cell(z));
		points.put(item, new Point(x, z, cell));
		cells.computeIfAbsent(cell, k -> new ArrayList<>()).add(item);
	}

	public boolean remove(T item) {
		Point p = points.remove(item);
		if (p == null) return false;
		List<T> list = cells.get(p.cell());
		list.remove(item);
		if (list.isEmpty()) cells.remove(p.cell());
		return true;
	}

	/** Items within {@code radius} (inclusive) of (x, z), in no particular order. */
	public List<T> within(double x, double z, double radius) {
		List<T> result = new ArrayList<>();
		if (points.isEmpty() || radius < 0 || Double.isNaN(radius)) return result;
		long reach = cellReach(radius);
		if (scanAll(reach)) {
			points.forEach((item, p) -> {
				if (distance(p, x, z) <= radius) result.add(item);
			});
			return result;
		}
		int cx = cell(x);
		int cz = cell(z);
		for (long i = cx - reach; i <= cx + reach; i++) {
			for (long j = cz - reach; j <= cz + reach; j++) {
				List<T> list = cells.get(key((int) i, (int) j));
				if (list == null) continue;
				for (T item : list) {
					if (distance(points.get(item), x, z) <= radius) result.add(item);
				}
			}
		}
		return result;
	}

	/**
	 * Nearest item within {@code radius} (inclusive) of (x, z), or {@code null}. Equal distances: the smallest by
	 * {@code tieBreak}.
	 */
	public T nearest(double x, double z, double radius, Comparator<? super T> tieBreak) {
		if (points.isEmpty() || radius < 0 || Double.isNaN(radius)) return null;
		long reach = cellReach(radius);
		Best<T> best = new Best<>(tieBreak);
		if (scanAll(reach)) {
			points.forEach((item, p) -> best.offer(item, distance(p, x, z), radius));
			return best.item;
		}
		int cx = cell(x);
		int cz = cell(z);
		for (long ring = 0; ring <= reach; ring++) {
			// Every cell of ring k is at least (k - 1) cells away, so once the best is within that, outer rings cannot win.
			if (best.item != null && best.distance < (ring - 1) * cellSize) break;
			for (long i = cx - ring; i <= cx + ring; i++) {
				boolean edge = i == cx - ring || i == cx + ring;
				for (long j = cz - ring; j <= cz + ring; j += edge ? 1 : 2 * ring) {
					List<T> list = cells.get(key((int) i, (int) j));
					if (list != null) {
						for (T item : list) best.offer(item, distance(points.get(item), x, z), radius);
					}
					if (ring == 0) break;
				}
			}
		}
		return best.item;
	}

	private static final class Best<T> {
		private final Comparator<? super T> tieBreak;
		T item;
		double distance = Double.POSITIVE_INFINITY;

		Best(Comparator<? super T> tieBreak) {
			this.tieBreak = tieBreak;
		}

		void offer(T candidate, double d, double radius) {
			if (d > radius) return;
			if (item == null || d < distance || d == distance && tieBreak.compare(candidate, item) < 0) {
				item = candidate;
				distance = d;
			}
		}
	}

	/** Cells to look at on each side of the query cell (a point within radius is at most this many cells away). */
	private long cellReach(double radius) {
		double cellsAway = Math.ceil(radius / cellSize);
		return cellsAway >= Integer.MAX_VALUE / 4.0 ? Long.MAX_VALUE / 4 : (long) cellsAway;
	}

	/** Looking at every item is cheaper than visiting this many cells (also covers huge or infinite radii). */
	private boolean scanAll(long reach) {
		if (reach >= 1 << 15) return true;
		long side = 2 * reach + 1;
		return side * side > 4L * points.size();
	}

	private static double distance(Point p, double x, double z) {
		return DistanceModel.horizontalDistance(p.x(), p.z(), x, z);
	}

	private int cell(double v) {
		return (int) Math.floor(v / cellSize);
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}
}
