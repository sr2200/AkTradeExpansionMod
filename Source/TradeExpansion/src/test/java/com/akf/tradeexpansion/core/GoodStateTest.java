package com.akf.tradeexpansion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Price history of one good (design A-4, Q17): X sampled at most once per in-game day, newest {@code max} kept. */
class GoodStateTest {
	@Test
	void oneSamplePerDay() {
		GoodState s = new GoodState(1, 2, 0, 0);
		s.sample(5, 14);
		s.setPressure(3);
		s.sample(5, 14); // same day: ignored
		assertEquals(List.of(2.0), s.history());
		s.sample(6, 14);
		assertEquals(List.of(2.0, 3.0), s.history());
		assertEquals(6, s.lastSampleDay());
	}

	/** The clock going back (DAY_TIME clock) records nothing until the day passes the last sample again. */
	@Test
	void earlierDaysAreIgnored() {
		GoodState s = new GoodState(1, 2, 0, 0);
		s.sample(10, 14);
		s.sample(4, 14);
		assertEquals(1, s.history().size());
		assertEquals(10, s.lastSampleDay());
	}

	@Test
	void keepsTheNewestSamples() {
		GoodState s = new GoodState(1, 0, 0, 0);
		for (int day = 0; day < 20; day++) {
			s.setPressure(day);
			s.sample(day, 14);
		}
		assertEquals(14, s.history().size());
		assertEquals(6.0, s.history().getFirst());
		assertEquals(19.0, s.history().getLast());
	}

	/** The saved history is copied: changing the source list does not change the state. */
	@Test
	void historyIsCopied() {
		List<Double> saved = new ArrayList<>(List.of(1.0, 2.0));
		GoodState s = new GoodState(1, 0, 0, 0, saved, 3);
		saved.add(9.0);
		assertEquals(List.of(1.0, 2.0), s.history());
		s.sample(3, 14);
		assertEquals(2, s.history().size()); // day 3 is already recorded
	}
}
