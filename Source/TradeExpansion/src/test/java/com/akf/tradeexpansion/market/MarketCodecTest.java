package com.akf.tradeexpansion.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.quest.Request;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Saved form of the markets (design F-1, {@code ak_trade_expansion:markets}): round trip, snake_case names, and saves of
 * older versions that lack the fields added later.
 */
class MarketCodecTest {
	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final Identifier BREAD = Identifier.parse("ak_trade_expansion:bread");
	private static final Identifier IRON = Identifier.parse("ak_trade_expansion:iron_ingot");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static Market roundTrip(Market market) {
		JsonElement json = Market.CODEC.encodeStart(JsonOps.INSTANCE, market).getOrThrow();
		return Market.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
	}

	@Test
	void roundTripKeepsEveryField() {
		Market m = new Market(5, Level.NETHER, new BlockPos(10, 64, -20), MarketOrigin.CRAFTED, 1000);
		m.setProfileId(Identifier.parse("ak_trade_expansion:plains_village"));
		m.setLevel(3);
		m.setMeasuredLevel(2);
		m.stalls().add(new BlockPos(11, 64, -20));
		m.addGood(BREAD, new GoodState(1.1, -3.5, 12.25, 900, List.of(1.0, 2.0), 7));
		m.addGood(IRON, new GoodState(0.9, 4, 3, 950));
		m.requests().add(new Request(3, BREAD, 16, 400, 50_000, 2).accept(PLAYER));
		m.requests().add(new Request(4, IRON, 8, 300, 60_000, 1, List.of(), Optional.of(new Request.Transport(9, 120))));
		m.shortages().put(IRON, 1.6);
		m.setShortagePeriod(4);
		m.setName("麦の里");
		m.setNamer(PLAYER);
		m.specials().add(IRON);
		m.bonusSpecials().add(IRON);
		m.setSpecialDay(12);
		m.setStanding(new Market.Standing(PLAYER, 123.5, 800));
		m.setDevelopment(456, 700);
		m.contributors().put(PLAYER, new Market.Contributor(PLAYER, "Alex", 456));
		m.markers().add(new BlockPos(20, 64, -20));
		m.setRivalDay(6);
		m.trends().put(Identifier.parse("ak_trade_expansion:food"), 1.2);
		m.setTrendPeriod(2);

		Market back = roundTrip(m);
		assertEquals(5, back.id());
		assertEquals(Level.NETHER, back.dimension());
		assertEquals(new BlockPos(10, 64, -20), back.center());
		assertEquals(MarketOrigin.CRAFTED, back.origin());
		assertEquals(Identifier.parse("ak_trade_expansion:plains_village"), back.profileId());
		assertEquals(3, back.level());
		assertEquals(2, back.measuredLevel());
		assertEquals(1000, back.lastUpdateGameTime());
		assertTrue(back.stalls().contains(new BlockPos(11, 64, -20)));
		// goods keep their display order and every value of the state
		assertEquals(List.of(BREAD, IRON), List.copyOf(back.goods().keySet()));
		GoodState bread = back.good(BREAD);
		assertEquals(1.1, bread.priceFactor());
		assertEquals(-3.5, bread.pressure());
		assertEquals(12.25, bread.stock());
		assertEquals(900, bread.lastTradeGameTime());
		assertEquals(List.of(1.0, 2.0), bread.history());
		assertEquals(7, bread.lastSampleDay());
		assertEquals(2, back.requests().size());
		assertEquals(List.of(PLAYER), back.requests().getFirst().accepted());
		assertEquals(new Request.Transport(9, 120), back.requests().get(1).transport().orElseThrow());
		assertEquals(1.6, back.shortages().get(IRON));
		assertEquals(4, back.shortagePeriod());
		assertEquals("麦の里", back.name());
		assertEquals(PLAYER, back.namer());
		assertTrue(back.specials().contains(IRON));
		assertTrue(back.bonusSpecials().contains(IRON));
		assertEquals(12, back.specialDay());
		assertEquals(new Market.Standing(PLAYER, 123.5, 800), back.standing(PLAYER));
		assertEquals(456, back.development());
		assertEquals(700, back.developmentSince());
		assertEquals("Alex", back.contributors().get(PLAYER).name());
		assertEquals(List.of(new BlockPos(20, 64, -20)), back.markers());
		assertEquals(6, back.rivalDay());
		assertEquals(1.2, back.trends().get(Identifier.parse("ak_trade_expansion:food")));
		assertEquals(2, back.trendPeriod());
	}

	/** A shelved good keeps its state through a save (D-4). */
	@Test
	void shelvedGoodsSurviveASave() {
		Market m = new Market(1, Level.OVERWORLD, BlockPos.ZERO, MarketOrigin.NATURAL, 0);
		m.addGood(BREAD, new GoodState(1, 2, 3, 0));
		m.shelve(BREAD);
		Market back = roundTrip(m);
		assertEquals(List.of(BREAD), back.shelvedGoods());
		assertNull(back.good(BREAD));
	}

