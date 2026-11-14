package com.akf.tradeexpansion.core;

import com.akf.tradeexpansion.core.ProfileChoice.Candidate;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileChoiceTest {
	@Test
	void noCandidates() {
		assertNull(ProfileChoice.choose(List.<Candidate<String>>of(), new Random(1)));
	}

	@Test
	void highestPriorityWins() {
		List<Candidate<String>> list = List.of(new Candidate<>("plains", 0, 100), new Candidate<>("fishing", 10, 1),
				new Candidate<>("other", 5, 100));
		for (int seed = 0; seed < 50; seed++) assertEquals("fishing", ProfileChoice.choose(list, new Random(seed)));
	}

	@Test
	void lowestPossiblePriorityStillCounts() {
		assertEquals("a", ProfileChoice.choose(List.of(new Candidate<>("a", Integer.MIN_VALUE, 1)), new Random(1)));
	}

	@Test
	void tiesDrawnByWeight() {
		List<Candidate<String>> list = List.of(new Candidate<>("a", 1, 1), new Candidate<>("b", 1, 3), new Candidate<>("low", 0, 100));
		Map<String, Integer> counts = new HashMap<>();
		Random random = new Random(7);
		for (int i = 0; i < 4000; i++) counts.merge(ProfileChoice.choose(list, random), 1, Integer::sum);
		assertEquals(2, counts.size());
		double share = counts.get("b") / 4000.0;
		assertTrue(share > 0.70 && share < 0.80, "b share " + share);
	}

	@Test
	void zeroWeightsFallBackToTheFirst() {
		List<Candidate<String>> list = List.of(new Candidate<>("a", 1, 0), new Candidate<>("b", 1, 0));
		assertEquals("a", ProfileChoice.choose(list, new Random(3)));
	}
}
