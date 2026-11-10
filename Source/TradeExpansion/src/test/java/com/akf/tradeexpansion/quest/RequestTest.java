package com.akf.tradeexpansion.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Delivery and transport requests (design J / J-2): saved form and the accept / taken rules. */
class RequestTest {
	private static final Identifier BREAD = Identifier.parse("ak_trade_expansion:bread");
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private static Request roundTrip(Request r) {
		return Request.CODEC.parse(JsonOps.INSTANCE, Request.CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow()).getOrThrow();
	}

	@Test
	void roundTrip() {
		Request delivery = new Request(3, BREAD, 16, 400, 50_000, 2).accept(A).accept(B);
		assertEquals(delivery, roundTrip(delivery));
		Request transport = new Request(4, BREAD, 8, 300, 60_000, 1, List.of(A), Optional.of(new Request.Transport(9, 120)));
		assertEquals(transport, roundTrip(transport));
	}

	/** A request saved before v0.29 / v0.35: no accepted players, not a transport request. */
	@Test
	void oldSaveTakesDefaults() {
		Request r = Request.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"id\": 1, \"goods\": \"ak_trade_expansion:bread\", \"quantity\": 4, \"reward\": 90, \"deadline\": 100, \"difficulty\": 1}"))
				.getOrThrow();
		assertTrue(r.accepted().isEmpty());
		assertFalse(r.isTransport());
		assertFalse(r.taken());
	}

	@Test
	void acceptAddsEachPlayerOnce() {
		Request r = new Request(1, BREAD, 4, 90, 100, 1);
		Request once = r.accept(A);
		assertEquals(List.of(A), once.accepted());
		assertSame(once, once.accept(A));
		assertEquals(List.of(A, B), once.accept(B).accepted());
		assertTrue(r.accepted().isEmpty()); // requests are immutable
	}

	/** J-2: a delivery request is never "taken"; a transport request is once somebody took the crate. */
	@Test
	void takenOnlyForTransport() {
		assertFalse(new Request(1, BREAD, 4, 90, 100, 1).accept(A).taken());
		Request transport = new Request(2, BREAD, 4, 90, 100, 1, List.of(), Optional.of(new Request.Transport(5, 40)));
		assertFalse(transport.taken());
		assertTrue(transport.accept(A).taken());
	}

	@Test
	void acceptedListIsCopied() {
		List<UUID> players = new ArrayList<>(List.of(A));
		Request r = new Request(1, BREAD, 4, 90, 100, 1, players, Optional.empty());
		players.add(B);
		assertEquals(List.of(A), r.accepted());
		assertThrows(UnsupportedOperationException.class, () -> r.accepted().add(B));
	}

	@Test
	void withRewardKeepsTheRest() {
		Request r = new Request(1, BREAD, 4, 90, 100, 1).accept(A);
		Request shown = r.withReward(120);
		assertEquals(120, shown.reward());
		assertEquals(new Request(1, BREAD, 4, 120, 100, 1).accept(A), shown);
	}
}