	/**
	 * F-1: saved names are snake_case; the goods entry keeps its schema 1 name "item". (Optional fields equal to their
	 * default are left out of the save, so every value here differs from the default.)
	 */
	@Test
	void savedNamesAreSnakeCase() {
		Market m = new Market(1, Level.OVERWORLD, BlockPos.ZERO, MarketOrigin.NATURAL, 100);
		m.addGood(BREAD, new GoodState(1, 0, 0, 0));
		m.takeRequestId();
		m.setSpecialDay(3);
		m.setMeasuredLevel(2);
		m.setTrendPeriod(4);
		JsonObject json = Market.CODEC.encodeStart(JsonOps.INSTANCE, m).getOrThrow().getAsJsonObject();
		for (String key : List.of("last_level_check", "last_update", "next_request_id", "special_day", "measured_level", "trend_period")) {
			assertTrue(json.has(key), key);
		}
		JsonObject good = json.getAsJsonArray("goods").get(0).getAsJsonObject();
		assertTrue(good.has("item"));
		assertTrue(good.getAsJsonObject("state").has("price_factor"));
		assertTrue(good.getAsJsonObject("state").has("last_trade"));
	}

	/** A market saved by the first versions: only the required fields; everything added later takes its default. */
	@Test
	void oldSaveTakesDefaults() {
		JsonElement json = JsonParser.parseString("""
				{"id": 2, "dimension": "minecraft:overworld", "center": [0, 64, 0], "origin": "natural", "level": 4,
				 "stalls": [[0, 64, 0]], "last_update": 500,
				 "goods": [{"item": "ak_trade_expansion:bread", "state": {"price_factor": 1.0, "pressure": 2.0, "stock": 5.0, "last_trade": 400}}]}
				""");
		Market m = Market.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(4, m.level());
		assertEquals(4, m.measuredLevel()); // saved before v0.38: measured = level (no facility bonus)
		assertNull(m.profileId());
		assertNull(m.name());
		assertEquals(0, m.lastLevelCheckGameTime());
		assertTrue(m.requests().isEmpty());
		assertTrue(m.shortages().isEmpty());
		assertEquals(Long.MIN_VALUE, m.shortagePeriod());
		assertEquals(1, m.takeRequestId());
		assertTrue(m.specials().isEmpty());
		assertEquals(0, m.development());
		assertTrue(m.facilities().isEmpty());
		assertTrue(m.trends().isEmpty());
		assertEquals(Long.MIN_VALUE, m.rivalDay());
		GoodState bread = m.good(BREAD);
		assertTrue(bread.history().isEmpty());
		assertEquals(Long.MIN_VALUE, bread.lastSampleDay());
	}

	@Test
	void levelOutOfRangeFailsToDecode() {
		JsonElement json = JsonParser.parseString("""
				{"id": 2, "dimension": "minecraft:overworld", "center": [0, 64, 0], "origin": "natural", "level": 9,
				 "stalls": [], "last_update": 0, "goods": []}
				""");
		assertTrue(Market.CODEC.parse(JsonOps.INSTANCE, json).isError());
	}

	@Test
	void registryRoundTrip() {
		JsonElement json = JsonParser.parseString("""
				{"schema_version": 2, "currency_ratios": [10, 20, 10, 10], "next_id": 3,
				 "markets": [{"id": 1, "dimension": "minecraft:overworld", "center": [0, 64, 0], "origin": "natural",
				              "stalls": [], "last_update": 0, "goods": []}],
				 "rival_day": 8, "next_campaign_id": 4, "next_employee_id": 6}
				""");
		MarketRegistry registry = MarketRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		MarketRegistry back = MarketRegistry.CODEC.parse(JsonOps.INSTANCE,
				MarketRegistry.CODEC.encodeStart(JsonOps.INSTANCE, registry).getOrThrow()).getOrThrow();
		assertEquals(1, back.all().size());
		assertEquals(1, back.get(1).id());
		assertEquals(List.of(10, 20, 10, 10), java.util.Arrays.stream(back.currencyRatios()).boxed().toList());
		assertEquals(8, back.rivalDay());
		assertEquals(4, back.takeCampaignId());
		assertEquals(6, back.takeEmployeeId());
		assertTrue(back.campaigns().isEmpty());
		assertTrue(back.employees().isEmpty());
	}

	/** A registry saved before v0.46 / v0.48: no rival guilds, no staff. */
	@Test
	void oldRegistryTakesDefaults() {
		JsonElement json = JsonParser.parseString("""
				{"schema_version": 2, "currency_ratios": [10, 10, 10, 10], "next_id": 1, "markets": []}
				""");
		MarketRegistry registry = MarketRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(Long.MIN_VALUE, registry.rivalDay());
		assertEquals(1, registry.takeCampaignId());
		assertEquals(1, registry.takeEmployeeId());
		assertFalse(registry.isDirty());
	}

	/** Schema 1 → 2: goods keyed by item id (minecraft:bread) are moved to the goods id (ak_trade_expansion:bread). */
	@Test
	void schemaOneGoodsAreRemapped() {
		JsonElement json = JsonParser.parseString("""
				{"schema_version": 1, "currency_ratios": [10, 10, 10, 10], "next_id": 2,
				 "markets": [{"id": 1, "dimension": "minecraft:overworld", "center": [0, 64, 0], "origin": "natural",
				              "stalls": [], "last_update": 0,
				              "goods": [{"item": "minecraft:bread", "state": {"price_factor": 1.0, "pressure": 1.5, "stock": 4.0, "last_trade": 0}}]}]}
				""");
		MarketRegistry registry = MarketRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		Market m = registry.get(1);
		assertNull(m.good(Identifier.parse("minecraft:bread")));
		assertEquals(1.5, m.good(BREAD).pressure());
		assertTrue(registry.isDirty()); // saved again with schema 2
		JsonObject saved = MarketRegistry.CODEC.encodeStart(JsonOps.INSTANCE, registry).getOrThrow().getAsJsonObject();
		assertEquals(MarketRegistry.SCHEMA_VERSION, saved.get("schema_version").getAsInt());
	}
}
