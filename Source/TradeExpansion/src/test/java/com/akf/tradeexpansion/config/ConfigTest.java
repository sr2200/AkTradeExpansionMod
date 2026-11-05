package com.akf.tradeexpansion.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** config/ak_trade_expansion.json (design G): defaults, partial files, range correction, round trip. */
class ConfigTest {
	@TempDir
	Path dir;

	@Test
	void missingFileWritesDefaults() {
		Path file = dir.resolve("ak_trade_expansion.json");
		TradeExpansionConfig config = ConfigManager.read(file);
		assertTrue(Files.exists(file));
		assertDoesNotThrow(() -> assertTrue(Files.readString(file).contains("\"halfLifeDays\": 2.0"), "every value is written"));
		assertEquals(2.0, config.recovery.halfLifeDays);
		assertEquals(List.of(10, 10, 10, 10), config.currency.ratios);
		assertEquals(20_000, config.wallet.capacity);
	}

	@Test
	void partialFileKeepsOtherDefaults() throws IOException {
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, "{\"recovery\": {\"halfLifeDays\": 3.5}, \"trade\": {\"allowCreative\": true}}");
		TradeExpansionConfig config = ConfigManager.read(file);
		assertEquals(3.5, config.recovery.halfLifeDays);
		assertTrue(config.trade.allowCreative);
		assertEquals(64, config.trade.maxPerTransaction);
		assertEquals(0.4, config.price.defaultSpread);
	}

	/** B-3: consumable overrides are normalized item ids; malformed and duplicate entries are dropped, lists stay editable. */
	@Test
	void consumableListsAreNormalized() throws IOException {
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, "{\"consumables\": {\"add\": [\"cobblestone\", \"minecraft:cobblestone\", \"Bad Id\"], \"remove\": [\"minecraft:coal\"]}}");
		TradeExpansionConfig config = ConfigManager.read(file);
		assertEquals(List.of("minecraft:cobblestone"), config.consumables.add);
		assertEquals(List.of("minecraft:coal"), config.consumables.remove);
		config.consumables.add.add("minecraft:sand");
		assertEquals(List.of(), ConfigManager.read(dir.resolve("missing.json")).consumables.add);
	}

	/** D-2 / G: per-goods values; ids get the mod namespace, values are clamped, empty entries dropped. */
	@Test
	void goodsOverridesAreNormalized() throws IOException {
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, """
				{"goods": {"dandelion": {"base_price": 5, "sell": false},
				           "ak_trade_expansion:poppy": {"spread": 5.0},
				           "ak_trade_expansion:empty": {}}}
				""");
		TradeExpansionConfig config = ConfigManager.read(file);
		assertEquals(5.0, config.goods.get("ak_trade_expansion:dandelion").basePrice().orElseThrow());
		assertFalse(config.goods.get("ak_trade_expansion:dandelion").sell().orElseThrow());
		assertEquals(1.9, config.goods.get("ak_trade_expansion:poppy").spread().orElseThrow());
		assertFalse(config.goods.containsKey("ak_trade_expansion:empty"));
	}

	/** C-1: where new markets may be founded; default 4 (beds and villagers). */
	@Test
	void newMarketRequirement() throws IOException {
		assertEquals(TradeExpansionConfig.NewMarketRequires.BEDS_AND_VILLAGERS, TradeExpansionConfig.defaults().market.newMarketRequires);
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, "{\"market\": {\"newMarketRequires\": \"VILLAGE\", \"newMarketMinBeds\": -5}}");
		TradeExpansionConfig config = ConfigManager.read(file);
		assertEquals(TradeExpansionConfig.NewMarketRequires.VILLAGE, config.market.newMarketRequires);
		assertEquals(0, config.market.newMarketMinBeds);
		assertEquals(3, config.market.newMarketMinVillagers);
	}

	@Test
	void outOfRangeValuesAreCorrected() throws IOException {
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, """
				{"currency": {"ratios": [1, 10, 500, 10], "emeraldCoinsPerBlock": 99},
				 "distance": {"mergeRadius": 64, "nearRadius": 10, "farRadius": 5},
				 "market": {"minGoods": 30, "maxGoods": 20, "levelThresholds": [5, 3, 20, 40]},
				 "command": {"permissionLevel": 9}}
				""");
		TradeExpansionConfig config = ConfigManager.read(file);
		assertEquals(List.of(2, 10, 100, 10), config.currency.ratios);
		assertEquals(64, config.currency.emeraldCoinsPerBlock);
		assertEquals(64, config.distance.nearRadius);
		assertEquals(65, config.distance.farRadius);
		assertEquals(30, config.market.maxGoods);
		assertEquals(List.of(5, 5, 20, 40), config.market.levelThresholds);
		assertEquals(4, config.command.permissionLevel);
	}

	@Test
	void brokenFileFallsBackToDefaults() throws IOException {
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, "{ this is not json");
		assertEquals(2.0, ConfigManager.read(file).recovery.halfLifeDays);
	}

	@Test
	void writeThenReadRoundTrips() {
		Path file = dir.resolve("ak_trade_expansion.json");
		TradeExpansionConfig config = TradeExpansionConfig.defaults();
		config.recovery.clock = TradeExpansionConfig.RecoveryClock.DAY_TIME;
		config.bank.cardFee = 250;
		config.market.villagerThresholds = List.of(1, 2, 3, 4);
		ConfigManager.write(file, config);
		TradeExpansionConfig read = ConfigManager.read(file);
		assertEquals(TradeExpansionConfig.RecoveryClock.DAY_TIME, read.recovery.clock);
		assertEquals(250, read.bank.cardFee);
		assertEquals(List.of(1, 2, 3, 4), read.market.villagerThresholds);
	}

	@Test
	void copyIsIndependent() {
		TradeExpansionConfig original = TradeExpansionConfig.defaults();
		TradeExpansionConfig copy = ConfigManager.copy(original);
		copy.currency.ratios.set(0, 20);
		copy.trade.maxDistance = 16;
		assertEquals(10, original.currency.ratios.get(0));
		assertEquals(8, original.trade.maxDistance);
	}

	/** D-5: specials per level; a short list repeats its last entry, a wrong size falls back to the defaults. */
	@Test
	void specialGoodsPerLevel() throws IOException {
		TradeExpansionConfig.Market market = TradeExpansionConfig.defaults().market;
		assertEquals(1, market.specialGoodsAt(1));
		assertEquals(2, market.specialGoodsAt(3));
		assertEquals(2, market.specialGoodsAt(9));
		Path file = dir.resolve("ak_trade_expansion.json");
		Files.writeString(file, "{\"market\": {\"specialGoods\": [3, 3]}}");
		assertEquals(java.util.List.of(1, 1, 2, 2, 2), ConfigManager.read(file).market.specialGoods);
	}
}
