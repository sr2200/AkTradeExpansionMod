package com.akf.tradeexpansion.core;

import java.util.Map;

/**
 * The weather and the market (design U, v0.82). Minecraft-independent.
 * <p>
 * Rain is counted per game day (24,000 ticks of game time): a rainy spell is when it rained at least {@code rainDays}
 * days in total today and the {@link #WINDOW_DAYS} − 1 days before.
 */
public final class WeatherMath {
	/** Days counted for a rainy spell: today and the 2 days before. */
	public static final int WINDOW_DAYS = 3;

	private WeatherMath() {
	}

	/** Game day of a game time. */
	public static long day(long gameTime) {
		return Math.floorDiv(gameTime, PriceModel.TICKS_PER_DAY);
	}

	/** Days of rain (rain ticks / 24,000) today and the days before, within the window. */
	public static double rainDays(Map<Long, Integer> rainTicksByDay, long today) {
		long ticks = 0;
		for (Map.Entry<Long, Integer> e : rainTicksByDay.entrySet()) {
			if (inWindow(e.getKey(), today)) ticks += Math.max(0, e.getValue());
		}
		return ticks / (double) PriceModel.TICKS_PER_DAY;
	}

	/** Whether a day is still counted on {@code today} (days after today, after the clock was set back, are not). */
	public static boolean inWindow(long day, long today) {
		return day <= today && day > today - WINDOW_DAYS;
	}

	public static boolean rainySpell(double rainDays, double threshold) {
		return rainDays >= threshold;
	}

	/** Multiplier of B of the rain goods: {@code multiplier} in a rainy spell, else 1. */
	public static double rainMultiplier(boolean rainySpell, double multiplier) {
		return rainySpell ? multiplier : 1.0;
	}

	/** Reward of a cargo crate delivered in a thunderstorm: ceil(reward × multiplier), never less than the reward. */
	public static long stormReward(long reward, double multiplier) {
		if (multiplier <= 1.0) return reward;
		return Math.max(reward, (long) Math.ceil(reward * multiplier - 1e-9));
	}
}
