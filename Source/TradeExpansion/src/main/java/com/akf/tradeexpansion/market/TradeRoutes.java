package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.RouteMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trade routes (design C-5): roads of signposts connect markets, whose prices then move together more strongly
 * ({@code route.roadWeight}, {@link Propagation}). The signposts are saved in {@link MarketRegistry}; the roads are
 * worked out from those positions only (no blocks are looked at, so unloaded chunks do not matter).
 */
public final class TradeRoutes {
	private TradeRoutes() {
	}

	/** The settings the routes depend on; a change of any works the routes out again. */
	record Key(boolean enabled, int spacing, double reach, int max) {
		static Key now() {
			TradeExpansionConfig c = TradeExpansionConfig.get();
			return new Key(c.route.enabled, c.route.signpostSpacing, c.distance.mergeRadius, c.route.maxRoutesPerMarket);
		}
	}

	/** Routes of one dimension and the signposts they were worked out from (same order as {@link RouteMath.Result#road()}). */
	public record Cached(RouteMath.Result result, List<BlockPos> posts, Map<BlockPos, Integer> index, Key key) {
		boolean current() {
			return key.equals(Key.now());
		}

		/** Road number of the signpost at {@code pos}, or -1. */
		public int road(BlockPos pos) {
			Integer i = index.get(pos);
			return i == null ? -1 : result.road()[i];
		}

		/** Signposts on the road {@code road}. */
		public int size(int road) {
			int n = 0;
			for (int r : result.road()) if (r == road) n++;
			return n;
		}
	}

	static Cached compute(ResourceKey<Level> dimension, Set<BlockPos> signposts, List<Market> markets) {
		Key key = Key.now();
		List<BlockPos> posts = new ArrayList<>(signposts);
		Map<BlockPos, Integer> index = new HashMap<>();
		List<RouteMath.Point> points = new ArrayList<>(posts.size());
		for (int i = 0; i < posts.size(); i++) {
			index.put(posts.get(i), i);
			points.add(new RouteMath.Point(0, posts.get(i).getX(), posts.get(i).getZ()));
		}
		List<RouteMath.Point> centers = new ArrayList<>(markets.size());
		for (Market m : markets) centers.add(new RouteMath.Point(m.id(), m.center().getX(), m.center().getZ()));
		RouteMath.Result result = RouteMath.routes(points, centers, key.spacing(), key.reach(), key.enabled() ? key.max() : 0);
		return new Cached(result, List.copyOf(posts), Map.copyOf(index), key);
	}

	/** Markets connected with {@code market} by a trade route (none when routes are off). */
	public static List<Market> partners(MarketRegistry registry, Market market) {
		if (!TradeExpansionConfig.get().route.enabled) return List.of();
		List<Market> list = new ArrayList<>();
		for (long id : registry.routes(market.dimension()).result().partners(market.id())) {
			Market other = registry.get(id);
			if (other != null) list.add(other);
		}
		return list;
	}

	/** C-5: the weight at least given to price changes between markets on a route ({@code route.roadWeight}). */
	public static double roadWeight() {
		return TradeExpansionConfig.get().route.roadWeight;
	}

	// ---- signpost blocks

