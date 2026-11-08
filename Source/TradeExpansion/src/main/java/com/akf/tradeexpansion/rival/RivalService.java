package com.akf.tradeexpansion.rival;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.RivalMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rival guilds (design O): campaigns are rolled once a day for the whole world (O-3) and applied lazily to each market
 * when it is touched (O-4). A campaign pushes X of one good at the start of each of its days; since X recovers linearly,
 * a push made on day k is added now already recovered to now.
 */
public final class RivalService {
	/** O-4: how many days back campaigns are rolled / applied; finished campaigns are dropped this long after their end. */
	public static final int KEEP_DAYS = 16;

	private RivalService() {
	}

	private static TradeExpansionConfig.Rivals config() {
		return TradeExpansionConfig.get().rivals;
	}

	public static long day(long gameTime) {
		return Math.floorDiv(gameTime, PriceModel.TICKS_PER_DAY);
	}

	/** O-4 step 1: rolls the campaigns of the days since the last roll. @return {@code true} if anything changed */
	public static boolean advance(ServerLevel level, MarketRegistry registry, long now) {
		if (!config().enabled) return false;
		long today = day(now);
		long last = registry.rivalDay();
		if (last != Long.MIN_VALUE && last >= today) return false;
		long from = last == Long.MIN_VALUE ? today : Math.max(last + 1, today - KEEP_DAYS + 1);
		for (long d = from; d <= today; d++) {
			long dayIndex = d;
			registry.campaigns().removeIf(c -> c.endDay() <= dayIndex - KEEP_DAYS);
			Random random = new Random(level.getSeed() ^ (d * 0x2545F4914F6CDD1DL) ^ 0x7A3C1F0BL);
			if (random.nextDouble() >= config().chancePerDay) continue;
			if (active(registry, d).size() >= config().maxActive) continue;
			Campaign campaign = roll(registry, null, d, random);
			if (campaign != null) registry.campaigns().add(campaign);
		}
		registry.setRivalDay(today);
		registry.setDirty();
		return true;
	}

	/**
	 * Starts a campaign now (command {@code rivals start}): centered on {@code center}, or on a random market when
	 * {@code null}. @return the campaign, or {@code null} when no good qualifies
	 */
	public static Campaign startNow(ServerLevel level, MarketRegistry registry, Market center, long now) {
		long today = day(now);
		Campaign campaign = roll(registry, center, today, new Random(level.getSeed() ^ now ^ registry.campaigns().size()));
		if (campaign == null) return null;
		registry.campaigns().add(campaign);
		registry.setDirty();
		return campaign;
	}

	/** O-3: chooses the center market, good, guild, direction, length and strength of a new campaign. */
	private static Campaign roll(MarketRegistry registry, Market fixedCenter, long day, Random random) {
		TradeExpansionConfig.Rivals c = config();
		List<Market> markets = new ArrayList<>(fixedCenter != null ? List.of(fixedCenter) : registry.all());
		markets.sort(Comparator.comparingLong(Market::id));
		List<Market> centers = new ArrayList<>();
		List<List<Identifier>> choices = new ArrayList<>();
		List<Campaign> running = active(registry, day);
		for (Market m : markets) {
			List<Identifier> goods = eligibleGoods(m);
			// Not the same good twice in one region at the same time.
			goods.removeIf(g -> running.stream().anyMatch(other -> other.goods().equals(g) && near(registry, m, other)));
			if (goods.isEmpty()) continue;
			centers.add(m);
			choices.add(goods);
		}
		if (centers.isEmpty()) return null;
		int index = random.nextInt(centers.size());
		Market center = centers.get(index);
		List<Identifier> goods = choices.get(index);
		Identifier good = goods.get(random.nextInt(goods.size()));
		Identifier guild = pickGuild(good, random);
		if (guild == null) return null;
		boolean buying = random.nextBoolean();
		int days = c.minDays + random.nextInt(Math.max(1, c.maxDays - c.minDays + 1));
		double impact = c.minImpact + (c.maxImpact - c.minImpact) * random.nextDouble();
		return new Campaign(registry.takeCampaignId(), guild, good, center.id(), buying, day, day + days, impact);
	}

	private static boolean near(MarketRegistry registry, Market m, Campaign other) {
		Market otherCenter = registry.get(other.market());
		return otherCenter != null && linked(m.dimension(), otherCenter.dimension())
				&& MarketRegistry.linkedDistance(m, otherCenter) <= config().farRadius;
	}

