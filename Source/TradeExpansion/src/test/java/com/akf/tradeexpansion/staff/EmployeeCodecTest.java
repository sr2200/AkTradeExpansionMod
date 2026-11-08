package com.akf.tradeexpansion.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saved form of an employee (design Q, F-1): round trip, and fields missing from older saves take their defaults. */
class EmployeeCodecTest {
	private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	void roundTrip() {
		Employee e = Employee.hire(7, OWNER, "Alex", Employee.Role.CARRIER, 3, Identifier.parse("ak_trade_expansion:iron_ingot"), 9, 16, 60, 55,
				24_000, 24_000);
		e.purse = 1234;
		e.stock = 12;
		e.cargoCost = 700;
		e.phase = Employee.Phase.OUTBOUND;
		e.status = Employee.Status.RESTING;
		e.trips = 2;
		e.addLog(new Employee.LogEntry(25_000, "bought", Optional.of(Identifier.parse("ak_trade_expansion:iron_ingot")), 9, List.of(12L, 700L)));

		JsonElement json = Employee.CODEC.encodeStart(JsonOps.INSTANCE, e).getOrThrow();
		Employee back = Employee.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(7, back.id());
		assertEquals(OWNER, back.owner());
		assertEquals(Employee.Role.CARRIER, back.role());
		assertEquals(3, back.home());
		assertEquals(9, back.destination());
		assertEquals(1234, back.purse());
		assertEquals(12, back.stock());
		assertEquals(Employee.Phase.OUTBOUND, back.phase());
		assertEquals(Employee.Status.RESTING, back.status());
		assertEquals(16, back.quantity);
		assertEquals(60, back.buyLimit);
		assertEquals(55, back.sellLimit);
		assertEquals(48_000, back.wageDue); // the first day is part of the fee
		assertEquals(1, back.log.size());
		assertEquals(List.of(12L, 700L), back.log.getFirst().values());
		// snake_case names (F-1)
		assertTrue(json.getAsJsonObject().getAsJsonObject("state").has("cargo_cost"));
		assertTrue(json.getAsJsonObject().getAsJsonObject("settings").has("buy_limit"));
	}

	@Test
	void minimalSaveTakesDefaults() {
		JsonElement json = JsonParser.parseString("""
				{"id": 1, "owner": [0, 0, 0, 1], "settings": {"role": "clerk", "home": 4, "goods": "ak_trade_expansion:bread"},
				 "state": {"phase": "something_new", "status": "working"}}
				""");
		Employee e = Employee.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(Employee.Role.CLERK, e.role());
		assertEquals(-1, e.destination());
		assertEquals(0, e.purse());
		assertEquals(Employee.Phase.HOME, e.phase()); // unknown names fall back instead of failing the whole save
		assertEquals(1, e.sellLimit);
		assertTrue(e.log.isEmpty());
	}

	@Test
	void automaticCarrierKeepsItsCargoByGood() {
		Employee e = Employee.hire(3, OWNER, "Alex", Employee.Role.CARRIER, 1, Employee.AUTO, 2, 64, 1, 1, 0, 24_000);
		assertTrue(e.auto());
		e.cargo.add(new Employee.CargoLine(Identifier.parse("ak_trade_expansion:iron_ingot"), 5, 300));
		e.cargo.add(new Employee.CargoLine(Identifier.parse("ak_trade_expansion:bread"), 12, 250));
		JsonElement json = Employee.CODEC.encodeStart(JsonOps.INSTANCE, e).getOrThrow();
		Employee back = Employee.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertTrue(back.auto());
		assertEquals(2, back.cargo.size());
		assertEquals(Identifier.parse("ak_trade_expansion:bread"), back.cargo.get(1).goods);
		assertEquals(12, back.cargo.get(1).units);
		assertEquals(250, back.cargo.get(1).cost);
		// a carrier with one good has no cargo list
		assertTrue(Employee.CODEC.parse(JsonOps.INSTANCE, Employee.CODEC.encodeStart(JsonOps.INSTANCE,
				Employee.hire(4, OWNER, "Alex", Employee.Role.CARRIER, 1, Identifier.parse("ak_trade_expansion:bread"), 2, 16, 30, 20, 0, 24_000))
				.getOrThrow()).getOrThrow().cargo.isEmpty());
	}

	@Test
	void logKeepsTheNewestLines() {
		Employee e = Employee.hire(1, OWNER, "Alex", Employee.Role.CLERK, 1, Identifier.parse("ak_trade_expansion:bread"), -1, 0, 0, 10, 0, 24_000);
		for (int i = 0; i < Employee.LOG_SIZE + 5; i++) e.addLog(new Employee.LogEntry(i, "clerk_sold", Optional.empty(), -1, List.of((long) i, 1L)));
		assertEquals(Employee.LOG_SIZE, e.log.size());
		assertEquals(5, e.log.getFirst().time());
	}
}