	/** A signpost was placed: record it and tell the player what it connects. */
	public static void onPlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		Set<RouteMath.Route> before = new HashSet<>(registry.routes(level.dimension()).result().routes());
		registry.addSignpost(level.dimension(), pos);
		if (player == null) return;
		Cached after = registry.routes(level.dimension());
		boolean opened = false;
		for (RouteMath.Route r : after.result().routes()) {
			if (before.contains(r)) continue;
			player.sendSystemMessage(Component.translatable("message.ak_trade_expansion.route.opened", pair(registry, r),
					percent(roadWeight())).withStyle(ChatFormatting.GREEN));
			opened = true;
		}
		if (!opened) describe(player, level, pos);
	}

	/** A signpost was removed (broken, exploded, replaced): forget it. */
	public static void onRemoved(ServerLevel level, BlockPos pos) {
		MarketRegistry.get(level.getServer()).removeSignpost(level.dimension(), pos);
	}

	/** Right-click on a signpost: its road, the markets it reaches, its routes and how to go on. */
	public static void describe(ServerPlayer player, ServerLevel level, BlockPos pos) {
		for (Component line : lines(MarketRegistry.get(level.getServer()), level.dimension(), pos)) player.sendSystemMessage(line);
	}

	/** The lines {@link #describe} sends (also used by the tests). */
	public static List<Component> lines(MarketRegistry registry, ResourceKey<Level> dimension, BlockPos pos) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		List<Component> lines = new ArrayList<>();
		if (!config.route.enabled) {
			lines.add(Component.translatable("message.ak_trade_expansion.route.disabled").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		Cached cached = registry.routes(dimension);
		int road = cached.road(pos);
		if (road < 0) {
			registry.addSignpost(dimension, pos); // placed before it was recorded (e.g. by a structure): record it now
			cached = registry.routes(dimension);
			road = cached.road(pos);
			if (road < 0) return lines;
		}
		lines.add(Component.translatable("message.ak_trade_expansion.route.header", cached.size(road)).withStyle(ChatFormatting.GOLD));
		List<Long> markets = cached.result().markets().get(road);
		if (markets.isEmpty()) {
			lines.add(Component.translatable("message.ak_trade_expansion.route.no_market", (int) config.distance.mergeRadius));
		} else {
			MutableComponent names = Component.empty();
			for (int i = 0; i < markets.size(); i++) {
				if (i > 0) names.append(Component.translatable("message.ak_trade_expansion.route.separator"));
				names.append(name(registry, markets.get(i)));
			}
			lines.add(Component.translatable("message.ak_trade_expansion.route.markets", names));
		}
		boolean any = false;
		for (RouteMath.Route r : cached.result().routes()) {
			if (!markets.contains(r.a()) || !markets.contains(r.b())) continue;
			lines.add(Component.translatable("message.ak_trade_expansion.route.route", pair(registry, r), percent(roadWeight()))
					.withStyle(ChatFormatting.GREEN));
			any = true;
		}
		for (RouteMath.Route r : cached.result().blocked()) {
			if (!markets.contains(r.a()) || !markets.contains(r.b())) continue;
			lines.add(Component.translatable("message.ak_trade_expansion.route.blocked", pair(registry, r), config.route.maxRoutesPerMarket)
					.withStyle(ChatFormatting.RED));
		}
		if (!any) {
			double nearest = nearestOther(cached, pos);
			lines.add(Component.translatable("message.ak_trade_expansion.route.hint", config.route.signpostSpacing,
					(int) config.distance.mergeRadius).withStyle(ChatFormatting.GRAY));
			if (Double.isFinite(nearest) && nearest > config.route.signpostSpacing) {
				lines.add(Component.translatable("message.ak_trade_expansion.route.nearest", (int) Math.round(nearest)).withStyle(ChatFormatting.GRAY));
			}
		}
		return lines;
	}

	/** {@code /aktradeex market info}: the market's routes. */
	public static List<Component> marketLines(MarketRegistry registry, Market market) {
		List<Component> lines = new ArrayList<>();
		if (!TradeExpansionConfig.get().route.enabled) return lines;
		RouteMath.Result result = registry.routes(market.dimension()).result();
		for (RouteMath.Route r : result.routes()) {
			if (r.has(market.id())) lines.add(Component.literal(" route ↔ ").append(name(registry, r.other(market.id()))));
		}
		for (RouteMath.Route r : result.blocked()) {
			if (r.has(market.id())) lines.add(Component.literal(" route (over the limit) ↔ ").append(name(registry, r.other(market.id()))));
		}
		return lines;
	}

	private static double nearestOther(Cached cached, BlockPos pos) {
		double best = Double.POSITIVE_INFINITY;
		for (BlockPos p : cached.posts()) {
			if (p.equals(pos)) continue;
			best = Math.min(best, DistanceModel.horizontalDistance(p.getX(), p.getZ(), pos.getX(), pos.getZ()));
		}
		return best;
	}

	private static Component pair(MarketRegistry registry, RouteMath.Route r) {
		return Component.translatable("message.ak_trade_expansion.route.pair", name(registry, r.a()), name(registry, r.b()));
	}

	private static Component name(MarketRegistry registry, long id) {
		Market m = registry.get(id);
		return m == null ? Component.literal("#" + id) : MarketNames.name(m);
	}

	private static String percent(double weight) {
		return String.valueOf(Math.round(weight * 100));
	}
}