	/** O-3: goods a campaign may move at this market, sorted by id. */
	public static List<Identifier> eligibleGoods(Market market) {
		List<Identifier> list = new ArrayList<>();
		for (Identifier goodsId : market.goods().keySet()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def == null || !def.buy() || !def.sell() || def.unit() != 1 || def.autoAdd().isPresent()) continue;
			if (market.isSpecial(goodsId) || market.params(goodsId) == null) continue;
			if (pickableGuilds(goodsId).isEmpty()) continue;
			list.add(goodsId);
		}
		list.sort(Comparator.comparing(Identifier::toString));
		return list;
	}

	private static List<Map.Entry<Identifier, RivalGuilds.Guild>> pickableGuilds(Identifier goodsId) {
		List<Map.Entry<Identifier, RivalGuilds.Guild>> list = new ArrayList<>();
		for (Map.Entry<Identifier, RivalGuilds.Guild> e : RivalGuilds.all().entrySet()) {
			if (e.getValue().weight() > 0 && e.getValue().dealsIn(goodsId)) list.add(e);
		}
		return list;
	}

	private static Identifier pickGuild(Identifier goodsId, Random random) {
		List<Map.Entry<Identifier, RivalGuilds.Guild>> guilds = pickableGuilds(goodsId);
		int total = guilds.stream().mapToInt(e -> e.getValue().weight()).sum();
		if (total <= 0) return null;
		int r = random.nextInt(total);
		for (Map.Entry<Identifier, RivalGuilds.Guild> e : guilds) {
			r -= e.getValue().weight();
			if (r < 0) return e.getKey();
		}
		return guilds.getLast().getKey();
	}

	/** Campaigns running on {@code day}. */
	public static List<Campaign> active(MarketRegistry registry, long day) {
		return registry.campaigns().stream().filter(c -> c.activeOn(day)).toList();
	}

	/**
	 * O-4 step 2: adds the pushes of the days since the market last received them; the market must be refreshed to
	 * {@code now}. @return {@code true} if anything changed
	 */
	public static boolean apply(MarketRegistry registry, Market market, long now) {
		long today = day(now);
		long last = market.rivalDay();
		if (last != Long.MIN_VALUE && last >= today) return false;
		market.setRivalDay(today);
		if (!config().enabled) return true;
		long from = last == Long.MIN_VALUE ? today : Math.max(last + 1, today - KEEP_DAYS + 1);
		for (Campaign c : registry.campaigns()) push(registry, market, c, from, today, now);
		return true;
	}

	/** Adds the pushes of {@code c} for days {@code from}..{@code to} (those it is running on), recovered to {@code now}. */
	public static void push(MarketRegistry registry, Market market, Campaign c, long from, long to, long now) {
		long first = Math.max(from, c.startDay());
		long end = Math.min(to, c.endDay() - 1);
		if (first > end) return;
		GoodState state = market.good(c.goods());
		GoodParams p = market.params(c.goods());
		if (state == null || p == null) return;
		double w = weight(registry, market, c);
		if (w <= 0) return;
		double push = RivalMath.dailyPush(p.elasticity(), p.halfLifeDays(), c.impact(), c.days()) * w;
		double x = state.pressure();
		for (long k = first; k <= end; k++) {
			double age = (now - k * PriceModel.TICKS_PER_DAY) / (double) PriceModel.TICKS_PER_DAY;
			double delta = push * RivalMath.decay(age, p.halfLifeDays());
			x += c.buying() ? -delta : delta; // buying up makes it dearer (X down)
		}
		state.setPressure(p.clampPressure(x));
	}

	/** O-3: share of the push a market gets: 1 at the center, falling off with distance, × the good's link factor. */
	public static double weight(MarketRegistry registry, Market market, Campaign c) {
		if (market.id() == c.market()) return 1.0;
		Market center = registry.get(c.market());
		if (center == null || !linked(market.dimension(), center.dimension())) return 0.0;
		GoodsDefinition def = GoodsCatalog.get(c.goods());
		if (def == null) return 0.0;
		double d = MarketRegistry.linkedDistance(market, center);
		TradeExpansionConfig config = TradeExpansionConfig.get();
		return DistanceModel.weight(d, config.rivals.nearRadius, config.rivals.farRadius, config.distance.falloffExponent) * def.link();
	}

	private static boolean linked(ResourceKey<Level> a, ResourceKey<Level> b) {
		return a.equals(b) || b.equals(MarketRegistry.linkedDimension(a));
	}

	/** O-5: the running campaign with the largest effect on this good at this market, or {@code null}. */
	public static Campaign strongest(MarketRegistry registry, Market market, Identifier goodsId, long now) {
		if (!config().enabled) return null;
		long today = day(now);
		Campaign best = null;
		double bestW = 0;
		for (Campaign c : registry.campaigns()) {
			if (!c.activeOn(today) || !c.goods().equals(goodsId)) continue;
			double w = weight(registry, market, c);
			if (w > bestW) {
				best = c;
				bestW = w;
			}
		}
		return best;
	}

	/** O-5: running campaigns for the board of {@code here}: centered within {@code radius}, or felt here. */
	public static List<Campaign> news(MarketRegistry registry, Market here, double radius, long now) {
		if (!config().enabled) return List.of();
		long today = day(now);
		List<Campaign> list = new ArrayList<>();
		for (Campaign c : registry.campaigns()) {
			if (!c.activeOn(today)) continue;
			Market center = registry.get(c.market());
			if (center == null) continue;
			boolean close = center.dimension().equals(here.dimension()) && center.horizontalDistanceTo(here.center()) <= radius;
			if (close || here.good(c.goods()) != null && weight(registry, here, c) > 0) list.add(c);
		}
		list.sort(Comparator.comparingDouble(c -> MarketRegistry.linkedDistance(here, registry.get(c.market()))));
		return list;
	}
}
