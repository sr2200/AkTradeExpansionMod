package com.akf.tradeexpansion.event;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DistanceModel;
import com.akf.tradeexpansion.core.EventMath;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.RivalMath;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.MarketEvents;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Market events (design V): once a day for the whole world an event may be rolled for the next day (the forecast); while
 * it runs it multiplies the base price B of its goods around its center market, falling off with distance. The
 * multipliers are copied to a market when it is touched ({@link #apply}) and used in {@link Market#params}.
 */
public final class EventService {
	/** Ticks between two looks at the market clock's day. */
	static final int CHECK_TICKS = 20;
	/** Spreads the daily seed; different from the rival guilds' so that both do not always happen on the same days. */
	private static final long DAY_MIX = 0x3C6EF372FE94F82BL;
	private static final long SALT = 0x6A09E667L;

	private static int tickCounter;

	private EventService() {
	}

	private static TradeExpansionConfig.Events config() {
		return TradeExpansionConfig.get().events;
	}

	public static void register() {
		// V-3: a new day is noticed even when nobody trades, so the forecast and the start can be told in the chat.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % CHECK_TICKS != 0) return;
			ServerLevel overworld = server.overworld();
			advance(overworld, MarketRegistry.get(server), MarketClock.now(overworld));
		});
	}

	public static long day(long time) {
		return Math.floorDiv(time, PriceModel.TICKS_PER_DAY);
	}

	/**
	 * V-3: rolls the events of the days since the last roll (each for the day after) and tells the players about the
	 * forecasts and the events starting today. @return {@code true} if anything changed
	 */
	public static boolean advance(ServerLevel level, MarketRegistry registry, long now) {
		if (!config().enabled) return false;
		long today = day(now);
		long last = registry.eventDay();
		if (last != Long.MIN_VALUE && last >= today) return false;
		registry.events().removeIf(e -> e.endDay() <= today);
		// Only today and the day before matter: older days' events would be over already.
		long from = last == Long.MIN_VALUE ? today : Math.max(last + 1, today - 1);
		List<MarketEvent> rolled = new ArrayList<>();
		for (long d = from; d <= today; d++) {
			long start = d + 1;
			Random random = new Random(level.getSeed() ^ (start * DAY_MIX) ^ SALT);
			if (random.nextDouble() >= config().chancePerDay) continue;
			if (registry.events().stream().filter(e -> e.activeOn(start)).count() >= config().maxActive) continue;
			MarketEvent event = roll(registry, null, null, start, random, false);
			if (event == null) continue;
			registry.events().add(event);
			rolled.add(event);
		}
		registry.setEventDay(today);
		registry.setDirty();
		MinecraftServer server = level.getServer();
		for (MarketEvent e : registry.events()) {
			if (e.startDay() == today) announce(server, registry, e, false);
			else if (e.startDay() == today + 1 && rolled.contains(e) && config().forecast) announce(server, registry, e, true);
		}
		return true;
	}

	/**
	 * Starts an event today (command {@code events start}) centered on {@code center}: {@code eventId}, or a random one
	 * that fits. @return the event, or {@code null} when none fits the market
	 */
	public static MarketEvent startNow(ServerLevel level, MarketRegistry registry, Identifier eventId, Market center, long now) {
		long today = day(now);
		long seed = (level.getSeed() ^ now ^ registry.events().size()) * 0x9E3779B97F4A7C15L;
		MarketEvent event = roll(registry, eventId, center, today, new Random(seed ^ (seed >>> 31)), true);
		if (event == null) return null;
		registry.events().add(event);
		registry.setDirty();
		return event;
	}

	/**
	 * V-3: chooses the event, its center, goods, multiplier and length. The center is drawn from the markets near
	 * ({@code board.radius}) a market players visited lately, or from all markets that fit when none is near one.
	 *
	 * @param command {@code events start}: the weight and the price direction settings are not looked at
	 */
	private static MarketEvent roll(MarketRegistry registry, Identifier fixedEvent, Market fixedCenter, long startDay, Random random, boolean command) {
		TradeExpansionConfig.Events c = config();
		List<Market> markets = new ArrayList<>(fixedCenter != null ? List.of(fixedCenter) : registry.all());
		markets.sort(Comparator.comparingLong(Market::id));
		List<Market> visited = registry.all().stream()
				.filter(m -> RivalMath.visitedLately(m.lastVisit(), startDay - 1, RivalService.RECENT_DAYS)).toList();
		double radius = TradeExpansionConfig.get().board.radius;
		// Per event: where it may be centered, and the same restricted to markets near a recent visit.
		List<Identifier> ids = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		List<List<Market>> centersOf = new ArrayList<>();
		List<List<List<Identifier>>> goodsOf = new ArrayList<>();
		List<List<Market>> nearCentersOf = new ArrayList<>();
		List<List<List<Identifier>>> nearGoodsOf = new ArrayList<>();
		for (Map.Entry<Identifier, MarketEvents.Definition> entry : MarketEvents.all().entrySet()) {
			MarketEvents.Definition def = entry.getValue();
			if (fixedEvent != null && !fixedEvent.equals(entry.getKey())) continue;
			if (!command && (def.weight() <= 0 || (def.raises() ? !c.priceUp : !c.priceDown))) continue;
			List<Market> centers = new ArrayList<>();
			List<List<Identifier>> choices = new ArrayList<>();
			List<Market> nearCenters = new ArrayList<>();
			List<List<Identifier>> nearChoices = new ArrayList<>();
			for (Market m : markets) {
				if (!fits(def, m)) continue;
				List<Identifier> goods = eligibleGoods(registry, m, def, startDay);
				if (goods.isEmpty()) continue;
				centers.add(m);
				choices.add(goods);
				if (nearVisited(m, visited, radius)) {
					nearCenters.add(m);
					nearChoices.add(goods);
				}
			}
			if (centers.isEmpty()) continue;
			ids.add(entry.getKey());
			weights.add(Math.max(1, def.weight()));
			centersOf.add(centers);
			goodsOf.add(choices);
			nearCentersOf.add(nearCenters);
			nearGoodsOf.add(nearChoices);
		}
		if (ids.isEmpty()) return null;
		// Near the players if any event can happen there (only those events, centered there); otherwise anywhere.
		if (nearCentersOf.stream().anyMatch(l -> !l.isEmpty())) {
			for (int i = ids.size() - 1; i >= 0; i--) {
				if (nearCentersOf.get(i).isEmpty()) {
					ids.remove(i);
					weights.remove(i);
					centersOf.remove(i);
					goodsOf.remove(i);
				} else {
					centersOf.set(i, nearCentersOf.get(i));
					goodsOf.set(i, nearGoodsOf.get(i));
				}
			}
		}
		int total = weights.stream().mapToInt(Integer::intValue).sum();
		int r = random.nextInt(total);
		int pick = 0;
		while (r >= weights.get(pick)) r -= weights.get(pick++);
		MarketEvents.Definition def = MarketEvents.get(ids.get(pick));
		int index = random.nextInt(centersOf.get(pick).size());
		Market center = centersOf.get(pick).get(index);
		List<Identifier> goods = goodsOf.get(pick).get(index);
		double multiplier = EventMath.between(random.nextDouble(), def.minMultiplier(), def.maxMultiplier());
		int days = def.minDays() + random.nextInt(Math.max(1, def.maxDays() - def.minDays() + 1));
		return new MarketEvent(registry.takeEventId(), ids.get(pick), center.id(), List.copyOf(goods), startDay, startDay + days, multiplier);
	}

	/** V-1: the market may be the center of this event (dimension, profile, events.otherDimensions). */
	static boolean fits(MarketEvents.Definition def, Market m) {
		if (!config().otherDimensions && !m.dimension().equals(Level.OVERWORLD)) return false;
		if (!def.dimensions().isEmpty() && !def.dimensions().contains(m.dimension().identifier())) return false;
		return def.profiles().isEmpty() || m.profileId() != null && def.profiles().contains(m.profileId());
	}

	/**
	 * V-3: goods of the event the market deals in and an event may move: no specials, auto_add goods or multi-item units,
	 * not short here (K), and not moved by another event or a rival campaign (O) centered within {@code events.farRadius}.
	 */
	static List<Identifier> eligibleGoods(MarketRegistry registry, Market market, MarketEvents.Definition def, long startDay) {
		List<Identifier> list = new ArrayList<>();
		for (Identifier goodsId : def.allGoods()) {
			GoodsDefinition g = GoodsCatalog.get(goodsId);
			if (g == null || market.good(goodsId) == null || g.unit() != 1 || g.autoAdd().isPresent()) continue;
			if (market.isSpecial(goodsId) || market.params(goodsId) == null || market.shortages().containsKey(goodsId)) continue;
			if (blockedByEvent(registry, market, goodsId, startDay) || blockedByRival(registry, market, goodsId, startDay)) continue;
			list.add(goodsId);
		}
		return list;
	}

	private static boolean blockedByEvent(MarketRegistry registry, Market market, Identifier goodsId, long startDay) {
		for (MarketEvent e : registry.events()) {
			if (e.endDay() <= startDay || !e.goods().contains(goodsId)) continue;
			Market center = registry.get(e.market());
			if (center != null && near(market, center)) return true;
		}
		return false;
	}

	private static boolean blockedByRival(MarketRegistry registry, Market market, Identifier goodsId, long startDay) {
		for (Campaign c : registry.campaigns()) {
			if (c.endDay() <= startDay || !c.goods().equals(goodsId)) continue;
			Market center = registry.get(c.market());
			if (center != null && near(market, center)) return true;
		}
		return false;
	}

	/**
	 * V-3: a rival campaign (O-3) may not move this good at {@code market}: an event moving it is running or forecast
	 * with its center within {@code events.farRadius}, so that news pointing opposite ways are not shown side by side.
	 */
	public static boolean blocksRival(MarketRegistry registry, Market market, Identifier goodsId, long day) {
		if (!config().enabled) return false;
		return blockedByEvent(registry, market, goodsId, day);
	}

	private static boolean near(Market a, Market b) {
		return linked(a.dimension(), b.dimension()) && MarketRegistry.linkedDistance(a, b) <= config().farRadius;
	}

	static boolean nearVisited(Market m, List<Market> visited, double radius) {
		for (Market v : visited) {
			if (v.dimension().equals(m.dimension()) && v.horizontalDistanceTo(m.center()) <= radius) return true;
		}
		return false;
	}

	private static boolean linked(ResourceKey<Level> a, ResourceKey<Level> b) {
		return a.equals(b) || b.equals(MarketRegistry.linkedDimension(a));
	}

	/** Events running on {@code day}. */
	public static List<MarketEvent> active(MarketRegistry registry, long day) {
		return registry.events().stream().filter(e -> e.activeOn(day)).toList();
	}

	/** V-4: share of an event a market gets for one good: 1 at the center, falling off with distance, × the good's link. */
	public static double weight(MarketRegistry registry, Market market, MarketEvent e, Identifier goodsId) {
		if (market.id() == e.market()) return 1.0;
		Market center = registry.get(e.market());
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (center == null || def == null || !linked(market.dimension(), center.dimension())) return 0.0;
		TradeExpansionConfig config = TradeExpansionConfig.get();
		double d = MarketRegistry.linkedDistance(market, center);
		return DistanceModel.weight(d, config.events.nearRadius, config.events.farRadius, config.distance.falloffExponent) * def.link();
	}

	/** V-4: the multiplier of B of one good of {@code e} at {@code market} on {@code day} (1 = no effect). */
	public static double multiplier(MarketRegistry registry, Market market, MarketEvent e, Identifier goodsId, long day) {
		if (!e.activeOn(day) || !e.goods().contains(goodsId)) return 1.0;
		double w = weight(registry, market, e, goodsId);
		if (w <= 0) return 1.0;
		return EventMath.multiplier(e.multiplier(), config().strength, w, EventMath.ramp(day, e.startDay()));
	}

	/**
	 * V-4: copies today's multipliers of the running events to the market (before its prices are used).
	 * @return {@code true} if they changed
	 */
	public static boolean apply(MarketRegistry registry, Market market, long now) {
		Map<Identifier, Double> next = new LinkedHashMap<>();
		if (config().enabled) {
			long today = day(now);
			for (MarketEvent e : registry.events()) {
				if (!e.activeOn(today)) continue;
				for (Identifier goodsId : e.goods()) {
					if (market.good(goodsId) == null || market.isSpecial(goodsId)) continue;
					double m = multiplier(registry, market, e, goodsId, today);
					if (m != 1.0) next.merge(goodsId, m, (a, b) -> a * b);
				}
			}
		}
		if (next.equals(market.eventMultipliers())) return false;
		market.eventMultipliers().clear();
		market.eventMultipliers().putAll(next);
		return true;
	}

	/** V-5: the running event with the largest effect on this good at this market, or {@code null}. */
	public static MarketEvent strongest(MarketRegistry registry, Market market, Identifier goodsId, long now) {
		if (!config().enabled) return null;
		long today = day(now);
		MarketEvent best = null;
		double bestEffect = 0;
		for (MarketEvent e : registry.events()) {
			double effect = Math.abs(multiplier(registry, market, e, goodsId, today) - 1.0);
			if (effect > bestEffect) {
				best = e;
				bestEffect = effect;
			}
		}
		return best;
	}

	/** V-5: the market feels {@code e} on at least one good it deals in (today, or from tomorrow for a forecast). */
	public static boolean felt(MarketRegistry registry, Market market, MarketEvent e) {
		for (Identifier goodsId : e.goods()) {
			if (market.good(goodsId) != null && weight(registry, market, e, goodsId) > 0) return true;
		}
		return false;
	}

	/**
	 * V-5: events for the board of {@code here}: running (and forecast, with {@code events.forecast}) ones centered within
	 * {@code radius}, or felt here; nearest first.
	 */
	public static List<MarketEvent> news(MarketRegistry registry, Market here, double radius, long now) {
		if (!config().enabled) return List.of();
		long today = day(now);
		List<MarketEvent> list = new ArrayList<>();
		for (MarketEvent e : registry.events()) {
			if (!(e.activeOn(today) || config().forecast && e.startDay() == today + 1)) continue;
			Market center = registry.get(e.market());
			if (center == null) continue;
			boolean close = center.dimension().equals(here.dimension()) && center.horizontalDistanceTo(here.center()) <= radius;
			if (close || felt(registry, here, e)) list.add(e);
		}
		list.sort(Comparator.comparingDouble(e -> MarketRegistry.linkedDistance(here, registry.get(e.market()))));
		return list;
	}

	/** V-5: tells the players within {@code board.radius} of the center that the event starts today / tomorrow. */
	public static void announce(MinecraftServer server, MarketRegistry registry, MarketEvent e, boolean forecast) {
		if (!config().notifyChat) return;
		Market center = registry.get(e.market());
		if (center == null || MarketEvents.get(e.event()) == null) return;
		ServerLevel level = server.getLevel(center.dimension());
		if (level == null) return;
		Component message = message(e, center, forecast);
		double radius = TradeExpansionConfig.get().board.radius;
		for (ServerPlayer player : level.players()) {
			if (center.horizontalDistanceTo(player.blockPosition()) <= radius) player.sendSystemMessage(message);
		}
	}

	/** V-5: "小麦・パンなど" (the first two goods). */
	public static Component goodsNames(MarketEvent e) {
		net.minecraft.network.chat.MutableComponent names = Component.empty();
		int n = 0;
		for (Identifier id : e.goods()) {
			GoodsDefinition def = GoodsCatalog.get(id);
			if (def == null) continue;
			if (n == 2) return Component.translatable("message.ak_trade_expansion.weather.goods_more", names);
			if (n > 0) names.append(Component.translatable("message.ak_trade_expansion.weather.goods_separator"));
			names.append(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(def.item())).getHoverName());
			n++;
		}
		return names;
	}

	/** "【豊作】「麦の里」の周りは豊作です。小麦とパンが安くなっています" (or the forecast). */
	public static Component message(MarketEvent e, Market center, boolean forecast) {
		String key = MarketEvents.key(e.event());
		Component text = Component.translatable(key + (forecast ? ".forecast" : ".desc"), MarketNames.name(center));
		return Component.translatable(forecast ? "message.ak_trade_expansion.event.forecast" : "message.ak_trade_expansion.event.start",
				MarketEvents.name(e.event()), text).withStyle(e.raises() ? ChatFormatting.RED : ChatFormatting.GREEN);
	}
}
