package com.akf.tradeexpansion.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Events other mods can listen to (design S, API version {@link TradeExpansionApi#VERSION}). All are fired on the server
 * thread, after the change has been made. Listeners must not throw; they may read markets with {@link TradeExpansionApi}.
 *
 * <pre>{@code
 * TradeExpansionEvents.TRADE.register(trade -> LOGGER.info("{} bought {}", trade.player().getName(), trade.goods()));
 * }</pre>
 */
public final class TradeExpansionEvents {
	private TradeExpansionEvents() {
	}

	/** How a trade was made. */
	public enum Source {
		/** The buy or sell buttons of the trade screen. */
		SCREEN,
		/** "Sell all" of the trade screen (one event per goods). */
		SELL_ALL
	}

	/**
	 * A player bought from or sold to a market.
	 *
	 * @param marketId   the market ({@link TradeExpansionApi#market})
	 * @param goods      goods id ({@code ak_trade_expansion:bread}; not the item id)
	 * @param item       item id of the goods
	 * @param buy        the player bought (false: sold)
	 * @param units      trading units (a goods may trade several items per unit)
	 * @param itemCount  items moved
	 * @param total      coins paid or received, in copper
	 * @param card       paid with the bank card (false: cash)
	 */
	public record Trade(ServerPlayer player, long marketId, Identifier goods, Identifier item, boolean buy, int units, int itemCount,
			long total, boolean card, Source source) {
	}

	@FunctionalInterface
	public interface TradeListener {
		void onTrade(Trade trade);
	}

	/** Kind of a hired hand (design Q). */
	public enum StaffRole {
		/** Carries goods between two markets and trades at both. */
		CARRIER,
		/** Sells the goods left with it at its home market. */
		CLERK
	}

	/**
	 * A hired hand bought from or sold to a market (wholesale, design Q). The owner may be offline.
	 *
	 * @param owner     UUID of the player who hired it
	 * @param ownerName name of the owner when it was hired
	 * @param staffId   the hired hand's number (unique per owner)
	 * @param units     trading units
	 * @param itemCount items moved
	 * @param total     coins paid or received, in copper
	 */
	public record StaffTrade(MinecraftServer server, UUID owner, String ownerName, int staffId, StaffRole role, long marketId,
			Identifier goods, Identifier item, boolean buy, int units, int itemCount, long total) {
	}

	@FunctionalInterface
	public interface StaffTradeListener {
		void onStaffTrade(StaffTrade trade);
	}

	/** Kind of a delivered request (design J). */
	public enum RequestKind {
		/** Goods handed over at the market that asked for them (J-1). */
		GOODS,
		/** A cargo crate brought from another market (J-2). */
		CARGO
	}

	/**
	 * A player delivered a request.
	 *
	 * @param marketId       where it was delivered
	 * @param sourceMarketId CARGO: the market the crate came from; GOODS: the same as {@code marketId}
	 * @param quantity       items delivered
	 * @param reward         coins paid as the reward, in copper (CARGO: without the deposit paid back)
	 * @param card           the reward went to the bank account (false: cash)
	 */
	public record RequestCompleted(ServerPlayer player, RequestKind kind, long marketId, long sourceMarketId, Identifier goods,
			Identifier item, int quantity, long reward, boolean card) {
	}

	@FunctionalInterface
	public interface RequestCompletedListener {
		void onRequestCompleted(RequestCompleted request);
	}

	@FunctionalInterface
	public interface MarketsChangedListener {
		/**
		 * @param marketIds markets whose prices, stock, requests, facilities or other state changed (prices also move with
		 *                  time without this event: read them when needed)
		 */
		void onMarketsChanged(MinecraftServer server, Set<Long> marketIds);
	}

	/** After a successful player trade. */
	public static final Event<TradeListener> TRADE = EventFactory.createArrayBacked(TradeListener.class,
			listeners -> trade -> {
				for (TradeListener l : listeners) l.onTrade(trade);
			});

	/** After a hired hand traded (one event per goods and market). Not fired for player trades ({@link #TRADE}). */
	public static final Event<StaffTradeListener> STAFF_TRADE = EventFactory.createArrayBacked(StaffTradeListener.class,
			listeners -> trade -> {
				for (StaffTradeListener l : listeners) l.onStaffTrade(trade);
			});

	/** After a player delivered a request (one event per request; several crates delivered at once fire one each). */
	public static final Event<RequestCompletedListener> REQUEST_COMPLETED = EventFactory.createArrayBacked(RequestCompletedListener.class,
			listeners -> request -> {
				for (RequestCompletedListener l : listeners) l.onRequestCompleted(request);
			});

	/**
	 * After markets changed: trades and the price propagation to nearby markets, sell all, staff, requests, development,
	 * rival guilds, admin commands, {@link TradeExpansionApi#recordExternalTrade}.
	 */
	public static final Event<MarketsChangedListener> MARKETS_CHANGED = EventFactory.createArrayBacked(MarketsChangedListener.class,
			listeners -> (server, ids) -> {
				for (MarketsChangedListener l : listeners) l.onMarketsChanged(server, ids);
			});
}
