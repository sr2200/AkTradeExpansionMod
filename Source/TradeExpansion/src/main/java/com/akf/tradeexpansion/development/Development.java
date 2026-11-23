package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.market.Market;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Development points of each market (design N-2): raised by donations, delivered requests and trades, spent on facilities.
 * Decay ({@code development.halfLifeDays}, off by default) is applied lazily when the points are read.
 */
public final class Development {
	private Development() {
	}

	public static TradeExpansionConfig.Development config() {
		return TradeExpansionConfig.get().development;
	}

	/** v0.67 N-5: radius searched for a site and for stakes around {@code market}; it grows with the market level. */
	public static int siteRadius(com.akf.tradeexpansion.market.Market market) {
		TradeExpansionConfig.Development c = config();
		return com.akf.tradeexpansion.core.DevelopmentMath.siteRadius(c.siteRadius, c.siteRadiusPerLevel, market.level());
	}

	/** v0.67 N-5: the nearest market whose site radius reaches {@code pos}, or {@code null}. */
	public static com.akf.tradeexpansion.market.Market siteMarket(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		com.akf.tradeexpansion.market.Market market = com.akf.tradeexpansion.market.MarketRegistry.get(level.getServer())
				.nearestWithin(level.dimension(), pos, com.akf.tradeexpansion.core.DevelopmentMath.MAX_SITE_RADIUS);
		return market != null && market.horizontalDistanceTo(pos) <= siteRadius(market) ? market : null;
	}

	/** Radius told when a stake has no market: that of the nearest market in reach, else the level 1 radius. */
	public static int siteRadiusNear(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		com.akf.tradeexpansion.market.Market market = com.akf.tradeexpansion.market.MarketRegistry.get(level.getServer())
				.nearestWithin(level.dimension(), pos, com.akf.tradeexpansion.core.DevelopmentMath.MAX_SITE_RADIUS);
		return market != null ? siteRadius(market) : config().siteRadius;
	}

	/** Markets of this dimension can develop ({@code development.enabled} and {@code development.dimensions}). */
	public static boolean enabledIn(ResourceKey<Level> dimension) {
		TradeExpansionConfig.Development c = config();
		return c.enabled && c.dimensions.contains(dimension.identifier().toString());
	}

	public static boolean enabled(Market market) {
		return enabledIn(market.dimension());
	}

	/** Points of {@code market} at {@code now}, decay applied (and stored). */
	public static double points(Market market, long now) {
		double points = market.development();
		if (market.developmentSince() == Long.MIN_VALUE) return points;
		double days = (now - market.developmentSince()) / (double) PriceModel.TICKS_PER_DAY;
		if (days <= 0) return points;
		double decayed = DevelopmentMath.decay(points, days, config().halfLifeDays);
		market.setDevelopment(decayed, now);
		return decayed;
	}

	/**
	 * Adds points brought by {@code player} (also counted for the player in the contributors list), × {@code
	 * development.parkPointsMultiplier} while the market has a park and × {@code festivalMultiplier} on a festival day. Nothing happens in dimensions that do not develop.
	 * The caller marks the registry dirty.
	 *
	 * @return the points actually added
	 */
	public static double add(Market market, ServerPlayer player, double points, long now) {
		if (points <= 0 || !enabled(market)) return 0;
		if (market.hasFacility(FacilityKind.PARK)) points *= FacilityEffects.raise(market, FacilityKind.PARK, 0, config().parkPointsMultiplier);
		points *= FacilityEffects.perk(market, FacilityKind.Perk.DEVELOPMENT); // N-11 (v0.86): directions that bring more points
		points *= Festival.multiplier(market, now); // v0.51 festival day
		points *= vigorMultiplier(market); // K-2 (v0.78): a village low in vigor grows a little slower
		market.setDevelopment(points(market, now) + points, now);
		UUID uuid = player.getUUID();
		Market.Contributor old = market.contributors().get(uuid);
		double total = (old == null ? 0 : old.points()) + points;
		market.contributors().put(uuid, new Market.Contributor(uuid, player.getPlainTextName(), total));
		return points;
	}

	/** K-2: multiplier of the points brought to {@code market} from its vigor (1 from vigor 50 up). */
	public static double vigorMultiplier(Market market) {
		return com.akf.tradeexpansion.core.DemandMath.pointsMultiplier(market.vigor(), TradeExpansionConfig.get().demand.vigorPenalty);
	}

	/** Spends points (building a facility). @return {@code false} if there are not enough */
	public static boolean spend(Market market, double cost, long now) {
		double points = points(market, now);
		if (points + 1e-9 < cost) return false;
		market.setDevelopment(points - cost, now);
		return true;
	}

	/** N-2: a trade of {@code total} copper at the market. */
	public static void onTrade(Market market, ServerPlayer player, long total, long now) {
		add(market, player, total * config().tradePointsPerCoin, now);
	}

	/** N-2: a delivered request paid {@code reward}; for a transport request the caller adds it to both markets. */
	public static void onRequest(Market market, ServerPlayer player, long reward, long now) {
		add(market, player, reward * config().questRewardRatio, now);
	}

	/** Contributors with the most points first (ties: the earlier one). */
	public static List<Market.Contributor> top(Market market, int count) {
		List<Market.Contributor> list = new ArrayList<>(market.contributors().values());
		list.sort(Comparator.comparingDouble(Market.Contributor::points).reversed());
		return list.subList(0, Math.min(count, list.size()));
	}
}
