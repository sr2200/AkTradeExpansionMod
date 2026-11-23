package com.akf.tradeexpansion.api;

import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Propagation;
import com.akf.tradeexpansion.trade.TradeService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Access to the markets and money for other mods (design S). Server side, on the server thread only. Prices are the
 * market's own (without a player's reputation bonus or bulk discount), brought up to the current market clock.
 * Changes are limited to moving a price for a trade made elsewhere ({@link #recordExternalTrade}) and paying coins in
 * or out; there is no way to make a player trade. Events: {@link TradeExpansionEvents}. Everything outside the
 * {@code api} package may change between versions.
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

	// ---- changes (S-3) ----

	/** Largest {@code units} of one {@link #recordExternalTrade} call. */
	public static final int MAX_EXTERNAL_UNITS = 2304;

	/**
	 * Moves the market's price as if {@code units} trading units of {@code goods} had been bought ({@code buy}) or sold
	 * there, for trades made outside this mod (a shop of another mod, say). Only the price and stock move, and the change
	 * spreads to nearby markets as for a player trade; no items or coins move (the caller does that), and there is no
	 * reputation, development, request, ledger or advancement. Buying stops at the stock, selling at the room left.
	 * Fires {@link TradeExpansionEvents#MARKETS_CHANGED}, not {@link TradeExpansionEvents#TRADE}.
	 *
	 * @param units 1 to {@link #MAX_EXTERNAL_UNITS}
	 * @return the units applied (0 when the market or goods is unknown, not on its shelves, the direction is not traded,
	 *         or there is no stock / room)
	 */
	public static int recordExternalTrade(MinecraftServer server, long marketId, Identifier goods, boolean buy, int units) {
		if (units < 1 || units > MAX_EXTERNAL_UNITS) throw new IllegalArgumentException("units must be 1-" + MAX_EXTERNAL_UNITS + ": " + units);
		MarketRegistry registry = MarketRegistry.get(server);
		Market market = registry.get(marketId);
		if (market == null) return 0;
		ServerLevel level = server.getLevel(market.dimension());
		GoodsDefinition def = GoodsCatalog.get(goods);
		GoodState state = market.good(goods);
		GoodParams params = market.params(goods);
		if (level == null || def == null || state == null || params == null || (buy ? !def.buy() : !def.sell())) return 0;
		long now = MarketClock.now(level);
		market.refresh(now);
		int n = Math.min(units, buy ? (int) Math.max(0, Math.floor(state.stock())) : PriceModel.sellCapacity(params, state));
		if (n <= 0) return 0;
		if (buy) PriceModel.applyBuy(params, state, n);
		else PriceModel.applySell(params, state, n);
		state.setLastTradeGameTime(now);
		Set<Long> changed = new HashSet<>();
		changed.add(market.id());
		Propagation.apply(registry, market, goods, buy ? -n : n, now).forEach(m -> changed.add(m.id()));
		registry.setDirty();
		TradeService.broadcast(server, registry, changed, now); // also fires MARKETS_CHANGED
		return n;
	}

	/**
	 * Bank balance of a player, in copper (design E-7). Interest is paid up to now when the player is online; for an
	 * offline player it is paid the next time they use the bank.
	 */
	public static long bankBalance(MinecraftServer server, UUID player) {
		return accounts(server, player).balance(player);
	}

	/** Pays {@code amount} copper into the player's bank account (the player may be offline). */
	public static void deposit(MinecraftServer server, UUID player, long amount) {
		if (amount < 0) throw new IllegalArgumentException("amount must not be negative: " + amount);
		if (amount == 0) return;
		accounts(server, player).add(player, amount);
		sendAccount(server, player);
	}

	/**
	 * Takes {@code amount} copper from the player's bank account.
	 *
	 * @return false (and nothing changes) when the balance is short
	 */
	public static boolean withdraw(MinecraftServer server, UUID player, long amount) {
		if (amount < 0) throw new IllegalArgumentException("amount must not be negative: " + amount);
		BankAccounts bank = accounts(server, player);
		if (bank.balance(player) < amount) return false;
		if (amount > 0) {
			bank.add(player, -amount);
			sendAccount(server, player);
		}
		return true;
	}

	/** Cash a player carries: wallets plus loose coins, in copper (design E-6). */
	public static long cash(ServerPlayer player) {
		return WalletService.balance(player);
	}

	/** Gives {@code amount} copper as cash: into the wallets, the rest as coins; what does not fit is dropped (E-3). */
	public static void giveCash(ServerPlayer player, long amount) {
		if (amount < 0) throw new IllegalArgumentException("amount must not be negative: " + amount);
		WalletService.give(player, amount);
	}

	/**
	 * Takes {@code amount} copper of cash (wallets first, then coins, giving change).
	 *
	 * @return false (and nothing changes) when the player cannot pay
	 */
	public static boolean takeCash(ServerPlayer player, long amount) {
		if (amount < 0) throw new IllegalArgumentException("amount must not be negative: " + amount);
		return amount == 0 || WalletService.pay(player, amount);
	}

	private static BankAccounts accounts(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		return online != null ? BankService.accounts(online) : BankAccounts.get(server);
	}

	private static void sendAccount(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) BankService.sendAccount(online);
	}

	private static MarketInfo info(Market m) {
		return new MarketInfo(m.id(), m.dimension(), m.center(), MarketNames.name(m), m.level(), Set.copyOf(m.goods().keySet()));
	}
}
