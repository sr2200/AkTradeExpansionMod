package com.akf.tradeexpansion.api;

import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only access to the markets for other mods (design S). Server side, on the server thread only. Prices are the
 * market's own (without a player's reputation bonus or bulk discount), brought up to the current market clock.
 * Events: {@link TradeExpansionEvents}. Everything outside the {@code api} package may change between versions.
 */
public final class TradeExpansionApi {
	/** Raised when this API changes in a way that breaks callers. */
	public static final int VERSION = 1;

	private TradeExpansionApi() {
	}

	/** One market as seen from outside. */
	public record MarketInfo(long id, ResourceKey<Level> dimension, BlockPos center, Component name, int level, Set<Identifier> goods) {
	}

	/**
	 * Prices of one goods at one market, per trading unit, in copper.
	 *
	 * @param buyPrice  what a player pays to buy one unit (0 when the market does not sell it)
	 * @param sellPrice what a player gets for selling one unit (0 when the market does not buy it)
	 * @param stock     units in stock
	 * @param unit      items per trading unit
	 */
	public record Quote(long marketId, Identifier goods, long buyPrice, long sellPrice, double stock, int unit) {
	}

	/** Every market of every dimension, by id. */
	public static List<MarketInfo> markets(MinecraftServer server) {
		return MarketRegistry.get(server).all().stream().map(TradeExpansionApi::info).toList();
	}

	public static Optional<MarketInfo> market(MinecraftServer server, long marketId) {
		return Optional.ofNullable(MarketRegistry.get(server).get(marketId)).map(TradeExpansionApi::info);
	}

	/** The market of {@code level} whose center is nearest to {@code pos} within {@code radius} blocks (horizontal). */
	public static Optional<MarketInfo> nearestMarket(ServerLevel level, BlockPos pos, double radius) {
		return Optional.ofNullable(MarketRegistry.get(level.getServer()).nearestWithin(level.dimension(), pos, radius)).map(TradeExpansionApi::info);
	}

	/** Markets of {@code level} whose center is within {@code radius} blocks (horizontal) of {@code pos}, by id. */
	public static List<MarketInfo> marketsWithin(ServerLevel level, BlockPos pos, double radius) {
		return MarketRegistry.get(level.getServer()).within(level.dimension(), pos, radius).stream().map(TradeExpansionApi::info).toList();
	}

	/** Current prices of {@code goods} at the market; empty when the market or the goods is unknown or not on its shelves. */
	public static Optional<Quote> quote(MinecraftServer server, long marketId, Identifier goods) {
		Market market = MarketRegistry.get(server).get(marketId);
		if (market == null) return Optional.empty();
		ServerLevel level = server.getLevel(market.dimension());
		GoodsDefinition def = GoodsCatalog.get(goods);
		GoodState state = market.good(goods);
		GoodParams params = market.params(goods);
		if (level == null || def == null || state == null || params == null) return Optional.empty();
		market.refresh(MarketClock.now(level)); // lazy recovery (B-3), as when a player looks
		return Optional.of(new Quote(marketId, goods, def.buy() ? PriceModel.buyPrice(params, state.pressure()) : 0,
				def.sell() ? PriceModel.sellPrice(params, state.pressure()) : 0, state.stock(), def.unit()));
	}

	/** Goods ids whose item is {@code item} (several goods may share an item, e.g. with different components). */
	public static List<Identifier> goodsOf(Identifier item) {
		return GoodsCatalog.all().entrySet().stream().filter(e -> e.getValue().item().equals(item)).map(e -> e.getKey()).toList();
	}

	/** Market clock now (ticks; {@code recovery.clock} plus {@code /aktradeex clock advance}). */
	public static long marketTime(ServerLevel level) {
		return MarketClock.now(level);
	}

	private static MarketInfo info(Market m) {
		return new MarketInfo(m.id(), m.dimension(), m.center(), MarketNames.name(m), m.level(), Set.copyOf(m.goods().keySet()));
	}
}
