package com.akf.tradeexpansion.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Trade routes of signposts (design C-5): signposts within {@code spacing} of each other (horizontal) form one road;
 * a road touches every market whose center is within {@code reach} of one of its signposts, and connects each pair of
 * the markets it touches. Each market keeps at most {@code maxPerMarket} routes, the shortest first (ties: smaller ids).
 * Roads are judged from the saved signpost positions only, never from the blocks between them. Minecraft-independent.
 */
public final class RouteMath {
	private RouteMath() {
	}

	/** A signpost or a market center (x, z); {@code id} is the market id (unused for signposts). */
	public record Point(long id, double x, double z) {
	}

	/** Two connected markets, {@code a < b}. */
	public record Route(long a, long b) {
		public Route {
			if (a > b) {
				long t = a;
				a = b;
				b = t;
			}
		}

		public boolean has(long market) {
			return a == market || b == market;
		}

		public long other(long market) {
			return a == market ? b : a;
		}
	}

	/**
	 * @param routes    the routes in effect
	 * @param blocked   pairs a road connects that were dropped by the per-market limit
	 * @param road      road number of each signpost (same order as the input)
	 * @param markets   markets each road touches, by road number (smallest id first)
	 */
	public record Result(List<Route> routes, List<Route> blocked, int[] road, List<List<Long>> markets) {
		/** Markets connected with {@code market} by a route. */
		public List<Long> partners(long market) {
			List<Long> list = new ArrayList<>();
			for (Route r : routes) if (r.has(market)) list.add(r.other(market));
			return list;
		}

		public static final Result EMPTY = new Result(List.of(), List.of(), new int[0], List.of());
	}

	public static Result routes(List<Point> signposts, List<Point> markets, double spacing, double reach, int maxPerMarket) {
		int n = signposts.size();
		if (n == 0) return Result.EMPTY;
		// Roads: union of signposts within spacing of each other (only nearby cells are looked at).
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;
		SpatialHash<Integer> posts = new SpatialHash<>(Math.max(1, spacing));
		// Items are compared by identity: each index is boxed once here and the same object comes back from within().
		for (int i = 0; i < n; i++) posts.add(Integer.valueOf(i), signposts.get(i).x(), signposts.get(i).z());
		for (int i = 0; i < n; i++) {
			Point p = signposts.get(i);
			for (Integer j : posts.within(p.x(), p.z(), spacing)) union(parent, i, j);
		}
		int[] road = new int[n];
		Map<Integer, Integer> numbers = new HashMap<>();
		for (int i = 0; i < n; i++) road[i] = numbers.computeIfAbsent(find(parent, i), k -> numbers.size());
		List<TreeSet<Long>> touched = new ArrayList<>();
		for (int i = 0; i < numbers.size(); i++) touched.add(new TreeSet<>());
		Map<Long, Point> byId = new HashMap<>();
		for (Point m : markets) {
			byId.put(m.id(), m);
			for (Integer j : posts.within(m.x(), m.z(), reach)) touched.get(road[j]).add(m.id());
		}
		// Every pair of markets on the same road, shortest first.
		TreeSet<Route> pairs = new TreeSet<>(Comparator.comparingLong(Route::a).thenComparingLong(Route::b));
		for (TreeSet<Long> ids : touched) {
			List<Long> list = new ArrayList<>(ids);
			for (int i = 0; i < list.size(); i++) for (int j = i + 1; j < list.size(); j++) pairs.add(new Route(list.get(i), list.get(j)));
		}
		List<Route> sorted = new ArrayList<>(pairs);
		sorted.sort(Comparator.<Route>comparingDouble(r -> distance(byId.get(r.a()), byId.get(r.b())))
				.thenComparingLong(Route::a).thenComparingLong(Route::b));
		Map<Long, Integer> count = new HashMap<>();
		List<Route> routes = new ArrayList<>();
		List<Route> blocked = new ArrayList<>();
		for (Route r : sorted) {
			if (count.getOrDefault(r.a(), 0) < maxPerMarket && count.getOrDefault(r.b(), 0) < maxPerMarket) {
				routes.add(r);
				count.merge(r.a(), 1, Integer::sum);
				count.merge(r.b(), 1, Integer::sum);
			} else {
				blocked.add(r);
			}
		}
		List<List<Long>> roadMarkets = new ArrayList<>();
		for (TreeSet<Long> ids : touched) roadMarkets.add(List.copyOf(ids));
		return new Result(List.copyOf(routes), List.copyOf(blocked), road, List.copyOf(roadMarkets));
	}

	/** C-5: the propagation weight between two markets on a route: at least {@code roadWeight}. */
	public static double routeWeight(double weight, double roadWeight) {
		return Math.max(weight, Math.clamp(roadWeight, 0.0, 1.0));
	}

	private static double distance(Point a, Point b) {
		return DistanceModel.horizontalDistance(a.x(), a.z(), b.x(), b.z());
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		int ra = find(parent, a), rb = find(parent, b);
		if (ra != rb) parent[Math.max(ra, rb)] = Math.min(ra, rb);
	}
}
