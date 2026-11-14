package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The clock that drives lazy recovery and level checks (design B-3, {@code recovery.clock}).
 * GAME_TIME never jumps; DAY_TIME follows the overworld day clock, so sleeping also counts (and {@code /time set}
 * may move it backwards, which recovery treats as no time passing).
 * <p>
 * Plus the world's clock offset ({@code /aktradeex clock advance}, design G "テスト用のコマンド"): the server reads it from
 * the saved {@link MarketRegistry}, the client from {@code ConfigSyncS2C}. Everything driven by this clock (recovery,
 * specials, requests, interest, reputation, rival guilds, trends, staff, construction) moves forward together.
 */
public final class MarketClock {
	/** Offset the server sent (client side); 0 when not connected. */
	private static volatile long clientOffset;

	private MarketClock() {
	}

	public static long now(Level level) {
		long base = TradeExpansionConfig.get().recovery.clock == TradeExpansionConfig.RecoveryClock.DAY_TIME
				? level.getOverworldClockTime()
				: level.getGameTime();
		return base + offset(level);
	}

	/** Ticks the market clock of this world is ahead of the game clock. */
	public static long offset(Level level) {
		return level instanceof ServerLevel server ? MarketRegistry.get(server.getServer()).clockOffset() : clientOffset;
	}

	/** Client side: the offset from the server's {@code ConfigSyncS2C} (0 on disconnect). */
	public static void setClientOffset(long offset) {
		clientOffset = offset;
	}
}
