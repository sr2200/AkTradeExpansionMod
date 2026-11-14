package com.akf.tradeexpansion.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

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

	/**
	 * After markets changed: trades and the price propagation to nearby markets, sell all, staff, requests, development,
	 * rival guilds, admin commands.
	 */
	public static final Event<MarketsChangedListener> MARKETS_CHANGED = EventFactory.createArrayBacked(MarketsChangedListener.class,
			listeners -> (server, ids) -> {
				for (MarketsChangedListener l : listeners) l.onMarketsChanged(server, ids);
			});
}
