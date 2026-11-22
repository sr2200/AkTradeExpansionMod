package com.akf.tradeexpansion.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** The weather and the market (design U, v0.82). */
class WeatherMathTest {
	private static final int DAY = (int) PriceModel.TICKS_PER_DAY;

	@Test
	void rainOfTodayAndTheTwoDaysBefore() {
		Map<Long, Integer> rain = new TreeMap<>();
		rain.put(7L, DAY); // 3 days back: not counted
		rain.put(8L, DAY / 2);
		rain.put(9L, DAY / 4);
		rain.put(10L, DAY / 4);
		rain.put(11L, DAY); // after today (clock set back): not counted
		assertEquals(1.0, WeatherMath.rainDays(rain, 10), 1e-9);
		assertTrue(WeatherMath.rainySpell(WeatherMath.rainDays(rain, 10), 1.0));
		assertFalse(WeatherMath.rainySpell(WeatherMath.rainDays(rain, 10), 1.01));
		assertEquals(1.25, WeatherMath.rainDays(rain, 12), 1e-9); // days 10..12: 0.25 + 1 (day 11) + 0
		assertEquals(0.0, WeatherMath.rainDays(Map.of(), 10));
	}

	@Test
	void window() {
		assertTrue(WeatherMath.inWindow(10, 10));
		assertTrue(WeatherMath.inWindow(8, 10));
		assertFalse(WeatherMath.inWindow(7, 10));
		assertFalse(WeatherMath.inWindow(11, 10));
		assertEquals(-1, WeatherMath.day(-1));
		assertEquals(1, WeatherMath.day(DAY));
	}

	@Test
	void multipliers() {
		assertEquals(0.85, WeatherMath.rainMultiplier(true, 0.85));
		assertEquals(1.0, WeatherMath.rainMultiplier(false, 0.85));
		assertEquals(150, WeatherMath.stormReward(100, 1.5));
		assertEquals(152, WeatherMath.stormReward(101, 1.5)); // rounded up
		assertEquals(101, WeatherMath.stormReward(101, 1.0));
		assertEquals(101, WeatherMath.stormReward(101, 0.5)); // never less
	}
}
