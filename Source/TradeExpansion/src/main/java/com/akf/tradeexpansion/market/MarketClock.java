package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import net.minecraft.world.level.Level;

/**
 * The clock that drives lazy recovery and level checks (design B-3, {@code recovery.clock}).
 * GAME_TIME never jumps; DAY_TIME follows the overworld day clock, so sleeping also counts (and {@code /time set}
 * may move it backwards, which recovery treats as no time passing).
 */
public final class MarketClock {
	private MarketClock() {
	}

	public static long now(Level level) {
		return TradeExpansionConfig.get().recovery.clock == TradeExpansionConfig.RecoveryClock.DAY_TIME
				? level.getOverworldClockTime()
				: level.getGameTime();
	}
}
